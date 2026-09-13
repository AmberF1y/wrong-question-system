[CmdletBinding()]
param(
    [Parameter()]
    [string]$DatabaseName = "wrong_question_system_perf_20260912",

    [Parameter()]
    [string]$ImageDirectory,

    [Parameter()]
    [string]$EnvironmentId = "perf-small-20260912",

    [Parameter()]
    [string]$BaseUrl = "http://127.0.0.1:18080",

    [Parameter()]
    [string]$DatabaseHost = "127.0.0.1",

    [Parameter()]
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 3306,

    [Parameter()]
    [string]$DatabaseUser = "root",

    [Parameter(Mandatory = $true)]
    [ValidateSet("SEED_EMPTY_PERFORMANCE_DATABASE")]
    [string]$Confirmation
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

$actualDatabase = [string](
    Invoke-PerformanceMySql -Query "SELECT DATABASE()" -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
if ($actualDatabase.Trim() -ne $DatabaseName) {
    throw "Dedicated database identity check failed before seeding"
}

$requiredTablesQuery = @"
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = '$DatabaseName'
  AND table_name IN (
      'question',
      'knowledge_point',
      'question_knowledge_point',
      'question_review_state',
      'review_record'
  )
"@
$requiredTableCount = [int](
    Invoke-PerformanceMySql -Query $requiredTablesQuery -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
if ($requiredTableCount -ne 5) {
    throw (
        "Flyway schema is incomplete. Start the performance backend first; " +
        "expected 5 business tables, found $requiredTableCount"
    )
}

$metadataExistsQuery = @"
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = '$DatabaseName'
  AND table_name IN (
      'performance_dataset_metadata',
      'performance_question_fixture'
  )
"@
$metadataTableCount = [int](
    Invoke-PerformanceMySql -Query $metadataExistsQuery -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
if ($metadataTableCount -ne 0) {
    throw "Performance metadata already exists; no data will be changed"
}

$businessCountsOutput = @(
    Invoke-PerformanceMySql -Query "SELECT (SELECT COUNT(*) FROM question), (SELECT COUNT(*) FROM knowledge_point), (SELECT COUNT(*) FROM question_knowledge_point), (SELECT COUNT(*) FROM question_review_state), (SELECT COUNT(*) FROM review_record)" -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
$businessCounts = ([string]$businessCountsOutput[-1]) -split ([char]9)
if ($businessCounts.Count -ne 5) {
    throw "Unable to verify business table counts"
}
if (@($businessCounts | Where-Object { [long]$_ -ne 0 }).Count -ne 0) {
    throw (
        "Dedicated database is not empty; refusing to seed. Counts: " +
        ($businessCounts -join ",")
    )
}

if (Test-Path -LiteralPath $normalizedImageDirectory) {
    if (-not (Test-Path -LiteralPath $normalizedImageDirectory -PathType Container)) {
        throw "Performance image path is not a directory"
    }
    $existingImageEntry = Get-ChildItem -LiteralPath $normalizedImageDirectory -Force |
        Select-Object -First 1
    if ($null -ne $existingImageEntry) {
        throw "Performance image directory is not empty; refusing to seed"
    }
} else {
    New-Item -ItemType Directory -Path $normalizedImageDirectory -Force |
        Out-Null
}

$seedFile = Join-Path $repositoryRoot "performance\sql\seed-small.sql"
Invoke-PerformanceMySqlFile -SqlFile $seedFile -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser | Out-Null

$imageRows = @(
    Invoke-PerformanceMySql -Query "SELECT q.id, q.image_path, f.fixture_role FROM question q JOIN performance_question_fixture f ON f.question_id = q.id WHERE f.fixture_role IN ('IMAGE_SMALL', 'IMAGE_LARGE') ORDER BY q.id" -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
$pngBytes = [Convert]::FromBase64String(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
)
$createdImageCount = 0
foreach ($row in $imageRows) {
    if ([string]::IsNullOrWhiteSpace([string]$row)) {
        continue
    }
    $parts = ([string]$row) -split ([char]9)
    if ($parts.Count -ne 3) {
        throw "Unexpected image fixture row: $row"
    }
    $relativePath = $parts[1].Replace(
        "/",
        [System.IO.Path]::DirectorySeparatorChar
    )
    $targetPath = [System.IO.Path]::GetFullPath(
        (Join-Path $normalizedImageDirectory $relativePath)
    )
    if (-not (Test-PerformancePathInside -Path $targetPath -Root $normalizedImageDirectory)) {
        throw "Generated image path escaped the dedicated directory"
    }
    $targetDirectory = Split-Path -Parent $targetPath
    if (-not (Test-Path -LiteralPath $targetDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $targetDirectory -Force |
            Out-Null
    }
    $targetLength = if ($parts[2] -eq "IMAGE_LARGE") {
        1MB
    } else {
        64KB
    }
    $stream = New-Object System.IO.FileStream(
        $targetPath,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None
    )
    try {
        $stream.Write($pngBytes, 0, $pngBytes.Length)
        $stream.SetLength($targetLength)
    }
    finally {
        $stream.Dispose()
    }
    $createdImageCount++
}
if ($createdImageCount -ne 100) {
    throw "Expected 100 performance images, created $createdImageCount"
}

function Get-PerformanceQueryValues {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Query
    )

    return @(
        Invoke-PerformanceMySql -Query $Query -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser |
            ForEach-Object { ([string]$_).Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

$subjects = @(Get-PerformanceQueryValues -Query "SELECT name FROM knowledge_point WHERE parent_id IS NULL ORDER BY id")
$detailIds = @(Get-PerformanceQueryValues -Query "SELECT question_id FROM performance_question_fixture WHERE fixture_role = 'DETAIL' ORDER BY question_id" | ForEach-Object { [long]$_ })
$smallImageIds = @(Get-PerformanceQueryValues -Query "SELECT question_id FROM performance_question_fixture WHERE fixture_role = 'IMAGE_SMALL' ORDER BY question_id" | ForEach-Object { [long]$_ })
$largeImageIds = @(Get-PerformanceQueryValues -Query "SELECT question_id FROM performance_question_fixture WHERE fixture_role = 'IMAGE_LARGE' ORDER BY question_id" | ForEach-Object { [long]$_ })
$writeUpdateIds = @(Get-PerformanceQueryValues -Query "SELECT question_id FROM performance_question_fixture WHERE fixture_role = 'WRITE_UPDATE' ORDER BY question_id" | ForEach-Object { [long]$_ })
$reviewConcurrentIds = @(Get-PerformanceQueryValues -Query "SELECT question_id FROM performance_question_fixture WHERE fixture_role = 'REVIEW_CONCURRENT' ORDER BY question_id" | ForEach-Object { [long]$_ })
$createKnowledgePointIds = @(Get-PerformanceQueryValues -Query "SELECT child.id FROM knowledge_point child JOIN knowledge_point root ON root.id = child.parent_id WHERE root.name = 'PERF-SUBJECT-01' ORDER BY child.id LIMIT 2" | ForEach-Object { [long]$_ })

$metadataOutput = @(
    Invoke-PerformanceMySql -Query "SELECT dataset_id, scale_name, DATE_FORMAT(prepared_at, '%Y-%m-%dT%H:%i:%s.%fZ'), subject_count, knowledge_point_count, question_count, relation_count, review_state_count, review_record_count, image_question_count FROM performance_dataset_metadata" -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
$metadataParts = ([string]$metadataOutput[-1]) -split ([char]9)
if ($metadataParts.Count -ne 10) {
    throw "Unable to read performance dataset metadata"
}

$databaseBytesOutput = @(
    Invoke-PerformanceMySql -Query "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables WHERE table_schema = '$DatabaseName'" -DatabaseName $DatabaseName -DatabaseHost $DatabaseHost -DatabasePort $DatabasePort -DatabaseUser $DatabaseUser
)
$databaseBytes = [long](([string]$databaseBytesOutput[-1]).Trim())
$imageFiles = @(Get-ChildItem -LiteralPath $normalizedImageDirectory -File -Recurse)
$imageBytes = [long](($imageFiles | Measure-Object -Property Length -Sum).Sum)
$gitCommit = (& git -C $repositoryRoot rev-parse HEAD).Trim()
$gitStatus = @(& git -C $repositoryRoot status --short)

$config = [ordered]@{
    mode = "performance"
    environmentId = $EnvironmentId
    databaseName = $DatabaseName
    databaseHost = $DatabaseHost
    databasePort = $DatabasePort
    databaseUser = $DatabaseUser
    imageDirectory = $normalizedImageDirectory
    baseUrl = $BaseUrl.TrimEnd("/")
    gitCommit = $gitCommit
    gitStatusAtPreparation = $gitStatus
    dataset = [ordered]@{
        id = $metadataParts[0]
        scale = $metadataParts[1]
        preparedAt = $metadataParts[2]
        subjectCount = [int]$metadataParts[3]
        knowledgePointCount = [int]$metadataParts[4]
        questionCount = [int]$metadataParts[5]
        relationCount = [int]$metadataParts[6]
        reviewStateCount = [int]$metadataParts[7]
        reviewRecordCount = [int]$metadataParts[8]
        imageQuestionCount = [int]$metadataParts[9]
        databaseBytes = $databaseBytes
        imageFileCount = $imageFiles.Count
        imageBytes = $imageBytes
    }
    subjects = $subjects
    detailQuestionIds = $detailIds
    smallImageQuestionIds = $smallImageIds
    largeImageQuestionIds = $largeImageIds
    writeUpdateQuestionIds = $writeUpdateIds
    reviewConcurrentQuestionIds = $reviewConcurrentIds
    createKnowledgePointIds = $createKnowledgePointIds
    pageSize = 20
    middlePage = 25
    lastPage = 49
    requestTimeout = "5s"
}

$localDirectory = Join-Path $repositoryRoot "performance\.local"
$configPath = Join-Path $localDirectory "perf-environment.json"
Write-PerformanceUtf8File -Path $configPath -Content (
    $config | ConvertTo-Json -Depth 8
)

[pscustomobject]@{
    Seeded                 = $true
    DatasetId              = $config.dataset.id
    DatabaseName           = $DatabaseName
    SubjectCount           = $config.dataset.subjectCount
    KnowledgePointCount    = $config.dataset.knowledgePointCount
    QuestionCount          = $config.dataset.questionCount
    RelationCount          = $config.dataset.relationCount
    ReviewStateCount       = $config.dataset.reviewStateCount
    ReviewRecordCount      = $config.dataset.reviewRecordCount
    ImageFileCount         = $config.dataset.imageFileCount
    DatabaseBytes          = $databaseBytes
    ImageBytes             = $imageBytes
    EnvironmentConfig      = $configPath
} | Format-List
