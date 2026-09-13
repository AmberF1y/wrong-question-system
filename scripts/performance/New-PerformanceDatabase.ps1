[CmdletBinding()]
param(
    [Parameter()]
    [string]$DatabaseName = "wrong_question_system_perf_20260912",

    [Parameter()]
    [string]$DatabaseHost = "127.0.0.1",

    [Parameter()]
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 3306,

    [Parameter()]
    [string]$DatabaseUser = "root",

    [Parameter(Mandatory = $true)]
    [ValidateSet("CREATE_NEW_PERFORMANCE_DATABASE")]
    [string]$Confirmation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Import-Module (
    Join-Path $PSScriptRoot "PerformanceSafety.psm1"
) -Force

Assert-PerformanceDatabaseName -DatabaseName $DatabaseName

$existsQuery = @"
SELECT COUNT(*)
FROM information_schema.schemata
WHERE schema_name = '$DatabaseName'
"@
$exists = [int](
    Invoke-PerformanceMySql -Query $existsQuery -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
if ($exists -ne 0) {
    throw (
        "Database already exists and will not be changed: " +
        $DatabaseName
    )
}

$createQuery = @"
CREATE DATABASE $DatabaseName
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci
"@
Invoke-PerformanceMySql -Query $createQuery -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser | Out-Null

$actualDatabase = [string](
    Invoke-PerformanceMySql -Query "SELECT DATABASE()" -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
if ($actualDatabase.Trim() -ne $DatabaseName) {
    throw "Created database identity verification failed"
}

[pscustomobject]@{
    Created      = $true
    DatabaseName = $DatabaseName
    Host         = $DatabaseHost
    Port         = $DatabasePort
    CharacterSet = "utf8mb4"
    Collation    = "utf8mb4_unicode_ci"
} | Format-List
