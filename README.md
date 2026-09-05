# Wrong Question System

面向个人学习场景的错题整理与固定规则滚动复习全栈应用，同时作为 Java / Vue 工程实践项目。

## v1.0.0 第一版

F-001～F-008 已全部完成并合并到 `main`。当前 `release/v1.0.0` 正在执行发布
收口，版本号、产品边界、本地长期使用和备份流程均以 `v1.0.0` 为目标。标签与
GitHub Release 尚未创建前，本仓库内容仍属于发布候选状态。

- 健康检查；
- 树形知识点创建、修改、移动和严格删除；
- 错题创建、详情、分页、科目筛选、修改和删除；
- 固定规则滚动复习队列；
- 四级掌握程度评价；
- 连续两次“熟练”后进入已掌握状态；
- 已掌握错题手动重新加入复习；
- 复习当前状态与不可变事件历史；
- 基于 JPA `@Version` 的乐观锁并发保护；
- Flyway 数据库版本迁移；
- 独立 MySQL 测试数据库；
- Vue 桌面端应用布局、导航和后端连接状态；
- 知识点树的查看、创建、修改、同树移动和严格删除；
- 错题录入、分页列表、科目与掌握状态筛选、详情、修改和删除；
- 加载、空数据、404、Validation、409 和连接失败反馈；
- 复习状态摘要展示；
- 每日复习页面、全部科目或单科目动态队列及按需查看答案；
- 四级评价、服务器结果展示、手动进入下一题和空队列反馈；
- 评价响应不确定时禁止自动重发，并通过服务器状态同步恢复；
- 已掌握错题详情中的重新加入确认和今日复习入口。
- 单题单张可选图片的上传、读取、替换、单独移除和随题删除清理；
- PNG、JPEG、WebP、GIF 内容签名校验与 20 MiB 上限；
- 创建页和编辑页的图片选择、本地预览、替换与移除；
- 详情页和每日复习页的响应式图片、大图查看和局部失败重试；
- 服务端生成相对存储键，图片保存在可配置的本地文件系统目录。

当前不包含 OCR、Dashboard、趋势统计、薄弱知识点、复习历史页面、自适应
复习算法、用户系统、对象存储、多图片和部署。

`v1.0.0` 的第一版完成标准是“录入 → 保存 → 调度 → 复习 → 评价 → 再调度”
核心闭环可以在 Windows 桌面浏览器中长期自用。Dashboard、学习反馈和部署
进入后续版本候选，不阻断第一版发布。

## 技术栈

### 后端

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring Data JPA / Hibernate
- MySQL
- Flyway
- Jakarta Bean Validation
- JUnit 5 / Mockito / MockMvc

### 前端

- Vue 3 + TypeScript
- Vite
- Element Plus
- Vue Router
- Pinia
- Axios
- Vitest / Vue Test Utils / jsdom

## 数据库初始化

日常开发库与测试库只由辅助脚本创建空数据库：

```text
sql/create-database.sql
sql/create-test-database.sql
```

业务表统一由应用启动时的 Flyway 迁移创建：

```text
backend/src/main/resources/db/migration/V1__initial_schema.sql
backend/src/main/resources/db/migration/V2__add_rolling_review.sql
```

Hibernate 使用 `ddl-auto: validate` 校验 Entity 与迁移后的结构是否一致，不负责建表。

## 本地配置

数据库密码不写入仓库，通过环境变量提供：

```powershell
$env:DB_PASSWORD = "<你的本地 MySQL 密码>"
```

默认业务时区为北京时间，可通过环境变量覆盖：

```powershell
$env:APP_REVIEW_ZONE_ID = "Asia/Shanghai"
```

题目图片默认保存在后端工作目录下的 `./data/question-images`。部署或需要固定
外部目录时通过环境变量覆盖，例如：

```powershell
$env:APP_QUESTION_IMAGE_DIRECTORY = "D:\WrongQuestionData\question-images"
```

数据库只保存 `questions/{questionId}/{uuid}.{extension}` 形式的相对路径，
不得把图片目录或真实图片提交到 Git。

## 本地运行

长期使用建议从仓库根目录执行启动脚本。先在当前 PowerShell 会话中提供数据库
密码，再运行：

```powershell
Set-Location "D:\Projects\wrong-question-system"
$env:DB_PASSWORD = "<你的本地 MySQL 密码>"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-local.ps1"
```

脚本默认把正式题目图片保存到 `D:\WrongQuestionData\question-images`，并分别
打开后端和前端窗口。完整的启动、停止、备份、恢复和故障处理流程见
`docs/local-usage.md`。

也可以按下面的命令分别手动启动。

先启动 MySQL 和后端：

```powershell
cd D:\Projects\wrong-question-system\backend
$env:DB_PASSWORD = "<你的本地 MySQL 密码>"
$env:APP_QUESTION_IMAGE_DIRECTORY = "D:\WrongQuestionData\question-images"
.\mvnw.cmd spring-boot:run
```

再打开另一个 PowerShell 窗口启动前端：

```powershell
cd D:\Projects\wrong-question-system\frontend
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

浏览器访问 `http://127.0.0.1:5173/`。Vite 将 `/api` 请求代理到
`http://localhost:8080`。

## 运行测试

### 后端

先执行 `sql/create-test-database.sql` 创建空的 `wrong_question_system_test`，再运行：

```powershell
cd D:\Projects\wrong-question-system\backend
$env:DB_PASSWORD = "<你的本地 MySQL 密码>"
.\mvnw.cmd clean test
```

测试配置只连接 `wrong_question_system_test`，不应连接日常使用的 `wrong_question_system`。

### 前端

```powershell
cd D:\Projects\wrong-question-system\frontend
npm.cmd run type-check
npm.cmd run test:unit -- --run
npm.cmd run build
```

F-008 功能分支和合并后的 `main` 最终验证结果均为后端 146 个测试通过、
前端 16 个测试文件共 81 个测试通过，类型检查和生产构建通过。

Windows PowerShell 因执行策略阻止 `npm.ps1` 时，使用 `npm.cmd` 即可，
不需要修改机器级执行策略。

## 本地备份

先使用 `Ctrl+C` 停止后端，再从仓库根目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\backup-local.ps1"
```

脚本默认同时备份 `wrong_question_system` 数据库和
`D:\WrongQuestionData\question-images`，结果写入 `D:\WrongQuestionBackups`。
数据库密码由 `mysqldump` 交互式读取，不写入脚本参数、仓库或日志。

## 已有开发库首次接入 Flyway

不要直接在已有非空开发库上启动新版本。首次接管必须先备份并核对旧结构，然后仅本次启动显式设置：

```powershell
$env:FLYWAY_BASELINE_ON_MIGRATE = "true"
```

Flyway 会把现有 F-004 结构登记为 V1，再执行 V2。迁移成功并核对数据后应立即移除该环境变量。默认配置保持 `baseline-on-migrate=false`。

详细步骤和本次实际迁移记录见
`docs/plans/completed/F-005-rolling-review.md`。当前开发库已完成一次性 baseline
和 V2 迁移；其他已有数据库仍必须各自执行上述接管流程。

## 复习 API

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/reviews/due/next` | 获取当前下一道到期题和到期总数 |
| POST | `/api/reviews/{questionId}/evaluations` | 提交四级复习评价 |
| POST | `/api/reviews/{questionId}/reactivate` | 重新加入已掌握错题 |

答案和解析继续通过 `GET /api/questions/{id}` 获取。

## 题目图片 API

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| PUT | `/api/questions/{questionId}/image` | 上传或替换单张题目图片，multipart 字段名为 `file` |
| GET | `/api/questions/{questionId}/image` | 以内联方式读取题目图片 |
| DELETE | `/api/questions/{questionId}/image` | 单独移除图片而保留错题 |

支持 PNG、JPEG、WebP 和 GIF，单文件最大 20 MiB。服务端根据内容签名识别
实际格式，不信任客户端文件名、扩展名或 MIME；SVG 和无法识别的内容会被
拒绝。错题列表不请求图片，详情和每日复习仅在 `imagePath` 非空时读取。

## 文档

- `docs/PRODUCT.md`：产品范围；
- `docs/api-design.md`：HTTP 契约；
- `docs/database-design.md`：表结构和数据约束；
- `docs/project-status.md`：真实项目状态；
- `docs/local-usage.md`：Windows 本地长期使用、备份和恢复；
- `docs/releases/`：版本发布说明；
- `docs/decisions/`：架构决策记录；
- `docs/plans/`：Feature 计划与实施记录。
