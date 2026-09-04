# Wrong Question System

面向个人学习场景的错题整理与固定规则滚动复习全栈应用，同时作为 Java / Vue 工程实践项目。

## 当前代码能力

当前 `main` 已包含 F-006。Vue 前端通过 Pull Request #6 以 Merge Commit
`7cd5d9b` 合并；合并后的 `main` 已再次通过前端类型检查、24 个前端测试、
生产构建和后端 113 个测试。真实浏览器验收与开发库临时数据清理也已完成。

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
- 复习状态摘要展示。

当前不包含每日复习交互、四级评价前端、图片上传、OCR、Dashboard、
自适应复习算法、用户系统和复习历史查询 API。

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

## 本地运行

先启动 MySQL 和后端：

```powershell
cd D:\Projects\wrong-question-system\backend
$env:DB_PASSWORD = "<你的本地 MySQL 密码>"
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

Windows PowerShell 因执行策略阻止 `npm.ps1` 时，使用 `npm.cmd` 即可，
不需要修改机器级执行策略。

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

## 文档

- `docs/PRODUCT.md`：产品范围；
- `docs/api-design.md`：HTTP 契约；
- `docs/database-design.md`：表结构和数据约束；
- `docs/project-status.md`：真实项目状态；
- `docs/decisions/`：架构决策记录；
- `docs/plans/`：Feature 计划与实施记录。
