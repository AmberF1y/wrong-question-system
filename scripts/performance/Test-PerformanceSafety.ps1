[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Import-Module (
    Join-Path $PSScriptRoot "PerformanceSafety.psm1"
) -Force

$databaseName = "wrong_question_system_perf_20260912"
$validImageDirectory = (
    "D:\WrongQuestionPerfData\" +
    $databaseName +
    "\question-images"
)

Assert-PerformanceDatabaseName -DatabaseName $databaseName
$normalized = Assert-PerformanceImageDirectory -ImageDirectory $validImageDirectory -DatabaseName $databaseName

function Assert-Rejected {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Action,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    try {
        & $Action
    }
    catch {
        Write-Host "PASS: $Name"
        return
    }
    throw "Safety check unexpectedly accepted: $Name"
}

Assert-Rejected -Name "production database" -Action {
    Assert-PerformanceDatabaseName -DatabaseName "wrong_question_system"
}
Assert-Rejected -Name "ordinary test database" -Action {
    Assert-PerformanceDatabaseName -DatabaseName "wrong_question_system_test"
}
Assert-Rejected -Name "production image directory" -Action {
    Assert-PerformanceImageDirectory -ImageDirectory "D:\WrongQuestionData\question-images" -DatabaseName $databaseName
}
Assert-Rejected -Name "repository image directory" -Action {
    Assert-PerformanceImageDirectory -ImageDirectory (Join-Path (Get-PerformanceRepositoryRoot) $databaseName) -DatabaseName $databaseName
}
Assert-Rejected -Name "image directory missing database segment" -Action {
    Assert-PerformanceImageDirectory -ImageDirectory "D:\WrongQuestionPerfData\question-images" -DatabaseName $databaseName
}

Write-Host "PASS: dedicated database name"
Write-Host "PASS: dedicated image directory -> $normalized"
Write-Host "All offline performance safety checks passed."
