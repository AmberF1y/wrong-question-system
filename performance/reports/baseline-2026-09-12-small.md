# 低负载性能基线报告

## 结论状态

- 状态：已运行
- 性质：单机、单用户、低负载基线，不代表容量上限
- JSON 与图片场景分开测量
- 测试期间 Git 状态保持不变：True
- 测试前后专用数据库行数和图片文件保持不变：True

## 环境

| 项目 | 实际值 |
| --- | --- |
| 测试时间 | 2026-09-12 16:28:58 +08:00 |
| 分支 | codex/performance-baseline |
| Git HEAD | 72eaa0eafba3f760bfc8f05519250715cc20bed8 |
| Git 状态 |  M .gitignore;  M README.md; ?? backend/src/main/java/com/wrongquestion/backend/performance/; ?? backend/src/main/resources/application-performance.yaml; ?? backend/src/test/java/com/wrongquestion/backend/performance/; ?? pelican-bicycle.svg; ?? performance/; ?? scripts/performance/ |
| 数据库 | wrong_question_system_perf_20260912 |
| 数据集 | small-1000-v1 |
| 后端地址 | http://127.0.0.1:18080 |
| 图片目录 | D:\WrongQuestionPerfData\wrong_question_system_perf_20260912\question-images |
| 后端启动参数 | java -jar backend-1.0.0.jar --spring.profiles.active=performance；连接参数由进程环境注入且不记录密码 |
| Java | openjdk version "21.0.12" 2026-07-21 LTS |
| k6 | k6.exe v2.2.0 (commit/00a9a1b7f5, go1.26.5, windows/amd64) |
| MySQL 客户端 | D:\mysql-9.6.0-winx64\bin\mysql.exe  Ver 9.6.0 for Win64 on x86_64 (MySQL Community Server - GPL) |
| 操作系统 | Microsoft Windows 11 专业版 10.0.26200 |
| CPU | 13th Gen Intel(R) Core(TM) i9-13900HX |
| 逻辑处理器 | 32 |
| 总内存 GiB | 23.81 |
| 测试前可用内存 GiB | 4.26 |
| 测试后可用内存 GiB | 4.15 |
| 持续 CPU/内存采样 | 未采集；仅执行内存安全下限检查 |

## 隔离证明

| 检查 | 实际结果 |
| --- | --- |
| 数据库名称与 SELECT DATABASE() | wrong_question_system_perf_20260912，完全一致 |
| 性能身份端点 | 环境标识、数据库、图片目录、Git HEAD 全部匹配 |
| 服务绑定 | 仅 http://127.0.0.1:18080，未使用 8080/5173 |
| 正式库、正式图片、正式备份 | 未访问；正式目录字符串只用于拒绝名单比较 |
| 测试前后数据 | 五张业务表行数、图片数和图片总字节数一致 |

## 数据规模

| 表或资源 | 实际数量 |
| --- | ---: |
| 科目 | 5 |
| 知识点 | 105 |
| 错题 | 1000 |
| 题目与知识点关联 | 2000 |
| 复习状态 | 1000 |
| 复习历史 | 500 |
| 图片文件 | 100 |
| 数据库估算占用 MiB | 0.73 |
| 图片实际占用 MiB | 15.62 |

## JSON 读取测量事实

- 预热：30 秒；正式测量：5 分钟；最大 VU：1；请求超时：5 秒。

| 场景 | 请求数 | 请求/秒 | p50 ms | p95 ms | p99 ms | max ms | 错误率 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| health | 237 | 0.71 | 0.61 | 0.96 | 1.21 | 1.25 | 0.000% |
| knowledge_tree | 237 | 0.71 | 3.08 | 3.57 | 4.27 | 5.85 | 0.000% |
| questions_page_first | 237 | 0.71 | 6.18 | 6.82 | 8.27 | 13.31 | 0.000% |
| questions_page_middle | 237 | 0.71 | 6.45 | 7.11 | 8.33 | 9.52 | 0.000% |
| questions_page_last | 237 | 0.71 | 6.45 | 7.31 | 8.64 | 10.29 | 0.000% |
| questions_subject | 237 | 0.71 | 6.05 | 6.77 | 7.94 | 8.47 | 0.000% |
| questions_active | 237 | 0.71 | 8.40 | 9.09 | 9.39 | 10.03 | 0.000% |
| questions_mastered | 237 | 0.71 | 7.18 | 8.03 | 9.27 | 11.46 | 0.000% |
| questions_subject_active | 237 | 0.71 | 6.81 | 7.45 | 7.90 | 9.12 | 0.000% |
| question_detail | 237 | 0.71 | 3.46 | 3.91 | 4.18 | 5.01 | 0.000% |
| reviews_due | 237 | 0.71 | 3.62 | 4.19 | 4.54 | 5.20 | 0.000% |
| reviews_due_subject | 237 | 0.71 | 5.44 | 6.16 | 6.69 | 6.91 | 0.000% |

## 图片读取测量事实

- 预热：15 秒；正式测量：60 秒；最大 VU：1；64 KiB 与 1 MiB 分开统计。

| 场景 | 请求数 | 请求/秒 | p50 ms | p95 ms | p99 ms | max ms | 错误率 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| image_small_64k | 59 | 0.74 | 5.21 | 6.02 | 6.51 | 6.78 | 0.000% |
| image_large_1m | 59 | 0.74 | 13.01 | 14.65 | 19.58 | 24.29 | 0.000% |

## 解释边界

### 测量事实

- 上表只描述本机、当前提交、1,000 题专用数据集和给定低负载。
- 本轮没有执行创建、修改、评价、重新加入或删除操作。
- 图片响应没有混入 JSON 接口排序。
- JSON 和图片场景的非预期错误率均为 0。
- JSON 中 p99 最高的是 questions_active（9.39 ms）；图片中 p99 最高的是 image_large_1m（19.58 ms）。

### 初步定位与排查优先级

1. 中等证据：优先排查 questions_active。它是本轮最慢 JSON p99；先对实际 SQL 做 EXPLAIN ANALYZE，并记录总数查询、ID 分页、关联加载和复习状态查询的分段耗时。
2. 中等证据：大图片读取的 p99 高于小图片和纯 JSON。先用同一文件做直接磁盘读取对照，再用 JFR 或请求分段日志区分数据库定位、磁盘读取、签名校验与响应传输。
3. 较弱证据：科目条件下的到期查询高于无科目条件。检查连接条件和复合索引，但在 EXPLAIN ANALYZE 前不能断言缺索引。
4. 尚无证据：本轮未持续采集 CPU、磁盘和 MySQL 资源，因此不能把延迟归因于测试机资源或数据库服务器。

### 尚待验证

- 尚未使用较大数据档位验证数据量增长曲线。
- 尚未运行 5 VU 读取场景。
- 尚未运行写入和复习并发场景。
- 尚未采集慢查询日志、EXPLAIN ANALYZE、JFR 或持续资源曲线。

## 原始证据

- 本地原始目录：D:\Projects\wrong-question-system\performance\results\baseline-20260912-162151
- read-metrics.json：逐接口标准化结果
- image-metrics.json：图片结果
- k6 stdout/stderr：完整本地运行日志
