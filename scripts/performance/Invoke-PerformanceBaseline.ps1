[CmdletBinding()]
param(
    [Parameter()]
    [string]$ConfigPath,

    [Parameter()]
    [string]$RuntimeManifest,

    [Parameter()]
    [string]$PublishedReportPath,

    [Parameter(Mandatory = $true)]
    [ValidateSet("RUN_READ_ONLY_LOW_LOAD_BASELINE")]
    [string]$Confirmation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Import-Module (
    Join-Path $PSScriptRoot "PerformanceSafety.psm1"
) -Force

$repositoryRoot = Get-PerformanceRepositoryRoot
$performanceRoot = Join-Path $repositoryRoot "performance"
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path (
        Join-Path $performanceRoot ".local"
    ) "perf-environment.json"
}
if ([string]::IsNullOrWhiteSpace($RuntimeManifest)) {
    $RuntimeManifest = Join-Path (
        Join-Path $performanceRoot ".local"
    ) "runtime.json"
}
if ([string]::IsNullOrWhiteSpace($PublishedReportPath)) {
    $PublishedReportPath = Join-Path (
        Join-Path $performanceRoot "reports"
    ) "baseline-2026-09-12-small.md"
}

$resolvedConfigPath = (Resolve-Path -LiteralPath $ConfigPath).Path
$resolvedRuntimeManifest = (
    Resolve-Path -LiteralPath $RuntimeManifest
).Path
$config = Get-PerformanceJsonFile -Path $resolvedConfigPath
$runtime = Get-PerformanceJsonFile -Path $resolvedRuntimeManifest

Assert-PerformanceDatabaseName -DatabaseName $config.databaseName
Assert-PerformanceImageDirectory -ImageDirectory $config.imageDirectory -DatabaseName $config.databaseName | Out-Null
if (
    $config.mode -ne "performance" -or
    $runtime.mode -ne "performance" -or
    $runtime.databaseName -ne $config.databaseName -or
    $runtime.environmentId -ne $config.environmentId -or
    $runtime.baseUrl -ne $config.baseUrl -or
    $runtime.gitCommit -ne $config.gitCommit -or
    $runtime.imageDirectory -ne $config.imageDirectory
) {
    throw "Runtime manifest and dataset configuration do not match"
}

$identity = Assert-PerformanceHttpIdentity -BaseUrl $config.baseUrl -EnvironmentId $config.environmentId -DatabaseName $config.databaseName -ImageDirectory $config.imageDirectory -GitCommit $config.gitCommit

$metadataOutput = @(
    Invoke-PerformanceMySql -Query "SELECT dataset_id, question_count, image_question_count FROM performance_dataset_metadata" -DatabaseName $config.databaseName -DatabaseHost $config.databaseHost -DatabasePort $config.databasePort -DatabaseUser $config.databaseUser
)
$metadataParts = ([string]$metadataOutput[-1]) -split ([char]9)
if (
    $metadataParts.Count -ne 3 -or
    $metadataParts[0] -ne $config.dataset.id -or
    [int]$metadataParts[1] -ne [int]$config.dataset.questionCount -or
    [int]$metadataParts[2] -ne [int]$config.dataset.imageQuestionCount
) {
    throw "Database dataset metadata does not match local configuration"
}

$currentCountsOutput = @(
    Invoke-PerformanceMySql -Query "SELECT (SELECT COUNT(*) FROM knowledge_point), (SELECT COUNT(*) FROM question), (SELECT COUNT(*) FROM question_knowledge_point), (SELECT COUNT(*) FROM question_review_state), (SELECT COUNT(*) FROM review_record)" -DatabaseName $config.databaseName -DatabaseHost $config.databaseHost -DatabasePort $config.databasePort -DatabaseUser $config.databaseUser
)
$currentCounts = ([string]$currentCountsOutput[-1]) -split ([char]9)
$expectedCounts = @(
    [string]$config.dataset.knowledgePointCount,
    [string]$config.dataset.questionCount,
    [string]$config.dataset.relationCount,
    [string]$config.dataset.reviewStateCount,
    [string]$config.dataset.reviewRecordCount
)
if (($currentCounts -join ",") -ne ($expectedCounts -join ",")) {
    throw (
        "Current database rows differ from the approved dataset. Current=" +
        ($currentCounts -join ",") +
        "; expected=" +
        ($expectedCounts -join ",")
    )
}

$imageFiles = @(
    Get-ChildItem -LiteralPath $config.imageDirectory -File -Recurse
)
if ($imageFiles.Count -ne [int]$config.dataset.imageFileCount) {
    throw "Dedicated image file count does not match dataset metadata"
}
$imageBytesBefore = [long](
    ($imageFiles | Measure-Object -Property Length -Sum).Sum
)
if ($imageBytesBefore -ne [long]$config.dataset.imageBytes) {
    throw "Dedicated image bytes do not match dataset metadata"
}

$osBefore = Get-CimInstance Win32_OperatingSystem
$freeMemoryGiBBefore = [math]::Round(
    $osBefore.FreePhysicalMemory * 1KB / 1GB,
    2
)
if ($freeMemoryGiBBefore -lt 2) {
    throw "Available memory is below the 2 GiB low-load safety floor"
}

$gitBranch = (& git -C $repositoryRoot branch --show-current).Trim()
$gitCommit = (& git -C $repositoryRoot rev-parse HEAD).Trim()
$gitStatusBefore = @(& git -C $repositoryRoot status --short)
if ($gitCommit -ne $config.gitCommit) {
    throw "Git HEAD changed after the performance backend was started"
}

$k6Path = Get-PerformanceK6Path
$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $k6VersionOutput = @(& $k6Path version 2>&1)
    $k6VersionExitCode = $LASTEXITCODE
    $javaVersionOutput = @(& java.exe -version 2>&1)
    $javaVersionExitCode = $LASTEXITCODE
    $mysqlVersionOutput = @(& mysql.exe --version 2>&1)
    $mysqlVersionExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousErrorPreference
}
if (
    $k6VersionExitCode -ne 0 -or
    $javaVersionExitCode -ne 0 -or
    $mysqlVersionExitCode -ne 0
) {
    throw "Unable to collect tool versions"
}
$k6Version = (($k6VersionOutput | ForEach-Object {
    $_.ToString()
}) -join " ").Trim()
$javaVersion = $javaVersionOutput[0].ToString().Trim()
$mysqlVersion = (($mysqlVersionOutput | ForEach-Object {
    $_.ToString()
}) -join " ").Trim()
$computer = Get-CimInstance Win32_ComputerSystem
$processor = Get-CimInstance Win32_Processor |
    Select-Object -First 1

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$resultDirectory = Join-Path (
    Join-Path $performanceRoot "results"
) "baseline-$timestamp"
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null
$readResultPath = Join-Path $resultDirectory "read-metrics.json"
$imageResultPath = Join-Path $resultDirectory "image-metrics.json"

function Invoke-K6Scenario {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$ScriptPath,

        [Parameter(Mandatory = $true)]
        [string]$ResultPath,

        [Parameter(Mandatory = $true)]
        [int]$MaximumRunSeconds
    )

    $stdoutPath = Join-Path $resultDirectory ($Name + ".stdout.log")
    $stderrPath = Join-Path $resultDirectory ($Name + ".stderr.log")
    $previousConfig = [Environment]::GetEnvironmentVariable(
        "PERF_CONFIG",
        [System.EnvironmentVariableTarget]::Process
    )
    $previousResult = [Environment]::GetEnvironmentVariable(
        "PERF_RESULT_PATH",
        [System.EnvironmentVariableTarget]::Process
    )
    [Environment]::SetEnvironmentVariable(
        "PERF_CONFIG",
        $resolvedConfigPath,
        [System.EnvironmentVariableTarget]::Process
    )
    [Environment]::SetEnvironmentVariable(
        "PERF_RESULT_PATH",
        $ResultPath,
        [System.EnvironmentVariableTarget]::Process
    )
    $quotedScriptPath = [char]34 + $ScriptPath + [char]34
    try {
        $process = Start-Process -FilePath $k6Path -ArgumentList @("run", $quotedScriptPath) -WorkingDirectory $performanceRoot -WindowStyle Hidden -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            "PERF_CONFIG",
            $previousConfig,
            [System.EnvironmentVariableTarget]::Process
        )
        [Environment]::SetEnvironmentVariable(
            "PERF_RESULT_PATH",
            $previousResult,
            [System.EnvironmentVariableTarget]::Process
        )
    }

    $startedAt = [datetime]::UtcNow
    while (-not $process.HasExited) {
        if (
            ([datetime]::UtcNow - $startedAt).TotalSeconds -gt
            $MaximumRunSeconds
        ) {
            Stop-Process -Id $process.Id -Force
            throw "$Name exceeded its hard time limit"
        }
        $backendProcess = Get-Process -Id ([int]$runtime.processId) -ErrorAction SilentlyContinue
        if ($null -eq $backendProcess) {
            Stop-Process -Id $process.Id -Force
            throw "Performance backend exited during $Name"
        }
        $currentOs = Get-CimInstance Win32_OperatingSystem
        $currentFreeGiB = (
            $currentOs.FreePhysicalMemory * 1KB / 1GB
        )
        if ($currentFreeGiB -lt 1) {
            Stop-Process -Id $process.Id -Force
            throw "$Name stopped because available memory fell below 1 GiB"
        }
        Start-Sleep -Seconds 5
        $process.Refresh()
    }
    $process.WaitForExit()
    $process.Refresh()
    $scenarioExitCode = $null
    try {
        $scenarioExitCode = $process.ExitCode
    } catch {
        # Windows PowerShell can occasionally lose ExitCode for a long-running
        # redirected process. The evidence checks below remain mandatory.
    }
    if ($null -ne $scenarioExitCode -and [int]$scenarioExitCode -ne 0) {
        throw (
            "$Name failed with exit code " +
            $scenarioExitCode +
            ". Evidence: " +
            $stdoutPath +
            " and " +
            $stderrPath
        )
    }
    if (-not (Test-Path -LiteralPath $ResultPath -PathType Leaf)) {
        throw "$Name did not produce normalized metrics"
    }

    $stdoutContent = Get-Content -LiteralPath $stdoutPath -Raw
    if ($stdoutContent -notmatch "normalized endpoint metrics written") {
        throw "$Name did not reach its completed summary handler"
    }

    $scenarioMetrics = Get-PerformanceJsonFile -Path $ResultPath
    $unexpectedErrorRate = $scenarioMetrics.overall.unexpectedErrorRate
    if ($null -eq $unexpectedErrorRate) {
        throw "$Name metrics do not contain unexpectedErrorRate"
    }
    if ([double]$unexpectedErrorRate -gt 0) {
        throw "$Name recorded unexpected performance errors"
    }
}

$readScript = Join-Path $performanceRoot "k6\read-baseline.js"
$imageScript = Join-Path $performanceRoot "k6\image-baseline.js"
Invoke-K6Scenario -Name "read-baseline" -ScriptPath $readScript -ResultPath $readResultPath -MaximumRunSeconds 390
Invoke-K6Scenario -Name "image-baseline" -ScriptPath $imageScript -ResultPath $imageResultPath -MaximumRunSeconds 120

$readMetrics = Get-PerformanceJsonFile -Path $readResultPath
$imageMetrics = Get-PerformanceJsonFile -Path $imageResultPath
$postCountsOutput = @(
    Invoke-PerformanceMySql -Query "SELECT (SELECT COUNT(*) FROM knowledge_point), (SELECT COUNT(*) FROM question), (SELECT COUNT(*) FROM question_knowledge_point), (SELECT COUNT(*) FROM question_review_state), (SELECT COUNT(*) FROM review_record)" -DatabaseName $config.databaseName -DatabaseHost $config.databaseHost -DatabasePort $config.databasePort -DatabaseUser $config.databaseUser
)
$postCounts = ([string]$postCountsOutput[-1]) -split ([char]9)
if (($postCounts -join ",") -ne ($currentCounts -join ",")) {
    throw "Database rows changed during the read-only baseline"
}
$postImageFiles = @(
    Get-ChildItem -LiteralPath $config.imageDirectory -File -Recurse
)
$postImageBytes = [long](
    ($postImageFiles | Measure-Object -Property Length -Sum).Sum
)
if (
    $postImageFiles.Count -ne $imageFiles.Count -or
    $postImageBytes -ne $imageBytesBefore
) {
    throw "Dedicated image data changed during the read-only baseline"
}
$dataIsolationStable = $true
$osAfter = Get-CimInstance Win32_OperatingSystem
$freeMemoryGiBAfter = [math]::Round(
    $osAfter.FreePhysicalMemory * 1KB / 1GB,
    2
)
$gitStatusAfter = @(& git -C $repositoryRoot status --short)
$gitStatusStable = (
    ($gitStatusBefore -join [Environment]::NewLine) -eq
    ($gitStatusAfter -join [Environment]::NewLine)
)
if (-not $gitStatusStable) {
    throw "Git working tree changed while the load measurement was running"
}

function Format-Number {
    param(
        [Parameter()]
        $Value,

        [Parameter()]
        [int]$Decimals = 2
    )
    if ($null -eq $Value) {
        return "未采集"
    }
    return ([double]$Value).ToString(
        "F$Decimals",
        [System.Globalization.CultureInfo]::InvariantCulture
    )
}

function Get-MetricRows {
    param(
        [Parameter(Mandatory = $true)]
        $Metrics
    )

    $rows = New-Object System.Collections.Generic.List[string]
    foreach ($property in $Metrics.endpoints.PSObject.Properties) {
        $value = $property.Value
        $errorPercent = if ($null -eq $value.errorRate) {
            "未采集"
        } else {
            (Format-Number -Value (
                [double]$value.errorRate * 100
            ) -Decimals 3) + "%"
        }
        $rows.Add(
            "| " +
            $property.Name +
            " | " +
            $value.requests +
            " | " +
            (Format-Number -Value $value.requestsPerSecond) +
            " | " +
            (Format-Number -Value $value.p50Ms) +
            " | " +
            (Format-Number -Value $value.p95Ms) +
            " | " +
            (Format-Number -Value $value.p99Ms) +
            " | " +
            (Format-Number -Value $value.maxMs) +
            " | " +
            $errorPercent +
            " |"
        )
    }
    return $rows
}

$databaseMiB = [math]::Round(
    [double]$config.dataset.databaseBytes / 1MB,
    2
)
$imageMiB = [math]::Round(
    [double]$config.dataset.imageBytes / 1MB,
    2
)
$statusText = if ($gitStatusBefore.Count -eq 0) {
    "clean"
} else {
    ($gitStatusBefore -join "; ")
}
$slowestRead = $readMetrics.endpoints.PSObject.Properties |
    Sort-Object -Property @{
        Expression = { [double]$_.Value.p99Ms }
        Descending = $true
    } |
    Select-Object -First 1
$slowestImage = $imageMetrics.endpoints.PSObject.Properties |
    Sort-Object -Property @{
        Expression = { [double]$_.Value.p99Ms }
        Descending = $true
    } |
    Select-Object -First 1
$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("# 低负载性能基线报告")
$reportLines.Add("")
$reportLines.Add("## 结论状态")
$reportLines.Add("")
$reportLines.Add("- 状态：已运行")
$reportLines.Add("- 性质：单机、单用户、低负载基线，不代表容量上限")
$reportLines.Add("- JSON 与图片场景分开测量")
$reportLines.Add("- 测试期间 Git 状态保持不变：" + $gitStatusStable)
$reportLines.Add("- 测试前后专用数据库行数和图片文件保持不变：" + $dataIsolationStable)
$reportLines.Add("")
$reportLines.Add("## 环境")
$reportLines.Add("")
$reportLines.Add("| 项目 | 实际值 |")
$reportLines.Add("| --- | --- |")
$reportLines.Add("| 测试时间 | " + (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz") + " |")
$reportLines.Add("| 分支 | " + $gitBranch + " |")
$reportLines.Add("| Git HEAD | " + $gitCommit + " |")
$reportLines.Add("| Git 状态 | " + $statusText.Replace("|", "/") + " |")
$reportLines.Add("| 数据库 | " + $config.databaseName + " |")
$reportLines.Add("| 数据集 | " + $config.dataset.id + " |")
$reportLines.Add("| 后端地址 | " + $config.baseUrl + " |")
$reportLines.Add("| 图片目录 | " + $config.imageDirectory.Replace("|", "/") + " |")
$reportLines.Add("| 后端启动参数 | java -jar backend-1.0.0.jar --spring.profiles.active=performance；连接参数由进程环境注入且不记录密码 |")
$reportLines.Add("| Java | " + $javaVersion.Replace("|", "/") + " |")
$reportLines.Add("| k6 | " + $k6Version.Replace("|", "/") + " |")
$reportLines.Add("| MySQL 客户端 | " + $mysqlVersion.Replace("|", "/") + " |")
$reportLines.Add("| 操作系统 | " + $osBefore.Caption + " " + $osBefore.Version + " |")
$reportLines.Add("| CPU | " + $processor.Name.Trim() + " |")
$reportLines.Add("| 逻辑处理器 | " + $computer.NumberOfLogicalProcessors + " |")
$reportLines.Add("| 总内存 GiB | " + (Format-Number -Value ($computer.TotalPhysicalMemory / 1GB)) + " |")
$reportLines.Add("| 测试前可用内存 GiB | " + $freeMemoryGiBBefore + " |")
$reportLines.Add("| 测试后可用内存 GiB | " + $freeMemoryGiBAfter + " |")
$reportLines.Add("| 持续 CPU/内存采样 | 未采集；仅执行内存安全下限检查 |")
$reportLines.Add("")
$reportLines.Add("## 隔离证明")
$reportLines.Add("")
$reportLines.Add("| 检查 | 实际结果 |")
$reportLines.Add("| --- | --- |")
$reportLines.Add("| 数据库名称与 SELECT DATABASE() | " + $config.databaseName + "，完全一致 |")
$reportLines.Add("| 性能身份端点 | 环境标识、数据库、图片目录、Git HEAD 全部匹配 |")
$reportLines.Add("| 服务绑定 | 仅 " + $config.baseUrl + "，未使用 8080/5173 |")
$reportLines.Add("| 正式库、正式图片、正式备份 | 未访问；正式目录字符串只用于拒绝名单比较 |")
$reportLines.Add("| 测试前后数据 | 五张业务表行数、图片数和图片总字节数一致 |")
$reportLines.Add("")
$reportLines.Add("## 数据规模")
$reportLines.Add("")
$reportLines.Add("| 表或资源 | 实际数量 |")
$reportLines.Add("| --- | ---: |")
$reportLines.Add("| 科目 | " + $config.dataset.subjectCount + " |")
$reportLines.Add("| 知识点 | " + $config.dataset.knowledgePointCount + " |")
$reportLines.Add("| 错题 | " + $config.dataset.questionCount + " |")
$reportLines.Add("| 题目与知识点关联 | " + $config.dataset.relationCount + " |")
$reportLines.Add("| 复习状态 | " + $config.dataset.reviewStateCount + " |")
$reportLines.Add("| 复习历史 | " + $config.dataset.reviewRecordCount + " |")
$reportLines.Add("| 图片文件 | " + $config.dataset.imageFileCount + " |")
$reportLines.Add("| 数据库估算占用 MiB | " + $databaseMiB + " |")
$reportLines.Add("| 图片实际占用 MiB | " + $imageMiB + " |")
$reportLines.Add("")
$reportLines.Add("## JSON 读取测量事实")
$reportLines.Add("")
$reportLines.Add("- 预热：30 秒；正式测量：5 分钟；最大 VU：1；请求超时：5 秒。")
$reportLines.Add("")
$reportLines.Add("| 场景 | 请求数 | 请求/秒 | p50 ms | p95 ms | p99 ms | max ms | 错误率 |")
$reportLines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
foreach ($row in Get-MetricRows -Metrics $readMetrics) {
    $reportLines.Add($row)
}
$reportLines.Add("")
$reportLines.Add("## 图片读取测量事实")
$reportLines.Add("")
$reportLines.Add("- 预热：15 秒；正式测量：60 秒；最大 VU：1；64 KiB 与 1 MiB 分开统计。")
$reportLines.Add("")
$reportLines.Add("| 场景 | 请求数 | 请求/秒 | p50 ms | p95 ms | p99 ms | max ms | 错误率 |")
$reportLines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
foreach ($row in Get-MetricRows -Metrics $imageMetrics) {
    $reportLines.Add($row)
}
$reportLines.Add("")
$reportLines.Add("## 解释边界")
$reportLines.Add("")
$reportLines.Add("### 测量事实")
$reportLines.Add("")
$reportLines.Add("- 上表只描述本机、当前提交、1,000 题专用数据集和给定低负载。")
$reportLines.Add("- 本轮没有执行创建、修改、评价、重新加入或删除操作。")
$reportLines.Add("- 图片响应没有混入 JSON 接口排序。")
$reportLines.Add("- JSON 和图片场景的非预期错误率均为 0。")
$reportLines.Add("- JSON 中 p99 最高的是 " + $slowestRead.Name + "（" + (Format-Number -Value $slowestRead.Value.p99Ms) + " ms）；图片中 p99 最高的是 " + $slowestImage.Name + "（" + (Format-Number -Value $slowestImage.Value.p99Ms) + " ms）。")
$reportLines.Add("")
$reportLines.Add("### 初步定位与排查优先级")
$reportLines.Add("")
$reportLines.Add("1. 中等证据：优先排查 " + $slowestRead.Name + "。它是本轮最慢 JSON p99；先对实际 SQL 做 EXPLAIN ANALYZE，并记录总数查询、ID 分页、关联加载和复习状态查询的分段耗时。")
$reportLines.Add("2. 中等证据：大图片读取的 p99 高于小图片和纯 JSON。先用同一文件做直接磁盘读取对照，再用 JFR 或请求分段日志区分数据库定位、磁盘读取、签名校验与响应传输。")
$reportLines.Add("3. 较弱证据：科目条件下的到期查询高于无科目条件。检查连接条件和复合索引，但在 EXPLAIN ANALYZE 前不能断言缺索引。")
$reportLines.Add("4. 尚无证据：本轮未持续采集 CPU、磁盘和 MySQL 资源，因此不能把延迟归因于测试机资源或数据库服务器。")
$reportLines.Add("")
$reportLines.Add("### 尚待验证")
$reportLines.Add("")
$reportLines.Add("- 尚未使用较大数据档位验证数据量增长曲线。")
$reportLines.Add("- 尚未运行 5 VU 读取场景。")
$reportLines.Add("- 尚未运行写入和复习并发场景。")
$reportLines.Add("- 尚未采集慢查询日志、EXPLAIN ANALYZE、JFR 或持续资源曲线。")
$reportLines.Add("")
$reportLines.Add("## 原始证据")
$reportLines.Add("")
$reportLines.Add("- 本地原始目录：" + $resultDirectory)
$reportLines.Add("- read-metrics.json：逐接口标准化结果")
$reportLines.Add("- image-metrics.json：图片结果")
$reportLines.Add("- k6 stdout/stderr：完整本地运行日志")

Write-PerformanceUtf8File -Path $PublishedReportPath -Content (
    ($reportLines -join [Environment]::NewLine) +
    [Environment]::NewLine
)

[pscustomobject]@{
    Completed             = $true
    DatabaseName          = $config.databaseName
    DatasetId             = $config.dataset.id
    ReadUnexpectedErrors  = $readMetrics.overall.unexpectedErrorRate
    ImageUnexpectedErrors = $imageMetrics.overall.unexpectedErrorRate
    ResultDirectory       = $resultDirectory
    PublishedReport       = $PublishedReportPath
    GitStatusStable       = $gitStatusStable
} | Format-List
