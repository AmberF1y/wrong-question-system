[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ImageDirectory = "D:\WrongQuestionData\question-images",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ReviewZoneId = "Asia/Shanghai",

    [Parameter()]
    [ValidateRange(10, 300)]
    [int]$StartupTimeoutSeconds = 90,

    [Parameter()]
    [switch]$SkipNpmInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RequiredCommandPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue

    if ($null -eq $command) {
        throw "Required command was not found on PATH: $Name"
    }

    return $command.Source
}

function Get-NormalizedDirectoryPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $trimCharacters = [char[]]@(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $pathRoot = [System.IO.Path]::GetPathRoot($fullPath)

    if ($fullPath.Equals(
        $pathRoot,
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
        return $fullPath
    }

    return $fullPath.TrimEnd($trimCharacters)
}

function Test-PathInsideRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Root
    )

    $candidate = Get-NormalizedDirectoryPath -Path $Path
    $normalizedRoot = Get-NormalizedDirectoryPath -Path $Root
    $comparison = [System.StringComparison]::OrdinalIgnoreCase
    $prefix = $normalizedRoot + [System.IO.Path]::DirectorySeparatorChar

    return (
        $candidate.Equals($normalizedRoot, $comparison) `
            -or $candidate.StartsWith($prefix, $comparison)
    )
}

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [string]$HostName,

        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $client = New-Object System.Net.Sockets.TcpClient

    try {
        $task = $client.ConnectAsync($HostName, $Port)

        if (-not $task.Wait(500)) {
            return $false
        }

        return $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Wait-WebEndpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Uri,

        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process]$Process,

        [Parameter(Mandatory = $true)]
        [datetime]$Deadline
    )

    while ([datetime]::UtcNow -lt $Deadline) {
        if ($Process.HasExited) {
            throw "$Name process exited before its endpoint became available"
        }

        try {
            $response = Invoke-WebRequest `
                -Uri $Uri `
                -UseBasicParsing `
                -TimeoutSec 3 `
                -ErrorAction Stop

            if ($response.StatusCode -eq 200) {
                return
            }
        }
        catch {
            Start-Sleep -Seconds 1
        }
    }

    throw "$Name endpoint did not become available before the timeout: $Uri"
}

function ConvertTo-EncodedPowerShellCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command
    )

    $bytes = [System.Text.Encoding]::Unicode.GetBytes($Command)
    return [Convert]::ToBase64String($bytes)
}

$repoRoot = (
    Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)
).Path

$backendDirectory = Join-Path $repoRoot "backend"
$frontendDirectory = Join-Path $repoRoot "frontend"
$mavenWrapper = Join-Path $backendDirectory "mvnw.cmd"
$packageJson = Join-Path $frontendDirectory "package.json"
$nodeModulesDirectory = Join-Path $frontendDirectory "node_modules"

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven Wrapper was not found: $mavenWrapper"
}

if (-not (Test-Path -LiteralPath $packageJson -PathType Leaf)) {
    throw "Frontend package.json was not found: $packageJson"
}

$javaPath = Get-RequiredCommandPath -Name "java.exe"
$nodePath = Get-RequiredCommandPath -Name "node.exe"
$npmPath = Get-RequiredCommandPath -Name "npm.cmd"
$powerShellPath = Get-RequiredCommandPath -Name "powershell.exe"

$databasePassword = [Environment]::GetEnvironmentVariable(
    "DB_PASSWORD",
    [System.EnvironmentVariableTarget]::Process
)

if ([string]::IsNullOrWhiteSpace($databasePassword)) {
    throw "DB_PASSWORD must be set in the current PowerShell process"
}

$normalizedImageDirectory = Get-NormalizedDirectoryPath -Path $ImageDirectory

if (Test-PathInsideRoot -Path $normalizedImageDirectory -Root $repoRoot) {
    throw "ImageDirectory must be outside the Git repository"
}

if (-not (Test-Path -LiteralPath $normalizedImageDirectory)) {
    New-Item `
        -ItemType Directory `
        -Path $normalizedImageDirectory `
        -Force | Out-Null
}

if (-not (Test-Path -LiteralPath $normalizedImageDirectory -PathType Container)) {
    throw "ImageDirectory is not a directory: $normalizedImageDirectory"
}

foreach ($port in @(8080, 5173)) {
    if (Test-TcpPort -HostName "127.0.0.1" -Port $port) {
        throw "Required local port is already in use: $port"
    }
}

if (-not (Test-Path -LiteralPath $nodeModulesDirectory -PathType Container)) {
    if ($SkipNpmInstall) {
        throw "frontend/node_modules is missing and SkipNpmInstall was specified"
    }

    Write-Host "Installing frontend dependencies with npm ci..."
    Push-Location -LiteralPath $frontendDirectory

    try {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"

        try {
            & $npmPath ci
            $npmExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }
    }
    finally {
        Pop-Location
    }

    if ($npmExitCode -ne 0) {
        throw "npm ci failed with exit code ${npmExitCode}"
    }
}

[Environment]::SetEnvironmentVariable(
    "APP_QUESTION_IMAGE_DIRECTORY",
    $normalizedImageDirectory,
    [System.EnvironmentVariableTarget]::Process
)
[Environment]::SetEnvironmentVariable(
    "APP_REVIEW_ZONE_ID",
    $ReviewZoneId,
    [System.EnvironmentVariableTarget]::Process
)

$backendCommand = @'
& .\mvnw.cmd spring-boot:run
if ($LASTEXITCODE -ne 0) {
    Write-Host "Backend process exited with code $LASTEXITCODE" -ForegroundColor Red
}
'@

$frontendCommand = @'
& npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
if ($LASTEXITCODE -ne 0) {
    Write-Host "Frontend process exited with code $LASTEXITCODE" -ForegroundColor Red
}
'@

$backendProcess = $null
$frontendProcess = $null

try {
    Write-Host "Starting backend..."
    $backendProcess = Start-Process `
        -FilePath $powerShellPath `
        -ArgumentList @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-NoExit",
            "-EncodedCommand",
            (ConvertTo-EncodedPowerShellCommand -Command $backendCommand)
        ) `
        -WorkingDirectory $backendDirectory `
        -PassThru

    $deadline = [datetime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    Wait-WebEndpoint `
        -Name "Backend" `
        -Uri "http://127.0.0.1:8080/api/health" `
        -Process $backendProcess `
        -Deadline $deadline

    Write-Host "Starting frontend..."
    $frontendProcess = Start-Process `
        -FilePath $powerShellPath `
        -ArgumentList @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-NoExit",
            "-EncodedCommand",
            (ConvertTo-EncodedPowerShellCommand -Command $frontendCommand)
        ) `
        -WorkingDirectory $frontendDirectory `
        -PassThru

    $deadline = [datetime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    Wait-WebEndpoint `
        -Name "Frontend" `
        -Uri "http://127.0.0.1:5173/" `
        -Process $frontendProcess `
        -Deadline $deadline
}
catch {
    Write-Warning (
        "Startup did not complete. Close any child PowerShell windows that were opened. " +
        $_.Exception.Message
    )
    throw
}

[pscustomobject]@{
    RepositoryRoot       = $repoRoot
    BackendUrl           = "http://127.0.0.1:8080"
    FrontendUrl          = "http://127.0.0.1:5173"
    ImageDirectory       = $normalizedImageDirectory
    ReviewZoneId         = $ReviewZoneId
    JavaPath             = $javaPath
    NodePath             = $nodePath
    BackendProcessId     = $backendProcess.Id
    FrontendProcessId    = $frontendProcess.Id
    DatabasePasswordRead = $true
} | Format-List
