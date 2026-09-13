Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:PerformanceRepositoryRoot = (
    Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")
).Path

function Get-PerformanceRepositoryRoot {
    return $script:PerformanceRepositoryRoot
}

function Get-NormalizedPerformancePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $root = [System.IO.Path]::GetPathRoot($fullPath)
    if ($fullPath.Equals(
        $root,
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
        return $fullPath
    }
    return $fullPath.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
}

function Test-PerformancePathInside {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Root
    )

    $candidate = Get-NormalizedPerformancePath -Path $Path
    $normalizedRoot = Get-NormalizedPerformancePath -Path $Root
    $comparison = [System.StringComparison]::OrdinalIgnoreCase
    $prefix = $normalizedRoot + [System.IO.Path]::DirectorySeparatorChar
    return (
        $candidate.Equals($normalizedRoot, $comparison) -or
        $candidate.StartsWith($prefix, $comparison)
    )
}

function Assert-PerformanceDatabaseName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DatabaseName
    )

    if (
        $DatabaseName -notmatch
        '^wrong_question_system_perf_[0-9]{8}(?:_[a-z0-9_]+)?$'
    ) {
        throw (
            "DatabaseName must match the dedicated performance pattern: " +
            "wrong_question_system_perf_YYYYMMDD[_suffix]"
        )
    }
    if ($DatabaseName -in @(
        "wrong_question_system",
        "wrong_question_system_test"
    )) {
        throw "Production and ordinary test databases are forbidden"
    }
}

function Assert-PerformanceImageDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ImageDirectory,

        [Parameter(Mandatory = $true)]
        [string]$DatabaseName,

        [Parameter()]
        [string]$ProductionImageDirectory =
            "D:\WrongQuestionData\question-images",

        [Parameter()]
        [string]$ProductionBackupDirectory =
            "D:\WrongQuestionBackups"
    )

    Assert-PerformanceDatabaseName -DatabaseName $DatabaseName
    $normalized = Get-NormalizedPerformancePath -Path $ImageDirectory
    $repository = Get-PerformanceRepositoryRoot

    if (Test-PerformancePathInside -Path $normalized -Root $repository) {
        throw "Performance image directory must be outside the repository"
    }
    if (Test-PerformancePathInside -Path $normalized -Root $ProductionImageDirectory) {
        throw "Performance image directory must not use production images"
    }
    if (Test-PerformancePathInside -Path $normalized -Root $ProductionBackupDirectory) {
        throw "Performance image directory must not use production backups"
    }

    $segments = $normalized -split '[\\/]'
    if (-not ($segments | Where-Object {
        $_.Equals(
            $DatabaseName,
            [System.StringComparison]::OrdinalIgnoreCase
        )
    })) {
        throw "Performance image path must contain DatabaseName as a segment"
    }

    return $normalized
}

function Get-RequiredPerformanceCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Required command was not found: $Name"
    }
    return $command.Source
}

function Get-PerformanceK6Path {
    $command = Get-Command "k6.exe" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $candidates = @(
        (Join-Path $env:ProgramFiles "k6\k6.exe"),
        (Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Links\k6.exe")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    throw (
        "k6.exe was not found on PATH or in standard Windows locations. " +
        "Open a new PowerShell after installing k6."
    )
}

function Get-PerformanceDatabasePassword {
    $password = [Environment]::GetEnvironmentVariable(
        "DB_PASSWORD",
        [System.EnvironmentVariableTarget]::Process
    )
    if ([string]::IsNullOrWhiteSpace($password)) {
        throw "DB_PASSWORD must be set in the current PowerShell process"
    }
    return $password
}

function Invoke-PerformanceMySql {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Query,

        [Parameter()]
        [string]$DatabaseName,

        [Parameter()]
        [string]$DatabaseHost = "127.0.0.1",

        [Parameter()]
        [ValidateRange(1, 65535)]
        [int]$DatabasePort = 3306,

        [Parameter()]
        [string]$DatabaseUser = "root"
    )

    if (-not [string]::IsNullOrWhiteSpace($DatabaseName)) {
        Assert-PerformanceDatabaseName -DatabaseName $DatabaseName
    }
    $mysqlPath = Get-RequiredPerformanceCommand -Name "mysql.exe"
    $password = Get-PerformanceDatabasePassword
    $previousMysqlPassword = [Environment]::GetEnvironmentVariable(
        "MYSQL_PWD",
        [System.EnvironmentVariableTarget]::Process
    )

    $arguments = @(
        "--host=$DatabaseHost",
        "--port=$DatabasePort",
        "--user=$DatabaseUser",
        "--batch",
        "--raw",
        "--skip-column-names",
        "--default-character-set=utf8mb4"
    )
    if (-not [string]::IsNullOrWhiteSpace($DatabaseName)) {
        $arguments += "--database=$DatabaseName"
    }
    $arguments += "--execute=$Query"

    try {
        [Environment]::SetEnvironmentVariable(
            "MYSQL_PWD",
            $password,
            [System.EnvironmentVariableTarget]::Process
        )
        $output = & $mysqlPath @arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            "MYSQL_PWD",
            $previousMysqlPassword,
            [System.EnvironmentVariableTarget]::Process
        )
    }

    if ($exitCode -ne 0) {
        throw (
            "mysql.exe failed with exit code {0}: {1}" -f
            $exitCode,
            $output
        )
    }
    return $output
}

function Invoke-PerformanceMySqlFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SqlFile,

        [Parameter(Mandatory = $true)]
        [string]$DatabaseName,

        [Parameter()]
        [string]$DatabaseHost = "127.0.0.1",

        [Parameter()]
        [ValidateRange(1, 65535)]
        [int]$DatabasePort = 3306,

        [Parameter()]
        [string]$DatabaseUser = "root"
    )

    Assert-PerformanceDatabaseName -DatabaseName $DatabaseName
    $resolvedSqlFile = (Resolve-Path -LiteralPath $SqlFile).Path
    $mysqlPath = Get-RequiredPerformanceCommand -Name "mysql.exe"
    $password = Get-PerformanceDatabasePassword
    $previousMysqlPassword = [Environment]::GetEnvironmentVariable(
        "MYSQL_PWD",
        [System.EnvironmentVariableTarget]::Process
    )
    $quote = [char]34
    $command = (
        $quote +
        $mysqlPath +
        $quote +
        " --host=" +
        $DatabaseHost +
        " --port=" +
        $DatabasePort +
        " --user=" +
        $DatabaseUser +
        " --database=" +
        $DatabaseName +
        " --default-character-set=utf8mb4 --batch --raw < " +
        $quote +
        $resolvedSqlFile +
        $quote
    )

    try {
        [Environment]::SetEnvironmentVariable(
            "MYSQL_PWD",
            $password,
            [System.EnvironmentVariableTarget]::Process
        )
        $output = & cmd.exe /d /c $command 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            "MYSQL_PWD",
            $previousMysqlPassword,
            [System.EnvironmentVariableTarget]::Process
        )
    }
    if ($exitCode -ne 0) {
        throw (
            "mysql.exe SQL file failed with exit code {0}: {1}" -f
            $exitCode,
            $output
        )
    }
    return $output
}

function Test-PerformanceTcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetHost,

        [Parameter(Mandatory = $true)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($TargetHost, $Port)
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

function Write-PerformanceUtf8File {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8WithoutBom)
}

function Get-PerformanceJsonFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 |
        ConvertFrom-Json
}

function Assert-PerformanceHttpIdentity {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,

        [Parameter(Mandatory = $true)]
        [string]$EnvironmentId,

        [Parameter(Mandatory = $true)]
        [string]$DatabaseName,

        [Parameter(Mandatory = $true)]
        [string]$ImageDirectory,

        [Parameter(Mandatory = $true)]
        [string]$GitCommit
    )

    $uri = [System.Uri]$BaseUrl
    if (
        $uri.Scheme -ne "http" -or
        $uri.Host -ne "127.0.0.1" -or
        $uri.Port -eq 8080
    ) {
        throw "Performance BaseUrl must be loopback HTTP and not port 8080"
    }

    $identity = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd("/") + "/api/performance/identity") -Headers @{ "X-Performance-Environment" = $EnvironmentId } -TimeoutSec 3 -Method Get

    $normalizedImage = Get-NormalizedPerformancePath -Path $ImageDirectory
    $actualImage = Get-NormalizedPerformancePath -Path $identity.imageDirectory
    if (
        $identity.mode -ne "performance" -or
        $identity.environmentId -ne $EnvironmentId -or
        $identity.databaseName -ne $DatabaseName -or
        $actualImage -ne $normalizedImage -or
        $identity.gitCommit -ne $GitCommit
    ) {
        throw "Backend performance identity did not match approved values"
    }
    return $identity
}

Export-ModuleMember -Function @(
    "Get-PerformanceRepositoryRoot",
    "Get-NormalizedPerformancePath",
    "Test-PerformancePathInside",
    "Assert-PerformanceDatabaseName",
    "Assert-PerformanceImageDirectory",
    "Get-RequiredPerformanceCommand",
    "Get-PerformanceK6Path",
    "Invoke-PerformanceMySql",
    "Invoke-PerformanceMySqlFile",
    "Test-PerformanceTcpPort",
    "Write-PerformanceUtf8File",
    "Get-PerformanceJsonFile",
    "Assert-PerformanceHttpIdentity"
)
