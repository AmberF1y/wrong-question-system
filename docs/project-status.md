# 错题整理 + 滚动复习系统：项目状态

## 1. 文档用途

本文件只记录项目当前真实状态。已经完成的内容、正在进行的内容和尚未完成的内容必须明确区分。

更新时间：2026-09-04

---

## 2. 当前 Feature

| Feature | 状态 | 内容 |
| --- | --- | --- |
| F-001 | Completed | Spring Boot 项目初始化与健康检查 |
| F-002 | Completed | 数据库设计、JPA Entity、Repository 与真实 MySQL 集成测试 |
| F-003 | Completed | 知识点管理业务层、REST API、异常响应与测试 |
| F-004 | Completed | 错题基础管理业务层、REST API、异常响应与测试 |
| F-005 | Completed | 固定规则滚动复习、Flyway 迁移、113 个测试、旧库迁移与手工 API 验收均已完成 |
| F-006 | In Progress | 前端基础、知识点管理页面与错题管理页面；正式计划已确认，尚未编写前端实现代码 |

当前开发分支：

```text
feature/F-006-frontend-question-management
```

F-005 从 `main` 的 `bba3122` 开始，功能分支提交为：

```text
d5187f3 docs: add F-005 rolling review plan
9687337 feat: implement F-005 rolling review
a90a653 docs: record F-005 verification status
```

Pull Request #5 已通过 Merge Commit 合并到 `main`，合并提交为
`4378bda`。合并后的 `main` 已再次通过全部 113 个测试。

---

## 3. 项目目标与原则

项目是面向个人学习场景的错题整理与滚动复习系统，同时作为 Java 后端求职展示项目。

开发原则：

- MVP 优先；
- 先打通真实核心链路；
- 不为展示技术栈引入无实际需求的组件；
- 代码、数据库和文档保持一致。

---

## 4. 当前技术栈

### 后端

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring Data JPA
- Hibernate
- Maven Wrapper
- Jakarta Bean Validation

### 数据库

- MySQL 9.6
- MySQL Connector/J
- Flyway
- Hibernate `ddl-auto: validate`
- 独立测试数据库 `wrong_question_system_test`

### 当前明确不引入

- MyBatis / MyBatis-Plus
- H2
- Testcontainers
- MapStruct
- Lombok
- Redis、消息队列、微服务等当前无实际需求的组件

ADR-001 记录初始技术栈与分层；ADR-002 记录 F-005 引入 Flyway、
一次性 baseline 和独立 MySQL 测试库的决策。

---

## 5. 当前数据库结构

F-005 迁移后的目标结构为五张表：

- `question`
- `knowledge_point`
- `question_knowledge_point`
- `question_review_state`
- `review_record`

主要关系：

- `knowledge_point.parent_id` 自关联形成知识树；
- Question 与 KnowledgePoint 通过中间表形成多对多关系；
- `question.subject` 当前以字符串保存；
- `created_time`、`updated_time` 由 MySQL 维护；
- Entity 通过 `insertable = false`、`updatable = false` 读取时间字段。

F-005 将原三表结构转为 Flyway V1，通过 V2 创建两张复习表、索引、约束并回填旧错题。该目标结构已在用户本地空测试库和备份后的旧开发库上完成验收。

---

## 6. 已完成 Feature

### F-001

- Spring Boot 基础工程；
- Java 21 与 Maven 配置；
- MySQL 数据源配置；
- `GET /api/health`；
- 应用上下文与健康检查测试。

### F-002

- `sql/init.sql`；
- Question Entity；
- KnowledgePoint Entity；
- KnowledgePoint 父节点自关联；
- Question-KnowledgePoint 单向多对多映射；
- QuestionRepository；
- KnowledgePointRepository；
- Repository 真实 MySQL 集成测试；
- Hibernate Schema Validate。

---

## 7. F-003 已实现内容

F-003 提供四个知识点接口：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/knowledge-points/tree` | 查询完整知识树 |
| POST | `/api/knowledge-points` | 创建根节点或子节点 |
| PUT | `/api/knowledge-points/{id}` | 改名或同树移动 |
| DELETE | `/api/knowledge-points/{id}` | 严格删除 |

已实现：

- 请求、普通响应、树节点响应和删除消息 DTO；
- Jakarta Bean Validation 参数校验；
- KnowledgePointService 业务规则与事务；
- KnowledgePointController REST API；
- 统一 ApiErrorResponse 和 GlobalExceptionHandler；
- KnowledgePointRepository 与 QuestionRepository 所需查询；
- O(n) 完整知识树组装；
- Service 单元测试；
- Controller 真实 MySQL 集成测试；
- API、数据库规则和 Feature Plan 文档同步。

---

## 8. F-003 业务规则

- 名称去除首尾空格；
- 根节点最多 50 个字符；
- 普通节点最多 100 个字符；
- 根节点之间、同一父节点下不能重名；
- 普通节点只能在同一根节点内部移动；
- 禁止自引用、循环引用和跨根节点移动；
- 根节点不能变成子节点；
- 普通节点不能升级为根节点；
- 根节点改名同步相关 `Question.subject`；
- 只有无子节点且无错题引用的节点才能删除；
- 删除成功返回 `200 OK` 和 JSON 消息；
- 完整树和同级节点按 ID 升序排列。

---

## 9. F-003 代码分层

```text
HTTP / JSON
↓
KnowledgePointController
↓
请求 DTO + Validation
↓
KnowledgePointService + Transaction
↓
KnowledgePointRepository / QuestionRepository
↓
KnowledgePoint / Question Entity
↓
MySQL
```

职责边界：

- Controller 处理 HTTP，不直接调用 Repository；
- DTO 定义 API 合同，不作为数据库 Entity；
- Validation 处理通用输入格式；
- Service 处理知识树业务规则；
- Transaction 保证写操作原子性；
- Repository 负责 JPA 数据访问；
- Exception Handler 统一转换错误响应。

---

## 10. 自动化测试结果

2026-09-02 先在 `feature/F-004-question-management` 上完成 F-004 全量测试，再在合并提交 `ce54aad` 对应的 `main` 上重新执行相同测试。两次结果一致：

- Java 21.0.12；
- MySQL 9.6；
- 数据库 `wrong_question_system`；
- Maven 命令：`.\mvnw.cmd test`；
- Tests run：65；
- Failures：0；
- Errors：0；
- Skipped：0；
- 结果：`BUILD SUCCESS`。

测试构成：

| 测试类 | 数量 |
| --- | ---: |
| BackendApplicationTests | 1 |
| KnowledgePointControllerTest | 11 |
| KnowledgePointRepositoryTest | 1 |
| KnowledgePointServiceTest | 21 |
| QuestionControllerTest | 8 |
| QuestionRepositoryTest | 1 |
| QuestionServiceTest | 21 |
| HealthControllerTest | 1 |
| 合计 | 65 |

Controller 测试连接真实 MySQL；Service 测试使用 Mockito 单元测试隔离业务分支。原有 36 个测试全部继续通过，新增 29 个测试全部通过。

---

## 11. 手工 API 验证结果

### 11.1 F-003

2026-09-01 已完成：

- 健康检查和完整知识树查询；
- 创建两个临时根节点、两个分支节点和一个叶子节点；
- 同一知识树内改名和移动，节点 ID 保持不变；
- 跨根节点移动返回 409，数据库状态不变；
- 根节点名称去除首尾空格后重名返回 409；
- 删除有子节点的知识点返回 409；
- 删除被错题引用的知识点返回 409；
- 根节点改名后相关错题 `subject` 同步更新；
- 无引用叶子及其上级节点按顺序删除，均返回 `200 OK` 和 `知识点删除成功`；
- 临时错题、关联记录和知识点已全部清理。

Windows PowerShell 5.1 对 JSON UTF-8 的显示出现乱码和单元素数组包装，但浏览器原始响应与 Navicat 数据均正常，因此不是后端或数据库编码问题。

### 11.2 F-004

2026-09-02 已完成：

- 健康检查返回 `status=ok`；
- 创建两棵临时知识树，并创建三道真实错题；
- 创建接口返回 201，五个文本字段去除首尾空白；
- `subject` 根据所选知识点共同根节点自动生成；
- 允许同时直接选择父节点和子节点；
- 详情接口返回完整文本、知识点和数据库时间；
- 列表固定按 ID 倒序，摘要不返回答案、解析和图片路径；
- `subject` 精确筛选正确；
- 超范围页返回 200、空列表和正确总数；
- PUT 完整替换文本、科目和知识点关联；
- 跨科目修改后 ID、创建时间和 `imagePath` 保持不变，更新时间变化；
- 重复知识点和跨科目知识点返回 400，失败请求不产生数据；
- 非法更新返回 400，原文本、科目和知识点关联全部保持不变；
- 负数页码、超大分页、空文本、损坏 JSON、不存在的错题和知识点均返回约定错误码；
- 删除仍被错题引用的知识点返回 409；
- 删除错题返回 200，之后查询返回 404，其他错题不受影响；
- 删除三道临时错题后，五个临时知识点仍可分别显式删除，证明错题删除不级联删除知识点；
- Navicat 最终核对 `question`、`question_knowledge_point`、`knowledge_point` 中本次临时 ID 数量均为 0。

手工测试曾因 Windows PowerShell 变量边界和 GET 空请求体辅助函数写法出现本地命令错误；修正命令后请求与后端行为均符合约定，不属于 F-004 缺陷。

---

## 12. F-003 完成状态

- 实现、自动化测试和手工验证全部完成；
- 实现提交 `f13a8fa` 已推送；
- Feature Plan 已移入 `docs/plans/completed/`；
- F-003 未遗留待实现业务范围；
- F-003 未新增 ADR，也未修改数据库表结构。
- Pull Request #3 已使用 Merge Commit 合并到 `main`；
- 合并后的 `main` 已通过全部 36 个测试；
- F-003 本地和远程功能分支已删除。

---

## 13. F-004 完成范围

F-004 提供五个错题接口：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| POST | `/api/questions` | 创建错题 |
| GET | `/api/questions/{id}` | 查询错题详情 |
| GET | `/api/questions` | 分页查询与科目筛选 |
| PUT | `/api/questions/{id}` | 完整修改错题 |
| DELETE | `/api/questions/{id}` | 删除错题 |

F-004 不包含图片上传、OCR、复习、复杂搜索或前端页面。

---

## 14. F-004 业务规则

- 请求不提交 `subject`，由所选知识点共同根节点名称生成；
- 每道错题至少关联一个知识点；
- 知识点必须存在、不能重复且必须属于同一根节点；
- 允许直接选择根节点、普通节点或同时选择父子节点；
- 只保存直接选择的知识点；
- 五个核心文本字段必填，去除首尾空白并限制长度；
- PUT 整体替换全部可编辑字段与知识点集合；
- 修改允许切换到另一科目，并保留 `id`、`createdTime` 和 `imagePath`；
- 列表从第 0 页开始，默认每页 20 条、最多 100 条，固定按 ID 倒序；
- 列表支持 `subject` 精确筛选，不提供知识点筛选；
- 删除采用真实删除，清理关联表但不删除知识点。

---

## 15. F-004 完成状态

合并到 `main` 的 F-004 代码包含：

- Question 请求、详情、摘要和分页 DTO；
- QuestionController；
- QuestionService 与读写事务；
- QuestionRepository 分页 ID、批量关联加载和更新时间查询；
- Question 业务更新与知识点整体替换方法；
- Question 业务异常及统一异常响应扩展；
- 公共 MessageResponse；
- `spring.jpa.open-in-view=false`；
- QuestionService Mockito 单元测试；
- QuestionController 真实 MySQL 集成测试。

上述代码由实现提交 `d220806` 引入，并通过 Pull Request #4 合并到 `main`。功能分支和合并后的 `main` 均已在用户本地 Java 21.0.12 + MySQL 9.6 环境完成编译与全量测试。

---

## 16. F-004 验证与 Git 状态

已完成：

- 实现文件已应用到 F-004 功能分支；
- `./mvnw.cmd test` 全量 65 个测试通过；
- 创建、详情、分页、修改、删除成功链路通过；
- 参数校验、资源不存在、重复知识点、跨科目知识点和事务回滚等失败链路通过；
- 错题删除、关联清理和知识点保留规则通过；
- Navicat 最终数据核对通过；
- 手工临时数据全部清理；
- API、数据库设计和项目状态文档已同步；
- Feature Plan 已归档到 `docs/plans/completed/`；
- 计划提交 `bb22c98` 和实现提交 `d220806` 已推送；
- Pull Request #4 已使用 Merge Commit 合并，合并提交为 `ce54aad`；
- 合并后的 `main` 已再次通过全部 65 个测试；
- `main` 工作区 clean 并与 `origin/main` 同步；
- 本地和远程 `feature/F-004-question-management` 分支均已删除。

---

## 17. 当前客观状态

F-001、F-002、F-003、F-004 均已进入 `main`。F-004 的实现、65 个自动化测试、手工 API 验证、数据库清理、Pull Request 合并和功能分支清理均已完成。

F-005 已完成。实现已在用户本地 Java 21.0.12 与 MySQL 9.6 环境完成
自动化测试、已有开发库迁移和手工 API 验收，并通过 Pull Request #5
以 Merge Commit `4378bda` 合并到 `main`。合并后的 `main` 已再次通过
全部 113 个测试。

---

## 18. F-005 完成状态

已写入并通过本地验收：

- Flyway V1/V2 与独立测试库配置；
- `question_review_state` 当前状态和 `review_record` 历史；
- 配置时区与注入式 `Clock`；
- 四级固定间隔调度规则；
- 连续两次熟练进入 `MASTERED`；
- 到期/逾期动态队列和科目筛选；
- 评价与重新加入 API；
- Question 响应复习摘要和状态分页筛选；
- `@Version` 乐观锁；
- 单元、Repository、Controller、生命周期和并发测试代码；
- README、产品、API、数据库和 ADR 文档更新。

本地自动化测试实际结果：

- 命令：`.\mvnw.cmd clean test`；
- Java：21.0.12；
- MySQL：9.6，测试库为 `wrong_question_system_test`；
- Tests run：113；
- Failures：0；
- Errors：0；
- Skipped：0；
- 结果：`BUILD SUCCESS`；
- 原有 65 个测试全部回归通过，F-005 新增 48 个测试全部通过；
- 两个真实 MySQL 独立事务并发测试全部通过。

数据库迁移实际结果：

- 空测试库已自动执行 Flyway V1、V2，并通过 Hibernate Validate；
- 已有开发库迁移前完成无 GTID 备份，SHA-256 为
  `AC325FD38E9FA10669D507C8DE0EE99C282D1B0FD31E20F3B4BE759B743951D0`；
- 已核对旧库三表结构，并创建一条带知识点关联的受控旧题夹具；
- 一次性显式 baseline 成功把旧结构登记为 V1，随后 V2 成功执行；
- 旧题状态已按 `DATE(created_time) + 1 day` 回填；
- Flyway 历史、两张新表、检查约束、外键和目标索引均已核对；
- `FLYWAY_BASELINE_ON_MIGRATE` 已恢复为未设置状态。

手工 API 验收实际结果：

- 16 个步骤全部通过，覆盖新题初始状态、未到期排除、队列顺序、
  `dueCount`、科目筛选、详情查看答案、四级评价间隔、重复提交冲突、
  连续两次熟练、状态筛选、重新加入、修改保留进度和删除级联；
- 清理后数量为 `question=0`、`knowledge_point=4`、`relation=0`、
  `state=0`、`history=0`，没有遗留临时业务数据。

Git 与合并状态：

- 提交前严格核对了 50 个变更路径，没有 F-005 范围外文件；
- `git diff --cached --check` 通过；
- 计划提交：`d5187f3 docs: add F-005 rolling review plan`；
- 实现提交：`9687337 feat: implement F-005 rolling review`；
- 验证记录提交：`a90a653 docs: record F-005 verification status`；
- Pull Request：#5；
- 合并方式：Merge Commit；
- 合并提交：`4378bda`；
- 合并后的 `main` 与 `origin/main` 均指向 `4378bda`，工作区 clean；
- 合并后再次执行 `.\mvnw.cmd clean test`，113 个测试全部通过，
  `BUILD SUCCESS`，总耗时 37.602 秒；
- Feature Plan 已归档到 `docs/plans/completed/`；
- 本地和远程 `feature/F-005-rolling-review` 分支均已清理；
- 最终 `main` 工作区 clean 并与 `origin/main` 同步。

F-005 的范围、规则、实现、测试、迁移、手工验收、文档、合并和仓库清理
均已完成。

---

## 19. F-006 当前状态

F-006 已从 `main@8b8bfd8` 创建功能分支：

```text
feature/F-006-frontend-question-management
```

当前已经完成需求和技术计划，正式计划文件为：

```text
docs/plans/active/F-006-frontend-question-management.md
```

本阶段目标是首次建立 Vue 前端，并在浏览器中提供：

- 应用布局、路由、API 请求和统一错误处理；
- 后端健康状态；
- 知识树展示、创建、改名、同树移动和严格删除；
- 错题创建、分页列表、科目与掌握状态筛选；
- 错题详情、完整修改和删除；
- 复习状态摘要展示；
- 前端类型检查、测试、构建和真实后端联调。

F-006 不实现每日复习交互、四级评价、重新加入复习、Dashboard、图片上传、OCR、部署或数据库变更。每日复习前端留给 F-007。

计划基线已经由用户在真实本地仓库确认：

- 本地 `main`：`8b8bfd8`；
- 远程 `origin/main`：`8b8bfd8`；
- 工作区 clean；
- Node.js：24.18.0；
- npm：11.16.0；
- F-006 分支从正确基线创建。

当前尚未创建 `frontend` 目录，也没有修改 Java、数据库迁移或 API 契约。下一步是在功能分支提交本计划和项目状态，然后初始化 Vue 3 + TypeScript + Vite 工程。
