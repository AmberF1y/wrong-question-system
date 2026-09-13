# 安全性能测试

## 1. 目的

本目录用于 Windows 本地、单用户场景的可重复性能基线。目标是比较数据量增长
后的日常体验、识别相对较慢的接口，并验证有限复习并发的业务一致性。它不是
容量证明，也不会自动逐级加压。

默认基线只读取数据。所有写入、复习并发和 5 VU 读取场景均单独存放并要求
显式确认字符串。

## 2. 强制隔离

脚本只接受形如 wrong_question_system_perf_YYYYMMDD 的数据库名，并明确拒绝：

- wrong_question_system；
- wrong_question_system_test；
- 仓库内图片目录；
- D:\WrongQuestionData\question-images；
- D:\WrongQuestionBackups；
- 正式后端端口 8080 和前端端口 5173。

performance Profile 还会在应用启动时执行 SELECT DATABASE()，比较实际库名，
并验证专用图片目录、环境标识和 Git HEAD。k6 在 setup 阶段调用仅该 Profile
提供的身份端点，再次比较同一组值。任意一项不一致都会在业务请求前退出。

数据库密码只从当前 PowerShell 进程的 DB_PASSWORD 读取。MySQL 子进程通过
临时进程环境继承密码，不把密码放进参数、日志、配置或结果。

## 3. 实际接口与风险矩阵

以下来自当前 Controller 实现，不依赖历史接口清单：

| 接口 | 读写 | 本套件用途 | 数据与风险 |
| --- | --- | --- | --- |
| GET /api/health | 只读 | 默认 JSON 基线 | 无业务数据 |
| GET /api/knowledge-points/tree | 只读 | 默认 JSON 基线 | 读取 105 个专用知识点 |
| POST/PUT/DELETE /api/knowledge-points | 写入/删除 | 不运行 | 会改变树结构，排除 |
| GET /api/questions | 只读 | 首页、中间页、末页及科目/状态筛选 | 读取 1,000 道专用题 |
| POST /api/questions | 写入 | 显式写入冒烟脚本 | 固定最多创建 20 道 PERF 题 |
| GET /api/questions/{id} | 只读 | 默认 JSON 基线 | 仅使用 DETAIL 夹具 ID |
| PUT /api/questions/{id} | 写入 | 显式写入冒烟脚本 | 只修改脚本刚创建的题 |
| DELETE /api/questions/{id} | 删除 | 不运行 | 副作用较强，排除 |
| GET /api/questions/{id}/image | 只读 | 独立图片基线 | 90 个 64 KiB、10 个 1 MiB 合成文件 |
| PUT/DELETE /api/questions/{id}/image | 写入/删除 | 不运行 | 会改变文件与题目记录，排除 |
| GET /api/reviews/due/next | 只读 | 无科目/有科目默认基线 | 读取专用到期状态 |
| POST /api/reviews/{id}/evaluations | 写入 | 显式复习并发脚本 | 仅 20 道 REVIEW_CONCURRENT 夹具；推进状态并写历史 |
| POST /api/reviews/{id}/reactivate | 写入 | 不运行 | 会改变调度状态，排除 |

默认基线只运行表中的读取项。写入、并发和删除风险不会因运行默认脚本而被隐式
开启。

## 4. 工具

需要 Java 21、MySQL 客户端、Windows PowerShell 5.1 和 k6。k6 官方安装说明：

https://grafana.com/docs/k6/latest/set-up/install-k6/

Windows Package Manager 命令：

    winget install k6 --source winget

安装后可用以下命令验证：

    k6 version

脚本也会检查 C:\Program Files\k6\k6.exe，避免安装后当前终端尚未刷新 PATH
导致误报。

## 5. 数据档位

当前实现并批准的是 small-1000-v1：

| 项目 | small-1000-v1（已实现） | large-20000-v1（仅设计） |
| --- | ---: | ---: |
| 科目 | 5 | 12 |
| 知识点 | 105 | 612 |
| 错题 | 1,000 | 20,000 |
| 知识点关联 | 2,000 | 40,000 |
| 复习状态 | 1,000 | 20,000 |
| 复习历史 | 500 | 10,000 |
| 图片题 | 100 | 1,000 |
| 数据库占用估算 | 约 1–5 MiB | 约 200–450 MiB |
| 图片占用估算 | 约 15.6 MiB | 约 156 MiB |
| 总占用估算 | 约 17–21 MiB | 约 356–606 MiB |

状态分布为 35% 到期 ACTIVE、45% 未来 ACTIVE、20% MASTERED。图片包含
90 个 64 KiB 文件和 10 个 1 MiB 文件。题目、知识点和图片路径均带明显的
PERF 标识。

large-20000-v1 尚无生成 SQL，也没有实测。它只用于明确下一档的数据构成和磁盘
预算；必须先取得新的数据库名、图片目录、预计占用和运行规模确认，才可实现或
生成，不能从 small 数据库扩容或清空后复用。

## 6. 首次准备

以下示例只适用于已经批准的新库
wrong_question_system_perf_20260912。不要更换成正式库名。

在仓库根目录打开 PowerShell，设置本机 MySQL 密码：

    $env:DB_PASSWORD = "<本机 MySQL 密码>"

先运行不连接数据库的安全自检：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\performance\Test-PerformanceSafety.ps1"

只在新库尚不存在时创建它。脚本发现同名库已存在会拒绝，不会复用、删除或
清空：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\performance\New-PerformanceDatabase.ps1" -DatabaseName "wrong_question_system_perf_20260912" -Confirmation "CREATE_NEW_PERFORMANCE_DATABASE"

启动专用后端。脚本使用 127.0.0.1:18080、performance Profile 和专用图片
目录，不启动前端：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\performance\Start-PerformanceBackend.ps1" -DatabaseName "wrong_question_system_perf_20260912"

首次启动由 Flyway 在空库中创建业务表。随后仅当五张业务表都为空、性能元
数据表不存在且图片目录为空时，才准备 small-1000-v1：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\performance\Initialize-PerformanceDataset.ps1" -DatabaseName "wrong_question_system_perf_20260912" -Confirmation "SEED_EMPTY_PERFORMANCE_DATABASE"

生成的本地配置位于 performance/.local/perf-environment.json，并被 Git
忽略。它不含密码。

## 7. 低负载基线

运行前脚本重新核对数据库元数据、五张表行数、图片数量、后端身份、Git HEAD
和可用内存。JSON 与图片分开执行：

- JSON：1 VU，预热 30 秒，正式测量 5 分钟；
- 图片：1 VU，预热 15 秒，正式测量 60 秒；
- 单请求超时：5 秒；
- 意外错误率达到 1% 后停止；
- 可用内存低于 1 GiB、后端退出或超过硬时间上限时停止。

运行命令：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\performance\Invoke-PerformanceBaseline.ps1" -Confirmation "RUN_READ_ONLY_LOW_LOAD_BASELINE"

结果包括每个接口的请求数、每秒请求数、p50、p95、p99、最大值和错误率。
原始日志写入被忽略的 performance/results，汇总报告默认写入
performance/reports/baseline-2026-09-12-small.md。

同一个未被写场景修改的数据集可以重复执行读取基线。脚本会比较准备时和运行时
行数，发现变化即退出。

## 8. 停止专用后端

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\performance\Stop-PerformanceBackend.ps1"

停止脚本只接受运行清单中记录的 Java PID，并再次检查其命令行包含 performance
Profile 和本项目 jar；不会按进程名批量停止服务。

## 9. 非默认场景

以下场景本轮不自动运行。

5 VU、3 分钟有限读取并发：

    $env:PERF_CONFIG = (Resolve-Path ".\performance\.local\perf-environment.json").Path
    $env:PERF_RESULT_PATH = (Join-Path $PWD "performance\results\concurrent-reads.json")
    $env:PERF_ENABLE_CONCURRENT_READS = "I_UNDERSTAND_5_VUS"
    k6 run ".\performance\k6\concurrent-reads.js"

固定 20 次题目创建与修改：

    $env:PERF_ENABLE_WRITES = "I_UNDERSTAND_DEDICATED_PERF_WRITES"
    k6 run ".\performance\k6\write-smoke.js"

20 道专用到期题、每题两个并发评价请求：

    $env:PERF_ENABLE_REVIEW_CONCURRENCY = "I_UNDERSTAND_2_REQUEST_REVIEW_MUTATION"
    k6 run ".\performance\k6\review-concurrency.js"

评价场景将 200 视为成功，将 REVIEW_CONCURRENT_MODIFICATION 或
REVIEW_NOT_DUE 的 409 视为预期业务冲突；其他响应才计为意外错误。完成后执行：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\performance\Verify-ReviewConcurrency.ps1"

该检查要求 20 道题各只推进一次，且各新增一条评价历史。写场景会永久修改
专用数据集，因此必须最后运行；再次建立干净基线应申请一个新的专用数据库，
不得让脚本自动清空现有库。

## 10. 已排除操作

默认套件不执行知识点修改或删除、错题删除、图片上传替换、图片删除和重新加入。
这些操作的副作用较强，当前问题不需要用它们建立日常读取基线。

## 11. 初步排查路线

下面只是基于代码结构的候选项，不是性能结论：

1. 错题分页执行总数、ID 分页、知识点 fetch 和复习状态加载，应先比较首页、
   中间页和末页，再用 EXPLAIN ANALYZE 验证。
2. 到期复习执行数量查询和首题查询，应分别检查队列复合索引与科目连接条件。
3. 知识树一次读取全部节点并在 JVM 中构树，需结合节点数量曲线验证。
4. 图片读取包含题目定位、磁盘读取、路径与签名验证，必须和 JSON 分开分析。
5. 创建、修改包含多次 flush、refresh 和关联替换；评价包含乐观锁状态更新和
   历史插入，只有写场景实际运行后才能判断。

performance/sql/diagnostics.sql 提供只读 EXPLAIN ANALYZE 示例。运行前仍必须
人工确认当前连接的是专用性能库。
