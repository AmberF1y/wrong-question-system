[CmdletBinding()]
param(
    [Parameter()]
    [string]$DatabaseName = "wrong_question_system_perf_20260912",

    [Parameter()]
    [string]$ImageDirectory,

    [Parameter()]
    [string]$EnvironmentId = "perf-small-20260912",

    [Parameter()]
    [string]$DatabaseHost = "127.0.0.1",

    [Parameter()]
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 3306,

    [Parameter()]
    [string]$DatabaseUser = "root",

    [Parameter()]
    [ValidateRange(1024, 65535)]
    [int]$ServerPort = 18080,

    [Parameter()]
    [ValidateRange(10, 300)]
    [int]$StartupTimeoutSeconds = 120,

    [Parameter()]
    [switch]$SkipPackage
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Import-Module (
    Join-Path $PSScriptRoot "PerformanceSafety.psm1"
) -Force

$repositoryRoot = Get-PerformanceRepositoryRoot
if ([string]::IsNullOrWhiteSpace($ImageDirectory)) {
    $ImageDirectory = (
        "D:\WrongQuestionPerfData\" +
        $DatabaseName +
        "\question-images"
    )
}

Assert-PerformanceDatabaseName -DatabaseName $DatabaseName
$normalizedImageDirectory = Assert-PerformanceImageDirectory -ImageDirectory $ImageDirectory -DatabaseName $DatabaseName
if ($ServerPort -eq 8080 -or $ServerPort -eq 5173) {
    throw "Performance backend must not use production application ports"
}
if (Test-PerformanceTcpPort -TargetHost "127.0.0.1" -Port $ServerPort) {
    throw "Performance server port is already in use: $ServerPort"
}

$actualDatabase = [string](
    Invoke-PerformanceMySql -Query "SELECT DATABASE()" -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
if ($actualDatabase.Trim() -ne $DatabaseName) {
    throw "Dedicated database identity check failed before startup"
}

if (Test-Path -LiteralPath $normalizedImageDirectory) {
    if (-not (Test-Path -LiteralPath $normalizedImageDirectory -PathType Container)) {
        throw "Performance image path exists but is not a directory"
    }
} else {
    New-Item -ItemType Directory -Path $normalizedImageDirectory -Force |
        Out-Null
}

$backendDirectory = Join-Path $repositoryRoot "backend"
$mavenWrapper = Join-Path $backendDirectory "mvnw.cmd"
if (-not $SkipPackage) {
    Push-Location -LiteralPath $backendDirectory
    try {
        & $mavenWrapper -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven package failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

$jarPath = Join-Path $backendDirectory "target\backend-1.0.0.jar"
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Packaged backend jar was not found: $jarPath"
}

$javaPath = Get-RequiredPerformanceCommand -Name "java.exe"
$gitCommit = (& git -C $repositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $gitCommit -notmatch '^[0-9a-f]{40}$') {
    throw "Unable to resolve the current Git commit"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runtimeLogDirectory = Join-Path (
    Join-Path $repositoryRoot "performance\results"
) "runtime-$timestamp"
New-Item -ItemType Directory -Path $runtimeLogDirectory -Force |
    Out-Null
$stdoutPath = Join-Path $runtimeLogDirectory "backend.stdout.log"
$stderrPath = Join-Path $runtimeLogDirectory "backend.stderr.log"

$environmentValues = @{
    "SPRING_PROFILES_ACTIVE" = "performance"
    "PERF_DATABASE_NAME" = $DatabaseName
    "PERF_DB_HOST" = $DatabaseHost
    "PERF_DB_PORT" = $DatabasePort.ToString()
    "PERF_DB_USER" = $DatabaseUser
    "PERF_IMAGE_DIRECTORY" = $normalizedImageDirectory
    "PERF_ENVIRONMENT_ID" = $EnvironmentId
    "PERF_SERVER_PORT" = $ServerPort.ToString()
    "PERF_GIT_COMMIT" = $gitCommit
    "PERF_REPOSITORY_ROOT" = $repositoryRoot
    "PERF_PRODUCTION_IMAGE_DIRECTORY" =
        "D:\WrongQuestionData\question-images"
    "PERF_PRODUCTION_BACKUP_DIRECTORY" =
        "D:\WrongQuestionBackups"
}
$previousValues = @{}
foreach ($name in $environmentValues.Keys) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable(
        $name,
        [System.EnvironmentVariableTarget]::Process
    )
    [Environment]::SetEnvironmentVariable(
        $name,
        [string]$environmentValues[$name],
        [System.EnvironmentVariableTarget]::Process
    )
}

$quotedJarPath = [char]34 + $jarPath + [char]34
$javaArguments = @(
    "-jar",
    $quotedJarPath,
    "--spring.profiles.active=performance"
)
$backendProcess = $null
try {
    $backendProcess = Start-Process -FilePath $javaPath -ArgumentList $javaArguments -WorkingDirectory $backendDirectory -WindowStyle Hidden -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
}
finally {
    foreach ($name in $environmentValues.Keys) {
        [Environment]::SetEnvironmentVariable(
            $name,
            $previousValues[$name],
            [System.EnvironmentVariableTarget]::Process
        )
    }
}

$baseUrl = "http://127.0.0.1:$ServerPort"
$deadline = [datetime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
$identity = $null
try {
    while ([datetime]::UtcNow -lt $deadline) {
        if ($backendProcess.HasExited) {
            $stderrTail = @(
                Get-Content -LiteralPath $stderrPath -Tail 30 -ErrorAction SilentlyContinue
            ) -join [Environment]::NewLine
            $stdoutTail = @(
                Get-Content -LiteralPath $stdoutPath -Tail 30 -ErrorAction SilentlyContinue
            ) -join [Environment]::NewLine
            throw (
                "Performance backend exited before identity verification." +
                [Environment]::NewLine +
                $stdoutTail +
                [Environment]::NewLine +
                $stderrTail
            )
        }
        try {
            $identity = Assert-PerformanceHttpIdentity -BaseUrl $baseUrl -EnvironmentId $EnvironmentId -DatabaseName $DatabaseName -ImageDirectory $normalizedImageDirectory -GitCommit $gitCommit
            break
        }
        catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if ($null -eq $identity) {
        throw "Performance backend identity did not become available"
    }
}
catch {
    if ($null -ne $backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force
    }
    throw
}

$localDirectory = Join-Path $repositoryRoot "performance\.local"
$runtimeManifestPath = Join-Path $localDirectory "runtime.json"
$manifest = [ordered]@{
    mode = "performance"
    processId = $backendProcess.Id
    baseUrl = $baseUrl
    environmentId = $EnvironmentId
    databaseName = $DatabaseName
    databaseHost = $DatabaseHost
    databasePort = $DatabasePort
    databaseUser = $DatabaseUser
    imageDirectory = $normalizedImageDirectory
    gitCommit = $gitCommit
    startedAt = (Get-Date).ToUniversalTime().ToString("o")
    stdoutLog = $stdoutPath
    stderrLog = $stderrPath
}
Write-PerformanceUtf8File -Path $runtimeManifestPath -Content (
    $manifest | ConvertTo-Json -Depth 5
)

[pscustomobject]@{
    Started         = $true
    ProcessId       = $backendProcess.Id
    BaseUrl         = $baseUrl
    DatabaseName    = $DatabaseName
    ImageDirectory  = $normalizedImageDirectory
    EnvironmentId   = $EnvironmentId
    GitCommit       = $gitCommit
    RuntimeManifest = $runtimeManifestPath
    StandardOutput  = $stdoutPath
    StandardError   = $stderrPath
} | Format-List
