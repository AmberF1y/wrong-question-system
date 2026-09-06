[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ImageDirectory = "D:\WrongQuestionData\question-images",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$BackupRoot = "D:\WrongQuestionBackups",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseName = "wrong_question_system",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseHost = "127.0.0.1",

    [Parameter()]
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 3306,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseUser = "root",

    [Parameter()]
    [string]$MySqlDumpPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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

function Get-RelativeFilePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string]$RootPath
    )

    $normalizedRoot = Get-NormalizedDirectoryPath -Path $RootPath
    $prefix = $normalizedRoot + [System.IO.Path]::DirectorySeparatorChar
    $normalizedFile = [System.IO.Path]::GetFullPath($FilePath)

    if (-not $normalizedFile.StartsWith(
        $prefix,
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
        throw "File is outside the expected root: $FilePath"
    }

    return $normalizedFile.Substring($prefix.Length)
}

function Get-MySqlDumpExecutable {
    param(
        [Parameter()]
        [string]$RequestedPath
    )

    if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
        if (-not (Test-Path -LiteralPath $RequestedPath -PathType Leaf)) {
            throw "mysqldump executable was not found: $RequestedPath"
        }

        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $command = Get-Command "mysqldump.exe" -ErrorAction SilentlyContinue

    if ($null -eq $command) {
        throw "mysqldump.exe was not found on PATH; use MySqlDumpPath to specify it"
    }

    return $command.Source
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    try {
        & $FilePath @Arguments
        return $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
}

$repoRoot = (
    Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)
).Path

$normalizedImageDirectory = Get-NormalizedDirectoryPath -Path $ImageDirectory
$normalizedBackupRoot = Get-NormalizedDirectoryPath -Path $BackupRoot

if (-not (Test-Path -LiteralPath $normalizedImageDirectory -PathType Container)) {
    throw "ImageDirectory was not found: $normalizedImageDirectory"
}

if (Test-PathInsideRoot -Path $normalizedImageDirectory -Root $repoRoot) {
    throw "ImageDirectory must be outside the Git repository"
}

if (Test-PathInsideRoot -Path $normalizedBackupRoot -Root $repoRoot) {
    throw "BackupRoot must be outside the Git repository"
}

if (
    (Test-PathInsideRoot `
        -Path $normalizedBackupRoot `
        -Root $normalizedImageDirectory) `
        -or (Test-PathInsideRoot `
            -Path $normalizedImageDirectory `
            -Root $normalizedBackupRoot)
) {
    throw "BackupRoot and ImageDirectory must not contain one another"
}

if (Test-TcpPort -HostName "127.0.0.1" -Port 8080) {
    throw "Backend port 8080 is listening; stop the backend before backup"
}

if (-not (Test-Path -LiteralPath $normalizedBackupRoot)) {
    New-Item `
        -ItemType Directory `
        -Path $normalizedBackupRoot `
        -Force | Out-Null
}

if (-not (Test-Path -LiteralPath $normalizedBackupRoot -PathType Container)) {
    throw "BackupRoot is not a directory: $normalizedBackupRoot"
}

$mysqldump = Get-MySqlDumpExecutable -RequestedPath $MySqlDumpPath
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$stagingName = ".incomplete-wrong-question-system-$timestamp-$PID"
$finalName = "wrong-question-system-$timestamp"
$stagingDirectory = Join-Path $normalizedBackupRoot $stagingName
$finalDirectory = Join-Path $normalizedBackupRoot $finalName

if (
    (Test-Path -LiteralPath $stagingDirectory) `
        -or (Test-Path -LiteralPath $finalDirectory)
) {
    throw "Backup target already exists for timestamp $timestamp"
}

New-Item -ItemType Directory -Path $stagingDirectory | Out-Null

$sqlPath = Join-Path $stagingDirectory "database.sql"
$backupImageDirectory = Join-Path $stagingDirectory "question-images"
$summaryPath = Join-Path $stagingDirectory "backup-summary.txt"
$manifestPath = Join-Path $stagingDirectory "manifest.tsv"

New-Item `
    -ItemType Directory `
    -Path $backupImageDirectory | Out-Null

try {
    Write-Host "Exporting MySQL database..."
    $dumpArguments = @(
        "--host=$DatabaseHost",
        "--port=$DatabasePort",
        "--user=$DatabaseUser",
        "--password",
        "--single-transaction",
        "--quick",
        "--routines",
        "--triggers",
        "--no-tablespaces",
        "--set-gtid-purged=OFF",
        "--default-character-set=utf8mb4",
        "--result-file=$sqlPath",
        $DatabaseName
    )

    $dumpExitCode = Invoke-NativeCommand `
        -FilePath $mysqldump `
        -Arguments $dumpArguments

    if ($dumpExitCode -ne 0) {
        throw "mysqldump failed with exit code ${dumpExitCode}"
    }

    if (
        -not (Test-Path -LiteralPath $sqlPath -PathType Leaf) `
            -or (Get-Item -LiteralPath $sqlPath).Length -eq 0
    ) {
        throw "mysqldump did not create a non-empty SQL file"
    }

    Write-Host "Copying question images..."
    Get-ChildItem `
        -LiteralPath $normalizedImageDirectory `
        -Force | ForEach-Object {
            Copy-Item `
                -LiteralPath $_.FullName `
                -Destination $backupImageDirectory `
                -Recurse `
                -Force
        }

    $sourceImageFiles = @(
        Get-ChildItem `
            -LiteralPath $normalizedImageDirectory `
            -Recurse `
            -File
    )
    $copiedImageFiles = @(
        Get-ChildItem `
            -LiteralPath $backupImageDirectory `
            -Recurse `
            -File
    )

    if ($sourceImageFiles.Count -ne $copiedImageFiles.Count) {
        throw "Copied image file count does not match the source"
    }

    foreach ($sourceFile in $sourceImageFiles) {
        $relativePath = Get-RelativeFilePath `
            -FilePath $sourceFile.FullName `
            -RootPath $normalizedImageDirectory
        $copiedPath = Join-Path $backupImageDirectory $relativePath

        if (-not (Test-Path -LiteralPath $copiedPath -PathType Leaf)) {
            throw "Copied image file was not found: $relativePath"
        }

        $sourceHash = (
            Get-FileHash `
                -LiteralPath $sourceFile.FullName `
                -Algorithm SHA256
        ).Hash
        $copiedHash = (
            Get-FileHash `
                -LiteralPath $copiedPath `
                -Algorithm SHA256
        ).Hash

        if ($sourceHash -ne $copiedHash) {
            throw "Copied image hash does not match the source: $relativePath"
        }
    }

    $gitCommit = "unavailable"
    $gitCommand = Get-Command "git.exe" -ErrorAction SilentlyContinue

    if ($null -ne $gitCommand) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"

        try {
            $gitOutput = @(
                & $gitCommand.Source `
                    -C $repoRoot `
                    rev-parse HEAD `
                    2>$null
            )
            $gitExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }

        if ($gitExitCode -eq 0) {
            $gitCommit = [string]::Join("", $gitOutput).Trim()
        }
    }

    @(
        "BackupFormatVersion=1"
        "CreatedAt=$((Get-Date).ToString('o'))"
        "DatabaseHost=$DatabaseHost"
        "DatabasePort=$DatabasePort"
        "DatabaseName=$DatabaseName"
        "DatabaseUser=$DatabaseUser"
        "SourceImageDirectory=$normalizedImageDirectory"
        "SourceImageFileCount=$($sourceImageFiles.Count)"
        "GitCommit=$gitCommit"
    ) | Set-Content `
        -LiteralPath $summaryPath `
        -Encoding UTF8

    $manifestRows = @(
        Get-ChildItem `
            -LiteralPath $stagingDirectory `
            -Recurse `
            -File | Where-Object {
                $_.FullName -ne $manifestPath
            } | ForEach-Object {
                [pscustomobject]@{
                    Sha256 = (
                        Get-FileHash `
                            -LiteralPath $_.FullName `
                            -Algorithm SHA256
                    ).Hash.ToLowerInvariant()
                    Bytes = $_.Length
                    Path = Get-RelativeFilePath `
                        -FilePath $_.FullName `
                        -RootPath $stagingDirectory
                }
            } | Sort-Object Path
    )

    $manifestRows | ConvertTo-Csv `
        -Delimiter "`t" `
        -NoTypeInformation | Set-Content `
            -LiteralPath $manifestPath `
            -Encoding UTF8

    $manifestHash = (
        Get-FileHash `
            -LiteralPath $manifestPath `
            -Algorithm SHA256
    ).Hash.ToLowerInvariant()

    Move-Item `
        -LiteralPath $stagingDirectory `
        -Destination $finalDirectory

    $finalSqlPath = Join-Path $finalDirectory "database.sql"

    [pscustomobject]@{
        BackupDirectory       = $finalDirectory
        DatabaseName          = $DatabaseName
        SqlBytes              = (Get-Item -LiteralPath $finalSqlPath).Length
        SourceImageFileCount  = $sourceImageFiles.Count
        CopiedImageFileCount  = $copiedImageFiles.Count
        ManifestFileCount     = $manifestRows.Count
        ManifestSha256        = $manifestHash
        GitCommit             = $gitCommit
        IncompleteDirectory   = $false
    } | Format-List
}
catch {
    Write-Warning (
        "Backup failed. Any partial data remains under: $stagingDirectory"
    )
    throw
}
