[CmdletBinding()]
param(
    [Parameter()]
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Import-Module (
    Join-Path $PSScriptRoot "PerformanceSafety.psm1"
) -Force

$repositoryRoot = Get-PerformanceRepositoryRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path (
        Join-Path $repositoryRoot "performance\.local"
    ) "perf-environment.json"
}
$config = Get-PerformanceJsonFile -Path (
    (Resolve-Path -LiteralPath $ConfigPath).Path
)
Assert-PerformanceDatabaseName -DatabaseName $config.databaseName
Assert-PerformanceImageDirectory -ImageDirectory $config.imageDirectory -DatabaseName $config.databaseName | Out-Null

$fixtureCount = [int](
    Invoke-PerformanceMySql -Query "SELECT COUNT(*) FROM performance_question_fixture WHERE fixture_role = 'REVIEW_CONCURRENT'" -DatabaseName $config.databaseName -DatabaseHost $config.databaseHost -DatabasePort $config.databasePort -DatabaseUser $config.databaseUser
)
$advancedCount = [int](
    Invoke-PerformanceMySql -Query "SELECT COUNT(*) FROM question_review_state s JOIN performance_question_fixture f ON f.question_id = s.question_id AND f.fixture_role = 'REVIEW_CONCURRENT' WHERE s.review_status = 'ACTIVE' AND s.next_review_date > CURRENT_DATE AND s.version >= 1" -DatabaseName $config.databaseName -DatabaseHost $config.databaseHost -DatabasePort $config.databasePort -DatabaseUser $config.databaseUser
)
$historyCount = [int](
    Invoke-PerformanceMySql -Query "SELECT COUNT(*) FROM review_record r JOIN performance_question_fixture f ON f.question_id = r.question_id AND f.fixture_role = 'REVIEW_CONCURRENT' WHERE r.occurred_at >= CAST('$($config.dataset.preparedAt)' AS DATETIME(6))" -DatabaseName $config.databaseName -DatabaseHost $config.databaseHost -DatabasePort $config.databasePort -DatabaseUser $config.databaseUser
)

$consistent = (
    $fixtureCount -eq 20 -and
    $advancedCount -eq 20 -and
    $historyCount -eq 20
)
if (-not $consistent) {
    throw (
        "Review concurrency consistency check failed: fixtures=" +
        $fixtureCount +
        ", advanced=" +
        $advancedCount +
        ", newHistory=" +
        $historyCount
    )
}

[pscustomobject]@{
    Consistent       = $true
    FixtureCount     = $fixtureCount
    AdvancedCount    = $advancedCount
    NewHistoryCount  = $historyCount
    DatabaseName     = $config.databaseName
} | Format-List
