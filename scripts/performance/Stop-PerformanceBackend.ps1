[CmdletBinding()]
param(
    [Parameter()]
    [string]$RuntimeManifest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Import-Module (
    Join-Path $PSScriptRoot "PerformanceSafety.psm1"
) -Force

$repositoryRoot = Get-PerformanceRepositoryRoot
if ([string]::IsNullOrWhiteSpace($RuntimeManifest)) {
    $RuntimeManifest = Join-Path (
        Join-Path $repositoryRoot "performance\.local"
    ) "runtime.json"
}
$resolvedManifest = (Resolve-Path -LiteralPath $RuntimeManifest).Path
$manifest = Get-PerformanceJsonFile -Path $resolvedManifest

Assert-PerformanceDatabaseName -DatabaseName $manifest.databaseName
Assert-PerformanceImageDirectory -ImageDirectory $manifest.imageDirectory -DatabaseName $manifest.databaseName | Out-Null
if ($manifest.mode -ne "performance") {
    throw "Runtime manifest is not a performance environment"
}

$processId = [int]$manifest.processId
$processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $processId"
if ($null -eq $processInfo) {
    Write-Host "Performance backend process is already stopped: $processId"
    return
}
if (
    $processInfo.Name -notmatch '^java(?:\.exe)?$' -or
    $processInfo.CommandLine -notmatch
        '--spring\.profiles\.active=performance' -or
    $processInfo.CommandLine -notmatch 'backend-1\.0\.0\.jar'
) {
    throw "Refusing to stop a process that is not the owned performance backend"
}

Stop-Process -Id $processId
try {
    Wait-Process -Id $processId -Timeout 10
}
catch {
    throw "Performance backend did not stop within 10 seconds"
}

[pscustomobject]@{
    Stopped      = $true
    ProcessId    = $processId
    DatabaseName = $manifest.databaseName
    BaseUrl      = $manifest.baseUrl
} | Format-List
