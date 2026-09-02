# F-004 错题基础管理业务层与 REST API

## 1. Feature 基本信息

| 项目 | 内容 |
| --- | --- |
| Feature ID | F-004 |
| Feature 名称 | 错题基础管理业务层与 REST API |
| 状态 | Implemented and verified（功能实现与验证完成，待 PR 与 main 收尾） |
| 规划基线 | `main`，合并提交 `2106fc3` |
| 计划分支 | `feature/F-004-question-management` |
| 完成日期 | 2026-09-02 |
| 计划文件 | `docs/plans/completed/F-004-question-management.md` |
| 前置 Feature | F-001、F-002、F-003 均已完成并合并到 `main` |

> 本计划生成时尚未创建 Git 分支或编写实现代码；本文件归档时，功能实现、自动化测试和手工验证已经完成，PR 与 main 收尾状态仍由任务清单和项目状态文档单独记录。

---

## 2. 文档用途

本文件是 F-004 的正式 Feature Plan，用于约束本阶段的：

- 目标；
- 范围；
- 业务规则；
- REST API；
- DTO；
- Validation；
- Service；
- Transaction；
- Repository；
- 异常响应；
- 测试范围；
- 文档同步；
- 验收与 Git 完成标准。

未写入本计划的图片上传、OCR、复习、Dashboard、前端等功能，不得在实现过程中临时加入 F-004。

---

## 3. 规划依据

F-004 基于最新 `main` 归档中的以下真实文件重新规划：

- `docs/PRODUCT.md`
- `docs/database-design.md`
- `docs/project-status.md`
- `docs/api-design.md`
- `docs/decisions/ADR-001-initial-tech-stack-and-architecture.md`
- `docs/plans/completed/F-001-project-initialization.md`
- `docs/plans/completed/F-002-database-design.md`
- `docs/plans/completed/F-003-knowledge-point-management.md`
- `sql/init.sql`
- `backend/pom.xml`
- `backend/src/main/resources/application.yaml`
- Question、KnowledgePoint 相关 Entity 与 Repository
- F-003 的 Controller、DTO、Service、Exception Handler 与测试

规划不直接沿用此前聊天对后续 Feature 的假设，以当前文档、真实代码、数据库设计和已完成测试为准。

---

## 4. 当前真实项目基线

### 4.1 已完成内容

当前已经完成：

- Spring Boot 4.1.1 后端基础工程；
- Java 21；
- Maven Wrapper；
- MySQL 9.6 数据源连接；
- Spring Data JPA 与 Hibernate；
- `question`、`knowledge_point`、`question_knowledge_point` 三张表；
- Question 与 KnowledgePoint Entity；
- QuestionRepository 与 KnowledgePointRepository；
- 知识点完整树、创建、修改、同树移动和严格删除 API；
- Validation；
- Service 事务；
- 统一错误响应；
- Service 单元测试；
- Controller 真实 MySQL 集成测试。

2026-09-02 在合并后的 `main` 上执行：

```powershell
.\mvnw.cmd test
```

结果：

```text
Tests run: 36
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

### 4.2 当前缺失的错题链路

数据库层当前已经存在：

```text
QuestionRepository
↓
Question Entity
↓
MySQL
```

但还缺少：

```text
HTTP 请求
↓
QuestionController
↓
请求 DTO + Validation
↓
QuestionService + Transaction
↓
QuestionRepository / KnowledgePointRepository
```

因此当前只能通过测试代码或手工 SQL 保存错题，浏览器和未来前端还不能通过正式 API 管理错题。

---

## 5. 当前文档核对结果

### 5.1 与代码一致的内容

- PRODUCT.md 的 MVP 明确要求错题录入与管理；
- ADR-001 选择 Java、Spring Boot、Spring Data JPA、MySQL 和模块化单体，与真实代码一致；
- `sql/init.sql` 与三张真实表及 Entity 映射一致；
- `api-design.md` 与 F-003 已实现知识点 API 一致；
- `database-design.md` 中 Question、KnowledgePoint、多对多关系、删除约束和时间字段设计与真实结构一致。

### 5.2 需要在 F-004 中修正的过期内容

`docs/project-status.md` 当前仍记录：

- 当前开发分支为 F-003 功能分支；
- F-003 只推送到远端功能分支；
- 测试日期仍为 2026-09-01。

真实状态是：

- F-001、F-002、F-003 已通过 Pull Request 合并到 `main`；
- F-003 合并提交为 `2106fc3`；
- 原功能分支已经删除；
- 2026-09-02 已在 `main` 上重新执行 36 个测试并全部通过。

`docs/database-design.md` 开头仍将“当前阶段”写为 F-003，也需要在 F-004 文档同步时修正。

Completed Plan 是阶段历史记录，不因为后续 Feature 已完成而回写其当时的“尚未实现”章节。

---

## 6. Feature 目标

F-004 的目标是打通错题管理的第一条完整后端业务链路：

```text
创建错题
→ 校验核心文本
→ 校验知识点
→ 自动推导科目
→ 保存错题和知识点关联
→ 查询详情
→ 分页浏览
→ 修改
→ 删除
```

完成后，系统能够通过 REST API 管理真实错题，为后续图片录入、复习数据模型和每日复习队列提供稳定前置能力。

---

## 7. 为什么 F-004 先做错题管理

MVP 核心闭环是：

```text
录入
→ 保存
→ 调度
→ 复习
→ 评价
→ 再调度
```

知识点管理已经完成，但系统还不能通过 API 创建错题。若直接开始复习模块，只能依赖手工 SQL 或测试代码准备错题，无法形成真实产品链路。

依赖顺序应为：

```text
知识点管理
↓
错题管理
↓
复习数据模型与调度
↓
Dashboard
```

F-004 可以复用现有 Entity、Repository、Validation、事务、异常响应和测试方式，并且不需要修改数据库表结构，具备清晰边界和独立验收条件。

---

## 8. 范围

### 8.1 包含范围

F-004 包含：

- 创建错题；
- 查询错题详情；
- 分页浏览错题列表；
- 按科目名称精确筛选错题；
- 完整修改错题；
- 删除错题；
- 管理错题与一个或多个知识点的直接关联；
- 根据所选知识点共同根节点自动生成 `subject`；
- 校验知识点存在、去重和同科目；
- 文本字段清理和长度校验；
- DTO；
- Validation；
- QuestionService；
- 事务；
- Repository 分页和关联加载查询；
- Question 业务异常；
- 统一异常响应扩展；
- Service 单元测试；
- Controller 真实 MySQL 集成测试；
- 手工 API 验证；
- API、数据库设计、项目状态和 Feature Plan 文档同步。

### 8.2 不包含范围

F-004 不包含：

- 图片上传；
- 图片下载或访问接口；
- 图片格式、大小和文件名管理；
- OCR；
- AI 自动识别或自动解题；
- 复习字段；
- `review_record`；
- 下一次复习时间；
- 掌握程度；
- 今日复习队列；
- 已掌握状态；
- Dashboard；
- 全文搜索；
- 按知识点及其后代进行专项筛选；
- 自定义排序；
- 逻辑删除；
- 回收站；
- 前端页面；
- 多用户、认证和权限；
- 新数据库表或字段；
- H2、Testcontainers、Flyway；
- Redis、消息队列、微服务等无真实需求的组件。

---

## 9. REST API 总览

基础路径：

```text
/api/questions
```

接口：

| 方法 | 路径 | 功能 | 成功状态 |
| --- | --- | --- | ---: |
| POST | `/api/questions` | 创建错题 | `201 Created` |
| GET | `/api/questions/{id}` | 查询错题详情 | `200 OK` |
| GET | `/api/questions` | 分页浏览与科目筛选 | `200 OK` |
| PUT | `/api/questions/{id}` | 完整修改错题 | `200 OK` |
| DELETE | `/api/questions/{id}` | 删除错题 | `200 OK` |

F-004 不提供 PATCH 接口，也不提供批量创建、批量修改和批量删除。

---

## 10. 请求字段与文本规则

### 10.1 创建请求字段

```json
{
  "questionText": "题目内容",
  "wrongAnswer": "我的错误答案",
  "correctAnswer": "正确答案",
  "analysis": "题目解析",
  "errorReason": "错误原因",
  "knowledgePointIds": [4, 5]
}
```

### 10.2 修改请求字段

PUT 请求提交全部可编辑字段，结构与创建请求相同。

### 10.3 不允许由请求提交的字段

请求不接收：

- `id`：由 MySQL 自动生成；
- `subject`：由 Service 根据知识点自动生成；
- `imagePath`：F-004 不管理图片；
- `createdTime`：由 MySQL 维护；
- `updatedTime`：由 MySQL 维护。

### 10.4 文本字段限制

| 字段 | 是否必填 | 最大长度 |
| --- | --- | ---: |
| `questionText` | 是 | 10000 字符 |
| `wrongAnswer` | 是 | 5000 字符 |
| `correctAnswer` | 是 | 5000 字符 |
| `analysis` | 是 | 10000 字符 |
| `errorReason` | 是 | 2000 字符 |

处理规则：

- 使用 `strip()` 去除整个字段首尾的空格、制表符和多余换行；
- 去除后不能为空；
- 保留正文内部的空格和换行；
- 不修改数学公式、选项和段落排版；
- 没有实际作答时，`wrongAnswer` 应填写“未作答”，不能使用空字符串代替。

Validation 负责非空和最大长度；Service 在保存前再次使用标准化后的值。

---

## 11. 知识点与科目规则

### 11.1 知识点集合

- `knowledgePointIds` 必填；
- 至少包含一个 ID；
- 不允许包含 `null`；
- 不允许重复 ID；
- 每一个知识点必须真实存在；
- 可以选择根节点或普通节点；
- 不限制只能选择叶子节点；
- 允许同时直接选择父知识点和子知识点；
- 关联表只保存请求中直接选择的知识点，不自动保存祖先节点。

### 11.2 subject 自动生成

请求不提交 `subject`。

Service 对所有所选知识点向上查找根节点：

```text
知识点A → 根节点408
知识点B → 根节点408
```

全部根节点相同时：

```text
question.subject = 根节点名称
```

### 11.3 禁止跨科目混合关联

以下请求非法：

```text
知识点A → 根节点408
知识点B → 根节点数学
```

返回：

```text
400 QUESTION_KNOWLEDGE_POINTS_CROSS_SUBJECT
```

### 11.4 修改时切换科目

允许通过整体替换知识点集合将错题切换到另一科目。

例如：

```text
修改前：[TCP、拥塞控制] → subject=408
修改后：[极限、连续] → subject=数学
```

新的知识点集合仍必须全部属于同一新根节点。

---

## 12. 创建规则

创建流程：

```text
Validation 校验请求结构
↓
Service 清理文本
↓
检查知识点 ID 是否重复
↓
加载知识树并确认全部 ID 存在
↓
查找共同根节点
↓
生成 subject
↓
创建 Question
↓
保存直接知识点关联
↓
刷新数据库生成的时间字段
↓
返回详情 DTO
```

整个流程使用一个写事务。任何一步失败，Question 和关联表均不得留下部分数据。

---

## 13. 详情查询规则

```http
GET /api/questions/{id}
```

规则：

- 一次返回错题全部核心字段；
- 返回直接关联知识点；
- 知识点按 ID 升序；
- 不直接返回 Entity；
- 错题不存在时返回 `404 QUESTION_NOT_FOUND`；
- 使用只读事务。

详情响应示例：

```json
{
  "id": 101,
  "questionText": "TCP拥塞控制中，慢开始阶段……",
  "wrongAnswer": "拥塞窗口线性增长",
  "correctAnswer": "拥塞窗口指数增长",
  "analysis": "慢开始阶段每经过一个RTT……",
  "errorReason": "混淆了慢开始和拥塞避免",
  "subject": "408",
  "imagePath": null,
  "knowledgePoints": [
    {
      "id": 8,
      "name": "TCP",
      "parentId": 7
    },
    {
      "id": 9,
      "name": "拥塞控制",
      "parentId": 8
    }
  ],
  "createdTime": "2026-09-03T10:20:30",
  "updatedTime": "2026-09-03T10:20:30"
}
```

---

## 14. 分页列表规则

### 14.1 请求参数

```http
GET /api/questions?page=0&size=20
```

科目筛选：

```http
GET /api/questions?page=0&size=20&subject=408
```

### 14.2 分页约定

- `page` 从 0 开始；
- 默认 `page=0`；
- 默认 `size=20`；
- `page` 不能小于 0；
- `size` 最小为 1；
- `size` 最大为 100；
- 超出实际范围的页返回 `200 OK` 和空 `items`；
- 不直接向调用方暴露 Spring Data 的 Page 对象。

### 14.3 排序

固定使用：

```text
question.id DESC
```

后创建的错题排在前面。F-004 不允许调用方自定义排序。

### 14.4 科目筛选

- `subject` 可选；
- 不传时查询全部错题；
- 传入时去除首尾空白后进行精确匹配；
- 参数只包含空白时返回 400；
- 不存在的科目返回空页，不返回 404；
- F-004 不提供知识点筛选。

### 14.5 列表摘要

每个列表项返回：

- `id`；
- `questionText`；
- `subject`；
- 直接关联的 `knowledgePoints`；
- `createdTime`；
- `updatedTime`。

列表不返回：

- `wrongAnswer`；
- `correctAnswer`；
- `analysis`；
- `errorReason`；
- `imagePath`。

知识点按 ID 升序返回。

### 14.6 分页响应

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 35,
  "totalPages": 2
}
```

只返回项目真正需要的分页信息，不增加通用 `data` 包装层。

---

## 15. 修改规则

```http
PUT /api/questions/{id}
```

PUT 表示提交全部可编辑状态。

修改时：

- 重新校验所有文本字段；
- 重新校验完整知识点集合；
- 知识点关联整体替换，不做增量合并；
- 根据新集合重新生成 `subject`；
- `id` 保持不变；
- `createdTime` 保持不变；
- `imagePath` 保持不变；
- `updatedTime` 必须反映本次修改；
- 任意一步失败时文本、subject 和知识点关联全部保持原状态；
- 目标不存在时返回 `404 QUESTION_NOT_FOUND`。

如果只修改知识点关联而文本未变化，实现仍应确保 `updatedTime` 得到更新。应采用最小的数据库时间刷新方案，不为此增加新表或新时间管理框架。

---

## 16. 删除规则

```http
DELETE /api/questions/{id}
```

删除采用真实删除：

```text
删除 question
↓
数据库 ON DELETE CASCADE
↓
自动删除 question_knowledge_point 记录
```

规则：

- 不删除任何 KnowledgePoint；
- 不手工逐条删除关联表；
- 目标不存在时返回 `404 QUESTION_NOT_FOUND`；
- 成功返回 `200 OK`；
- 成功响应为：

```json
{
  "message": "错题删除成功"
}
```

当前没有复习记录表。未来增加复习记录后，错题删除与复习历史的关系必须在复习 Feature 中重新确认。

---

## 17. DTO 设计

### 17.1 CreateQuestionRequest

字段：

- `questionText`
- `wrongAnswer`
- `correctAnswer`
- `analysis`
- `errorReason`
- `knowledgePointIds`

使用 Bean Validation 表达非空、长度和集合元素非空规则。

### 17.2 UpdateQuestionRequest

字段与创建请求相同，但独立定义，以避免未来创建和修改合同发生变化时互相影响。

### 17.3 QuestionDetailResponse

字段：

- `id`
- `questionText`
- `wrongAnswer`
- `correctAnswer`
- `analysis`
- `errorReason`
- `subject`
- `imagePath`
- `knowledgePoints`
- `createdTime`
- `updatedTime`

### 17.4 QuestionSummaryResponse

字段：

- `id`
- `questionText`
- `subject`
- `knowledgePoints`
- `createdTime`
- `updatedTime`

### 17.5 QuestionPageResponse

字段：

- `items`
- `page`
- `size`
- `totalElements`
- `totalPages`

当前只为错题分页创建具体 DTO，不提前建立尚未被第二个模块使用的通用分页框架。

### 17.6 KnowledgePointResponse

错题响应复用现有知识点普通响应结构：

- `id`
- `name`
- `parentId`

### 17.7 MessageResponse

删除知识点和删除错题都使用同一种简单消息结构。

将现有 `MessageResponse` 从知识点专用包移动到公共 DTO 包，并同步更新 F-003 引用。只移动真实已经跨模块复用的 DTO，不建立通用成功响应包装层。

---

## 18. HTTP 状态码与错误码

| 场景 | HTTP 状态 | code |
| --- | ---: | --- |
| 请求字段为空或超过长度 | 400 | `VALIDATION_FAILED` |
| 分页参数非法 | 400 | `VALIDATION_FAILED` |
| 科目筛选为空白 | 400 | `VALIDATION_FAILED` |
| 知识点 ID 重复 | 400 | `QUESTION_DUPLICATE_KNOWLEDGE_POINT` |
| 多个知识点跨科目 | 400 | `QUESTION_KNOWLEDGE_POINTS_CROSS_SUBJECT` |
| 错题不存在 | 404 | `QUESTION_NOT_FOUND` |
| 知识点不存在 | 404 | `KNOWLEDGE_POINT_NOT_FOUND` |
| JSON 无法解析 | 400 | `MALFORMED_REQUEST_BODY` |
| 数据库完整性冲突 | 409 | `DATA_INTEGRITY_CONFLICT` |

错误响应继续使用现有 ApiErrorResponse：

```json
{
  "timestamp": "2026-09-03T02:20:30Z",
  "status": 400,
  "code": "QUESTION_KNOWLEDGE_POINTS_CROSS_SUBJECT",
  "message": "一道错题关联的知识点必须属于同一科目",
  "path": "/api/questions"
}
```

Validation 失败继续返回 `fieldErrors`。

---

## 19. 技术分层

```text
HTTP / JSON
↓
QuestionController
↓
请求 DTO + Validation
↓
QuestionService + Transaction
↓
QuestionRepository / KnowledgePointRepository
↓
Question / KnowledgePoint Entity
↓
MySQL
```

### 19.1 Controller

负责：

- 接收 HTTP 请求；
- 读取路径和查询参数；
- 触发 Validation；
- 调用 QuestionService；
- 返回 DTO 和状态码。

不得：

- 直接调用 Repository；
- 直接返回 Entity；
- 在 Controller 中查找知识点根节点；
- 在 Controller 中拼装分页业务数据。

### 19.2 DTO 与 Validation

DTO 使用 Java `record`。

Validation 负责通用输入结构，Service 负责需要数据库和树结构才能判断的业务规则。

分页参数应通过明确的查询参数约束或分页请求 DTO 进入 Validation，不能依赖数据库异常处理负数页码。

### 19.3 Service

只创建一个具体 `QuestionService` 类，不创建没有第二种实现的接口和 Impl。

Service 负责所有错题业务规则、事务、Entity 到 DTO 的转换和分页响应组装。

### 19.4 Repository

Repository 负责 JPA 查询，不返回 Controller 专用 JSON 结构。

### 19.5 Entity 与 MySQL

Entity 继续映射三张现有表。MySQL 继续负责主键、外键、联合主键、级联删除和时间默认值。

### 19.6 Exception Handler

新增 QuestionNotFoundException 与 QuestionValidationException，并在 GlobalExceptionHandler 中映射成统一 ApiErrorResponse。

不建立复杂的异常继承树。

---

## 20. Transaction 设计

### 20.1 写事务

以下方法使用 `@Transactional`：

- create；
- update；
- delete。

### 20.2 只读事务

以下方法使用 `@Transactional(readOnly = true)`：

- getDetail；
- getPage。

### 20.3 创建原子性

Question 和知识点关联必须同时成功或同时回滚。

### 20.4 修改原子性

文本、subject、完整知识点集合和更新时间必须作为同一个业务操作处理。任意失败不得留下部分修改。

### 20.5 删除原子性

Question 删除和关联表级联删除由同一数据库事务完成。

---

## 21. Repository 变更计划

### 21.1 QuestionRepository

保留 F-003 根节点改名所使用的：

```text
findAllBySubject(String subject)
```

新增能力：

- 查询全部错题的分页 ID，固定 ID 倒序；
- 按 subject 查询分页 ID，固定 ID 倒序；
- 根据一页 ID 批量加载 Question 及知识点；
- 根据单个 ID 加载 Question 详情及知识点；
- 在只修改关联时确保 `updated_time` 反映修改的最小持久化操作（实现时需与刷新方案一起验证）。

### 21.2 两步分页查询

列表使用：

```text
查询1：分页查询当前页 Question ID
↓
查询2：按 ID 集合一次加载 Question 和 KnowledgePoint
↓
按查询1的 ID 顺序重新排序
↓
映射 QuestionSummaryResponse
```

不直接对包含多对多集合的 fetch join 结果做分页，避免重复行、错误页大小或内存分页。

### 21.3 KnowledgePointRepository

优先复用现有一次加载知识点及父节点的查询，建立 ID 映射并向上查找根节点。

F-004 不增加递归 SQL、路径字段或额外 subject 字段。

---

## 22. Entity 变更计划

Question Entity 保留现有字段和映射。

只增加业务真正需要的方法，例如：

- 整体替换知识点集合；
- 在需要时明确触发更新时间处理。

不增加：

- DTO 字段；
- 分页字段；
- Service 依赖；
- 复习状态；
- 逻辑删除；
- 图片文件处理逻辑。

---

## 23. open-in-view 配置

在 `application.yaml` 明确设置：

```yaml
spring:
  jpa:
    open-in-view: false
```

所有关联数据必须在 Service 只读事务内加载并转换成 DTO。

作用：

- 防止 Controller 或 JSON 序列化阶段临时访问数据库；
- 让 Repository 查询是否完整在测试阶段暴露；
- 消除当前默认开启警告；
- 保持 HTTP 层与数据访问层边界。

这不是新增框架或架构层。

---

## 24. 计划代码结构

```text
backend/src/main/java/com/wrongquestion/backend/
├── common/
│   ├── dto/
│   │   └── MessageResponse.java
│   └── exception/
│       ├── ApiErrorResponse.java
│       └── GlobalExceptionHandler.java
├── knowledge/
│   ├── dto/
│   │   └── KnowledgePointResponse.java
│   └── ...
└── question/
    ├── controller/
    │   └── QuestionController.java
    ├── dto/
    │   ├── CreateQuestionRequest.java
    │   ├── UpdateQuestionRequest.java
    │   ├── QuestionDetailResponse.java
    │   ├── QuestionSummaryResponse.java
    │   └── QuestionPageResponse.java
    ├── exception/
    │   ├── QuestionNotFoundException.java
    │   └── QuestionValidationException.java
    ├── service/
    │   └── QuestionService.java
    ├── entity/
    │   └── Question.java
    └── repository/
        └── QuestionRepository.java
```

根据分页参数 Validation 的最终实现方式，可以增加一个具体的分页查询 DTO，但不创建通用查询框架。

---

## 25. 测试方案

### 25.1 测试原则

采用：

```text
QuestionService 单元测试
+
QuestionController 真实 MySQL 集成测试
+
原有测试回归
+
手工 API 验证
```

不以测试数量为目标，以已确认业务分支是否被覆盖为目标。

### 25.2 QuestionService 单元测试

创建至少覆盖：

- 单知识点和多知识点正常创建；
- 文本清理；
- subject 自动生成；
- 文本为空和超长；
- 知识点集合为空、含 null、重复；
- 知识点不存在；
- 知识点跨科目；
- 父子知识点同时选择。

修改至少覆盖：

- 全字段修改；
- 知识点整体替换；
- 旧关联移除；
- subject 重算；
- 跨科目切换；
- imagePath 保留；
- 失败不保存；
- Question 不存在。

查询至少覆盖：

- 详情映射；
- 知识点 ID 升序；
- Question 不存在；
- 分页响应；
- ID 倒序保持；
- 空页；
- 科目筛选条件。

删除至少覆盖：

- 正常删除；
- Question 不存在；
- 成功消息。

Service 单元测试使用 Mockito 模拟 Repository，不启动 Spring，不连接 MySQL。

### 25.3 QuestionController 真实 MySQL 集成测试

继续使用：

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
```

至少覆盖：

- POST 返回 201；
- 全部文本字段写入数据库；
- subject 自动生成；
- 多条关联写入；
- GET 详情；
- 分页元数据；
- ID 倒序；
- 科目筛选；
- PUT 文本和知识点整体替换；
- PUT 跨科目修改；
- DELETE 返回 200 和消息；
- 关联表 ON DELETE CASCADE；
- KnowledgePoint 不被删除；
- 空字段和超长字段；
- 空、含 null、重复知识点集合；
- 跨科目知识点；
- 不存在的 KnowledgePoint；
- 不存在的 Question；
- 非法分页参数；
- 非法 JSON。

测试数据通过事务回滚，不污染个人数据库。

### 25.4 Repository 查询验证

分页、详情和批量关联加载由 Controller 真实 MySQL 测试实际执行。

如果实现时发现某个复杂查询难以从 Controller 测试定位，再补一个有针对性的 QuestionRepositoryTest，不提前创建重复测试。

### 25.5 原有测试保护

必须保证原有 36 个测试继续通过，包括 F-003 根节点改名同步 Question.subject 的测试。

### 25.6 最终测试命令

Windows：

```powershell
cd D:\Projects\wrong-question-system\backend
.\mvnw.cmd test
```

要求：

```text
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

---

## 26. 手工 API 验证

自动化测试通过后，至少手工验证：

1. 创建临时错题；
2. 查询详情；
3. 查询分页列表；
4. 按科目筛选；
5. 修改文本和知识点；
6. 将错题切换到另一科目；
7. 混合不同科目知识点并确认返回 400；
8. 删除错题；
9. 使用 Navicat 确认关联记录级联删除；
10. 确认知识点仍存在；
11. 清理全部临时数据。

Windows PowerShell 5.1 若再次出现 JSON 中文显示乱码，需要同时使用浏览器原始响应或 Navicat 核对，区分终端显示问题与后端编码问题。

---

## 27. 文档变更计划

### 27.1 api-design.md

记录：

- 5 个错题 API；
- 请求与响应结构；
- 分页和科目筛选；
- 错误码；
- F-004 明确不提供的接口。

### 27.2 database-design.md

不修改表结构，但同步：

- 当前阶段；
- subject 自动生成；
- 至少一个知识点；
- 同科目规则；
- 整体替换关联；
- Question 删除级联；
- ID 倒序分页；
- image_path 只读；
- 复习字段仍未设计。

### 27.3 project-status.md

开始时：

- 修正 F-003 合并后的真实 Git 状态；
- 记录 F-004 为 Active；
- 记录功能分支名称。

完成并合并后：

- 记录 F-004 为 Completed；
- 记录最终测试结果；
- 记录 PR 和 main 合并提交；
- 当前分支写为 `main`；
- 不保留已经删除的 F-004 功能分支作为“当前分支”。

### 27.4 Feature Plan

实现和验证过程中同步任务状态。

完成后将：

```text
docs/plans/active/F-004-question-management.md
```

移动到：

```text
docs/plans/completed/F-004-question-management.md
```

### 27.5 不修改

- PRODUCT.md：产品目标未变化；
- ADR-001：技术决策未变化；
- F-001～F-003 Completed Plan：历史记录；
- `sql/init.sql`：表结构不变。

---

## 28. 实施顺序

### 阶段 0：计划与 Git 基线

- [x] 将本计划保存到 `docs/plans/active/`；
- [x] 检查 `main` 与 `origin/main` 一致；
- [x] 检查工作区 clean；
- [x] 从最新 main 创建 `feature/F-004-question-management`；
- [x] 提交并推送正式计划。

### 阶段 1：公共结构与配置

- [x] 将 MessageResponse 移入公共 DTO 包；
- [x] 同步 KnowledgePointController 和 Service 引用；
- [x] 增加 Question 异常；
- [x] 扩展 GlobalExceptionHandler；
- [x] 设置 `open-in-view: false`；
- [x] 运行原有测试确认无回归。

### 阶段 2：DTO 与 Validation

- [x] 创建请求 DTO；
- [x] 创建详情 DTO；
- [x] 创建摘要 DTO；
- [x] 创建分页 DTO；
- [x] 实现文本和集合 Validation；
- [x] 实现分页参数 Validation。

### 阶段 3：Repository 与 Entity

- [x] 增加详情关联查询；
- [x] 增加分页 ID 查询；
- [x] 增加科目分页 ID 查询；
- [x] 增加批量关联加载；
- [x] 增加 Question 知识点整体替换方法；
- [x] 验证数据库时间字段刷新方案；
- [x] 验证关联单独变化时 updated_time 行为。

### 阶段 4：QuestionService

- [x] 实现文本标准化；
- [x] 实现知识点集合校验；
- [x] 实现共同根节点查找；
- [x] 实现 subject 自动生成；
- [x] 实现创建；
- [x] 实现详情；
- [x] 实现两步分页；
- [x] 实现科目筛选；
- [x] 实现完整修改；
- [x] 实现删除；
- [x] 实现 DTO 映射和稳定排序；
- [x] 添加事务边界。

### 阶段 5：QuestionController

- [x] 实现 5 个 REST API；
- [x] 设置正确状态码；
- [x] Controller 只调用 Service；
- [x] 不直接返回 Entity。

### 阶段 6：自动化测试

- [x] 编写 QuestionServiceTest；
- [x] 编写 QuestionControllerTest；
- [x] 评估后确认现有 Repository 测试与 Controller 集成测试已覆盖本阶段查询，无需重复新增测试类；
- [x] 执行全部测试；
- [x] 确认原有 36 个测试继续通过；
- [x] 确认新增 29 个测试全部通过。

### 阶段 7：手工验证

- [x] 启动后端；
- [x] 执行完整成功链路；
- [x] 执行主要失败场景；
- [x] 使用 Navicat 验证关联和级联删除；
- [x] 清理临时数据。

### 阶段 8：文档与功能分支收尾

- [x] 更新 api-design.md；
- [x] 更新 database-design.md；
- [x] 更新 project-status.md；
- [x] 更新本计划任务状态；
- [x] 将本计划移至 completed；
- [x] 再次执行全量测试；
- [ ] 检查 diff 和 status；
- [ ] 提交并推送功能分支。

### 阶段 9：PR 与 main 收尾

- [ ] 创建 F-004 → main Pull Request；
- [ ] 确认 PR 只包含 F-004；
- [ ] 使用 Create a merge commit；
- [ ] 拉取最新 main；
- [ ] 在 main 上重新执行全量测试；
- [ ] 确认 main 工作区 clean；
- [ ] 删除本地和远程 F-004 分支；
- [ ] 确认 project-status.md 最终 Git 状态准确。

---

## 29. 验收标准

### 29.1 功能验收

- [x] 5 个错题 API 均可正常调用；
- [x] 创建支持一个或多个知识点；
- [x] subject 自动推导正确；
- [x] 跨科目混合知识点被拒绝；
- [x] 详情返回完整字段；
- [x] 列表分页正确；
- [x] 列表固定 ID 倒序；
- [x] 科目筛选正确；
- [x] 修改整体替换知识点；
- [x] 修改允许切换科目；
- [x] imagePath 在修改时保持不变；
- [x] 删除级联清理关联表；
- [x] 删除不影响知识点。

### 29.2 API 验收

- [x] POST 返回 201；
- [x] GET、PUT、DELETE 返回约定状态；
- [x] 请求和响应字段与计划一致；
- [x] Validation 失败返回 400；
- [x] Question 不存在返回 404；
- [x] KnowledgePoint 不存在返回 404；
- [x] 数据库完整性冲突统一映射为 409；
- [x] 不直接返回 Entity；
- [x] 不增加通用成功包装层。

### 29.3 代码验收

- [x] Controller 不直接调用 Repository；
- [x] Validation 与业务校验职责分离；
- [x] Service 负责业务与 DTO 映射；
- [x] 写操作有事务；
- [x] 查询使用只读事务；
- [x] 列表避免 N+1；
- [x] open-in-view 已关闭；
- [x] 不增加无真实用途的接口或框架；
- [x] 不修改数据库表结构；
- [x] 项目可以编译和启动。

### 29.4 测试验收

- [x] Service 单元测试覆盖约定业务分支；
- [x] Controller 真实 MySQL 集成测试覆盖代表性链路；
- [x] 原有 36 个测试继续通过；
- [x] 新增 29 个测试全部通过；
- [x] Failures、Errors、Skipped 均为 0；
- [x] BUILD SUCCESS；
- [x] 手工 API 验证完成；
- [x] 临时数据全部清理。

### 29.5 文档验收

- [x] api-design.md 与实现一致；
- [x] database-design.md 与实现一致；
- [x] project-status.md 与真实 Git 和测试状态一致；
- [x] Completed Plan 保持历史语义；
- [x] 本计划完成后移入 completed。

### 29.6 Git 验收

- [x] F-004 分支从最新 main 创建；
- [ ] PR 只包含 F-004；
- [ ] 使用 Merge Commit 合并；
- [ ] F-004 已进入 main；
- [ ] 合并后 main 测试通过；
- [ ] main 工作区 clean；
- [ ] 本地和远程 F-004 分支已删除。

---

## 30. 风险与处理

### 30.1 subject 与知识点重复表达

数据库同时保存 subject 字符串和知识点树归属。F-004 不让请求提交 subject，而是从共同根节点自动生成，避免不一致输入。

### 30.2 多对多分页

直接 fetch join 多对多集合并分页可能造成重复行或内存分页。使用“分页 ID + 批量关联加载”两步查询。

### 30.3 LAZY 关联与 open-in-view

关闭 open-in-view 后，如果 Repository 未正确加载知识点，Service 映射会立即暴露问题。通过明确关联查询和真实 MySQL 集成测试解决，不重新开启 open-in-view 掩盖问题。

### 30.4 数据库生成时间字段

created_time、updated_time 由 MySQL 维护。创建和修改响应必须在持久化后获得真实数据库值；只修改关联时也必须验证 updated_time 是否反映业务修改。实现阶段采用最小刷新或 touch 方案，不改变表结构。

### 30.5 真实 MySQL 测试

延续当前本地真实 MySQL 测试方式，通过事务回滚隔离测试数据。自动 CI 和 Testcontainers 在出现真实需求时再评估。

### 30.6 文本长度与 TEXT

MySQL TEXT 不是无限长度。F-004 使用明确的应用层字符限制，优先返回清晰 400，而不是让超长内容在数据库层失败。

### 30.7 当前没有复习记录

当前删除 Question 可以只考虑关联表级联。增加复习历史后必须重新设计删除规则，不能默认沿用 F-004 结论。

---

## 31. 已确认决策汇总

| 决策项 | 结论 |
| --- | --- |
| F-004 目标 | 完整基础错题管理业务层与 REST API |
| API 数量 | 5 个 |
| 图片上传 | 不包含 |
| OCR | 不包含 |
| 复习 | 不包含 |
| 前端 | 不包含 |
| subject 输入 | 请求不提交 |
| subject 来源 | 所选知识点共同根节点名称 |
| 跨科目知识点 | 禁止 |
| 修改时切换科目 | 允许 |
| 最少知识点数量 | 1 |
| 根节点可否直接关联 | 可以 |
| 父子知识点可否同时关联 | 可以 |
| 重复知识点 ID | 400，拒绝 |
| 文本处理 | strip 首尾，保留内部格式 |
| 修改语义 | PUT 全量修改 |
| 关联修改 | 整体替换 |
| 删除 | 真实删除 |
| 删除响应 | 200 + JSON 消息 |
| 分页页码 | 从 0 开始 |
| 默认页大小 | 20 |
| 最大页大小 | 100 |
| 排序 | ID 倒序 |
| 科目筛选 | 可选，名称精确匹配 |
| 知识点筛选 | 不包含 |
| 列表知识点 | 返回直接关联知识点 |
| imagePath | 详情只读，修改时保留 |
| Service | 单个具体类 |
| DTO | Java record |
| 事务 | Service 写事务与只读事务 |
| 列表查询 | 分页 ID + 批量关联加载 |
| open-in-view | 关闭 |
| 数据库表变更 | 无 |
| 新 Maven 依赖 | 无 |
| ADR | 不新增 |
| 测试 | Service 单测 + Controller 真实 MySQL集成测试 |
| Git 完成 | PR 合并 main、main 回归、删除分支 |

---

## 32. 实施与验证结果

2026-09-02 在 `feature/F-004-question-management` 上完成实现和验证。

自动化测试环境与结果：

```text
Java 21.0.12
MySQL 9.6
Tests run: 65
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

其中原有 36 个测试全部继续通过，新增：

- `QuestionServiceTest`：21 个；
- `QuestionControllerTest`：8 个；
- 新增测试合计：29 个。

手工验证完成：

- 创建、详情、分页、科目筛选、完整修改和删除成功链路；
- subject 自动推导、父子知识点共同选择和跨科目切换；
- ID 倒序、列表摘要字段和超范围空页；
- 重复知识点、跨科目知识点、参数校验、损坏 JSON 和资源不存在错误；
- 非法创建不产生数据，非法更新保持原状态；
- 删除错题清理关联但保留知识点；
- 临时错题、关联和知识点全部清理；
- Navicat 最终查询确认本次临时数据数量均为 0。

本阶段没有修改数据库表结构，没有增加 Maven 依赖或 ADR。

---

## 33. 完成定义

F-004 的仓库级收尾需要满足以下条件：

1. 五个错题 API 全部实现；
2. 所有已确认字段、知识点、subject、分页、修改和删除规则生效；
3. Controller、DTO、Validation、Service、Transaction、Repository、Entity 和 MySQL 职责清晰；
4. 列表没有 N+1 或错误的多对多分页行为；
5. Service 单元测试和 Controller 真实 MySQL 集成测试全部通过；
6. 原有 36 个测试未被破坏；
7. 全量 Maven 测试为 BUILD SUCCESS；
8. 手工 API 验证完成并清理临时数据；
9. API、数据库设计、项目状态和 Feature Plan 与实现一致；
10. Feature Plan 已移入 `docs/plans/completed/`；
11. F-004 Pull Request 已使用 Merge Commit 合并到 main；
12. 合并后的 main 已再次通过全量测试；
13. main 工作区 clean；
14. 本地和远程 F-004 功能分支已删除；
15. project-status.md 不再遗留过期功能分支状态。

第 1～10 项已经完成。第 11～15 项属于 PR 与 `main` 收尾，在本计划归档后继续执行；这些步骤完成前，`project-status.md` 中 F-004 仍保持 Active。

当前没有未确认的 F-004 范围、业务规则、技术方案、测试范围或完成标准。
