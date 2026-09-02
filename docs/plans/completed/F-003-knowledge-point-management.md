# F-003 知识点管理业务层与 REST API

## 1. Feature 基本信息

| 项目 | 内容 |
| --- | --- |
| Feature ID | F-003 |
| Feature 名称 | 知识点管理业务层与 REST API |
| 状态 | Completed |
| 计划日期 | 2026-08-31 |
| 完成日期 | 2026-09-01 |
| 目标分支 | `feature/F-003-knowledge-point-management` |
| 前置 Feature | F-001、F-002 |
| 目标目录 | `docs/plans/completed/F-003-knowledge-point-management.md` |

---

## 2. 文档用途

本文件是 F-003 的正式 Feature Plan，用于约束本阶段的目标、范围、业务规则、技术方案、测试范围和完成标准。

本文件只记录已经确认的内容。实现过程中如果发现必须修改的业务规则，应先讨论并更新本文件，再修改实现，避免代码、数据库与文档长期不一致。

本 Feature Plan 不承担 `project-status.md` 的职责，不重复记录整个项目的发展历史。

---

## 3. 规划依据

F-003 基于以下当前项目文件和真实代码重新规划：

- `docs/PRODUCT.md`
- `docs/database-design.md`
- `docs/project-status.md`
- `docs/decisions/ADR-001-initial-tech-stack-and-architecture.md`
- `docs/plans/completed/F-002-database-design.md`
- `pom.xml`
- `application.yaml`
- `Question.java`
- `KnowledgePoint.java`
- `QuestionRepository.java`
- `KnowledgePointRepository.java`
- `QuestionRepositoryTest.java`
- `KnowledgePointRepositoryTest.java`
- `HealthControllerTest.java`

本次规划不直接沿用此前聊天中关于 F-003 的结论。

---

## 4. 当前项目基线

F-001 和 F-002 已完成，当前已经具备：

- Java 21。
- Spring Boot 4.1.1。
- Spring MVC。
- Spring Data JPA。
- Hibernate。
- MySQL 9.6。
- `Question` Entity。
- `KnowledgePoint` Entity。
- `QuestionRepository`。
- `KnowledgePointRepository`。
- Question 与 KnowledgePoint 的单向多对多映射。
- KnowledgePoint 的父节点自关联。
- `ddl-auto: validate` 数据库结构校验。
- Repository 真实 MySQL 集成测试。

当前尚未实现：

- 知识点 Service。
- 知识点业务 Controller。
- 知识点 REST API。
- API 请求与响应 DTO。
- 请求参数 Validation。
- 生产业务事务边界。
- 统一异常响应。
- 知识树查询接口。
- 知识点新增、修改、移动和删除接口。

---

## 5. 已发现的现有文档问题

这些问题不是 F-003 的业务目标，但需要在 F-003 文档工作中修正。

### 5.1 ADR-001 与真实代码一致

上传后的真实仓库显示，ADR-001 已经明确记录：

- 使用 Spring Data JPA。
- 不引入 MyBatis、MyBatis-Plus。
- 数据库迁移工具暂未确定。

这些决定与当前代码一致，因此 F-003 不新增其他 ADR，也不重复记录已经存在的技术决策。

### 5.2 database-design.md 状态过期

文档顶部及末尾仍包含“待编写 SQL、待创建 Entity、待创建 Repository”等已经完成的内容。

时间字段由数据库还是 JPA 维护也已经确定，应更新为：

- 数据库维护 `created_time` 和 `updated_time`。
- Entity 通过 `insertable = false`、`updatable = false` 读取时间字段。

### 5.3 project-status.md 存在收尾残留

文档中存在重复标题、F-002 收尾步骤残留及已经过期的下一步描述，需要在 F-003 文档同步时清理。

---

## 6. Feature 目标

F-003 的目标是打通知识点管理的第一条完整后端业务链路：

```text
HTTP 请求
↓
Controller
↓
DTO + Validation
↓
Service + Transaction
↓
Repository
↓
Entity
↓
MySQL
```

完成后，Java 后端应能够通过稳定的 REST API：

- 查询完整知识树。
- 新增根知识点或子知识点。
- 修改知识点名称。
- 在同一知识树内部调整层级。
- 安全删除符合条件的知识点。
- 对非法业务操作返回明确、稳定的错误响应。

---

## 7. 为什么 F-003 先做知识点管理

错题创建要求至少关联一个知识点，因此错题管理 API 依赖一个可查询、可维护的知识体系。

当前合理依赖顺序为：

```text
F-002 数据模型与 Repository
↓
F-003 知识点管理业务与 API
↓
后续错题管理业务与 API
↓
后续复习调度与复习记录
```

如果先做错题 API，知识点只能通过 Navicat 或测试代码手工准备，无法形成真实可用的录入流程。

复习模块又依赖可正常创建和管理的错题，因此不应提前进入复习功能。

---

## 8. F-003 范围

### 8.1 包含范围

- 查询完整知识树。
- 新增根知识点。
- 新增子知识点。
- 修改知识点名称。
- 同一知识树内调整父节点。
- 删除没有子节点且没有错题引用的知识点。
- 根节点改名时同步相关错题的 `subject`。
- 名称、父节点、循环引用和移动范围校验。
- 请求 DTO 与响应 DTO。
- Validation。
- Service。
- Transaction。
- Controller。
- REST API。
- 统一 Exception Handler。
- Service 单元测试。
- Controller 真实 MySQL 集成测试。
- API、数据库设计和项目状态文档同步。

### 8.2 不包含范围

- 错题 CRUD API。
- 错题分页与搜索。
- 图片上传与访问。
- OCR。
- 复习记录。
- 复习队列。
- 复习算法。
- 掌握程度。
- Dashboard。
- 前端页面。
- Subject 独立数据表。
- 知识点手工排序字段。
- 知识树分页。
- 跨知识树迁移。
- 递归删除子树。
- 自动解除错题关联。
- H2。
- Testcontainers。
- Flyway。
- MapStruct。
- Service 接口与 `ServiceImpl` 双层结构。
- 当前没有真实需求的缓存、消息队列或分布式组件。

---

## 9. 本 Feature 中各层的职责

### 9.1 REST

REST 是本 Feature 对外提供 HTTP 接口的组织方式。

知识点作为资源，通过 HTTP 方法表达操作：

- `GET`：查询。
- `POST`：创建。
- `PUT`：修改。
- `DELETE`：删除。

REST 不是一个新的代码层，而是 Controller 对外接口的设计方式。

### 9.2 Controller

Controller 位于 HTTP 请求入口，负责：

- 接收路径参数和 JSON 请求体。
- 触发 Validation。
- 调用 Service。
- 返回响应 DTO 和 HTTP 状态码。

Controller 不负责：

- 查询或保存 Entity。
- 判断知识树是否形成环。
- 判断是否跨知识树移动。
- 判断是否允许删除。
- 直接调用 Repository。

### 9.3 DTO

DTO 是 API 专用的数据载体，负责在前端与后端之间传递数据。

F-003 不直接把 Entity 作为请求体或响应体，原因包括：

- Entity 服务于 JPA 与数据库映射，不等于 API 合同。
- `KnowledgePoint.parent` 是对象关系，直接序列化容易引起懒加载或递归问题。
- API 字段未来可以独立演进，不必强迫 Entity 同步变化。

DTO 使用 Java `record` 实现，保持数据结构简洁、不可变。

### 9.4 Validation

Validation 负责通用输入格式校验：

- 名称不能为空。
- 输入名称不能超过 100 个字符。
- 请求体必须是合法 JSON。

Validation 不负责依赖数据库或当前业务状态的判断。

### 9.5 Service

Service 位于 Controller 和 Repository 之间，负责知识点业务规则：

- 名称清理。
- 根节点与普通节点长度区别。
- 重名检查。
- 父节点存在检查。
- 自引用检查。
- 循环引用检查。
- 同树移动检查。
- 删除安全检查。
- 根节点改名后的错题 `subject` 同步。
- Entity 与 DTO 转换。
- 知识树组装。

F-003 只创建一个具体的 `KnowledgePointService` 类，不创建没有第二种实现的接口和 `Impl` 类。

### 9.6 Transaction

Transaction 保证一次业务操作中的多个数据库动作具有原子性。

尤其是根节点改名：

```text
修改根节点名称
+
更新相关 Question.subject
```

两部分必须全部成功或全部回滚，不能只完成其中一部分。

写操作在 Service 上使用 `@Transactional`，知识树查询使用 `@Transactional(readOnly = true)`。

### 9.7 Repository

Repository 负责数据访问，不负责决定业务规则。

Service 通过 Repository：

- 查询知识点。
- 检查重名。
- 检查子节点。
- 检查错题引用。
- 查询需要同步 `subject` 的错题。
- 保存或删除数据。

### 9.8 Entity 与 MySQL

Entity 继续映射 F-002 已建立的数据库表：

- `knowledge_point`。
- `question`。
- `question_knowledge_point`。

F-003 不修改数据库表结构。

### 9.9 Exception Handler

Exception Handler 统一把 Java 异常转换成可预测的 HTTP 状态码和 JSON 错误结构。

它避免：

- 把数据库异常堆栈直接返回给调用方。
- 不同 Controller 各自拼装不同错误格式。
- 前端依赖不稳定的异常文本。

---

## 10. 已确认的业务规则

### 10.1 名称清理

- 创建和修改时均去除名称首尾空格。
- 去除首尾空格后不能为空。
- 不自动删除或合并名称内部空格。

例如：

```text
"  TCP  "
```

保存为：

```text
"TCP"
```

但：

```text
"操作 系统"
```

内部空格保持不变。

### 10.2 名称长度

- 根节点名称最多 50 个字符。
- 普通知识点名称最多 100 个字符。
- DTO Validation 统一限制输入不超过 100 个字符。
- Service 在创建或修改根节点时额外执行 50 字符业务限制。

根节点限制为 50 的原因是：

```text
knowledge_point.name  VARCHAR(100)
question.subject      VARCHAR(50)
```

根节点名称同时承担科目名称作用，必须能够安全同步到 `question.subject`。

### 10.3 名称唯一性

- 同一个父节点下面不能存在两个同名知识点。
- 所有根节点视为同一组，根节点之间不能重名。
- 不同父节点下面允许同名知识点。
- 创建和改名使用相同的重名规则。
- 根据当前 `utf8mb4_unicode_ci` 排序规则，英文大小写不作为不同名称。

因此同级的 `TCP` 和 `tcp` 被视为重名。

### 10.4 创建根节点

请求中：

```json
{
  "name": "408",
  "parentId": null
}
```

表示创建根节点。

根节点名称必须满足：

- 非空。
- 最多 50 个字符。
- 不与现有根节点重名。

### 10.5 创建子节点

请求中：

```json
{
  "name": "TCP",
  "parentId": 3
}
```

表示在 ID 为 3 的父节点下创建子节点。

父节点必须真实存在。

### 10.6 节点的知识树归属

根节点代表一棵知识树，同时代表科目边界。

节点创建后，其所属根节点不可通过移动操作改变。

### 10.7 同一知识树内移动

普通节点允许在同一根节点下调整父节点。

移动前必须验证：

- 目标节点存在。
- 新父节点存在。
- 新父节点不是目标节点自身。
- 新父节点不是目标节点的后代。
- 移动后根节点不发生变化。
- 新父节点下不存在同名节点。

### 10.8 禁止跨根节点移动

不允许把一个节点或子树从一个根节点移动到另一个根节点。

例如不允许：

```text
408 → TCP
```

移动到：

```text
数学 → TCP
```

F-003 不实现自动迁移错题或自动调整 `subject`。

### 10.9 根节点移动限制

- 根节点不能变成任何其他节点的子节点。
- 普通节点不能通过把 `parentId` 修改为 `null` 升级为新根节点。
- 根节点只允许改名或在满足删除条件时删除。

### 10.10 自引用与循环引用

不允许节点把自己设为父节点。

不允许把节点移动到自己的任何后代下面。

例如：

```text
TCP
└── 拥塞控制
```

不允许再把 `TCP` 移动到 `拥塞控制` 下面。

### 10.11 根节点改名

允许根节点改名。

根节点改名时，Service 必须在同一个事务中：

1. 校验新名称。
2. 校验根节点重名。
3. 查询 `subject` 等于旧根节点名称的 Question。
4. 将这些 Question 的 `subject` 修改为新根节点名称。
5. 修改根节点名称。
6. 提交事务。

任意一步失败时，整个操作回滚。

### 10.12 严格删除

只有同时满足以下条件的知识点才允许删除：

- 没有子节点。
- 没有任何错题引用。

删除规则：

- 不递归删除子树。
- 不自动解除错题关联。
- 没有子节点且没有错题引用的根节点允许删除。
- 知识点不存在时返回 404。
- 存在子节点或错题引用时返回 409。

数据库的 `ON DELETE RESTRICT` 继续作为第二层数据完整性保护。

### 10.13 知识树查询

- 一次返回完整知识树。
- 不分页。
- 最外层返回所有根节点。
- 每个节点返回嵌套的 `children`。
- 叶子节点返回 `children: []`，不返回 `null`。
- 根节点和同级子节点统一按照 `id` 升序排列。
- 改名不改变显示顺序。

---

## 11. REST API 设计

### 11.1 接口总览

| 功能 | 方法 | 路径 | 成功状态 |
| --- | --- | --- | --- |
| 查询完整知识树 | GET | `/api/knowledge-points/tree` | 200 |
| 新增知识点 | POST | `/api/knowledge-points` | 201 |
| 修改名称或父节点 | PUT | `/api/knowledge-points/{id}` | 200 |
| 删除知识点 | DELETE | `/api/knowledge-points/{id}` | 200 |

F-003 不提供单独的：

```http
GET /api/knowledge-points/{id}
```

原因是完整知识树已经包含当前编辑场景需要的 `id`、`name`、`parentId` 和层级关系。

### 11.2 查询完整知识树

请求：

```http
GET /api/knowledge-points/tree
```

成功响应：

```http
200 OK
```

```json
[
  {
    "id": 1,
    "name": "408",
    "parentId": null,
    "children": [
      {
        "id": 2,
        "name": "计算机网络",
        "parentId": 1,
        "children": [
          {
            "id": 3,
            "name": "TCP",
            "parentId": 2,
            "children": []
          }
        ]
      }
    ]
  }
]
```

### 11.3 新增知识点

请求：

```http
POST /api/knowledge-points
Content-Type: application/json
```

请求体：

```json
{
  "name": "TCP",
  "parentId": 2
}
```

成功响应：

```http
201 Created
```

```json
{
  "id": 3,
  "name": "TCP",
  "parentId": 2
}
```

### 11.4 修改知识点

请求：

```http
PUT /api/knowledge-points/3
Content-Type: application/json
```

请求体需要提交完整可编辑状态：

```json
{
  "name": "TCP/IP",
  "parentId": 2
}
```

成功响应：

```http
200 OK
```

```json
{
  "id": 3,
  "name": "TCP/IP",
  "parentId": 2
}
```

调用方应始终提交 `parentId` 字段：

- 根节点保持 `null`。
- 普通节点提交当前或新的同树父节点 ID。

### 11.5 删除知识点

请求：

```http
DELETE /api/knowledge-points/3
```

成功响应：

```http
200 OK
```

```json
{
  "message": "知识点删除成功"
}
```

---

## 12. DTO 设计

建议 DTO：

### 12.1 CreateKnowledgePointRequest

字段：

- `String name`
- `Long parentId`

用途：创建根节点或子节点。

### 12.2 UpdateKnowledgePointRequest

字段：

- `String name`
- `Long parentId`

用途：修改名称或在同一知识树内调整父节点。

虽然当前字段与创建请求相同，仍使用独立 DTO，以保持创建和修改 API 合同的语义边界。

### 12.3 KnowledgePointResponse

字段：

- `Long id`
- `String name`
- `Long parentId`

用途：创建和修改成功响应。

### 12.4 KnowledgePointTreeNodeResponse

字段：

- `Long id`
- `String name`
- `Long parentId`
- `List<KnowledgePointTreeNodeResponse> children`

用途：嵌套知识树响应。

### 12.5 MessageResponse

字段：

- `String message`

用途：删除成功响应。

### 12.6 ApiErrorResponse

字段：

- `Instant timestamp`
- `int status`
- `String code`
- `String message`
- `String path`
- `Map<String, String> fieldErrors`（仅参数校验失败时使用）

F-003 不增加通用 `ApiResponse<T>` 成功包装层。成功接口直接返回对应 DTO。

---

## 13. HTTP 状态码与错误码

### 13.1 状态码规则

| 场景 | HTTP 状态码 |
| --- | ---: |
| 查询成功 | 200 |
| 创建成功 | 201 |
| 修改成功 | 200 |
| 删除成功 | 200 |
| Validation 失败 | 400 |
| 请求体不是合法 JSON | 400 |
| 目标知识点或父节点不存在 | 404 |
| 名称冲突 | 409 |
| 自引用或循环引用 | 409 |
| 非法跨树移动 | 409 |
| 根节点或普通节点非法改变根归属 | 409 |
| 删除受子节点或错题引用限制 | 409 |
| 数据库完整性冲突 | 409 |

### 13.2 错误码建议

| 错误码 | 状态 | 含义 |
| --- | ---: | --- |
| `VALIDATION_FAILED` | 400 | 请求字段校验失败 |
| `MALFORMED_REQUEST_BODY` | 400 | JSON 请求体无法解析 |
| `KNOWLEDGE_POINT_NOT_FOUND` | 404 | 目标或父知识点不存在 |
| `KNOWLEDGE_POINT_NAME_CONFLICT` | 409 | 同级或根节点名称冲突 |
| `KNOWLEDGE_POINT_SELF_PARENT` | 409 | 节点把自己设为父节点 |
| `KNOWLEDGE_POINT_CYCLE` | 409 | 调整层级会形成环 |
| `KNOWLEDGE_POINT_CROSS_TREE_MOVE_FORBIDDEN` | 409 | 跨根节点移动被禁止 |
| `KNOWLEDGE_POINT_ROOT_CHANGE_FORBIDDEN` | 409 | 根节点或普通节点非法改变根归属 |
| `KNOWLEDGE_POINT_HAS_CHILDREN` | 409 | 存在子节点，不能删除 |
| `KNOWLEDGE_POINT_IN_USE` | 409 | 被错题引用，不能删除 |
| `DATA_INTEGRITY_CONFLICT` | 409 | 数据库完整性约束冲突 |

### 13.3 业务冲突响应示例

```json
{
  "timestamp": "2026-08-31T17:20:00Z",
  "status": 409,
  "code": "KNOWLEDGE_POINT_NAME_CONFLICT",
  "message": "同一父节点下已存在同名知识点",
  "path": "/api/knowledge-points"
}
```

### 13.4 Validation 响应示例

```json
{
  "timestamp": "2026-08-31T17:20:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "请求参数校验失败",
  "path": "/api/knowledge-points",
  "fieldErrors": {
    "name": "知识点名称不能为空"
  }
}
```

---

## 14. Repository 变更计划

### 14.1 KnowledgePointRepository

根据 Service 的实际查询需求增加派生查询方法，预计包括：

- 按 ID 升序查询全部知识点。
- 检查根节点名称是否存在。
- 更新时检查除当前节点外的根节点名称冲突。
- 检查指定父节点下的名称是否存在。
- 更新时检查除当前节点外的同级名称冲突。
- 检查某知识点是否存在直接子节点。

方法名应以 Spring Data JPA 当前版本能够正常解析为准，最终由自动化测试验证，不在本计划中强行绑定具体拼写。

### 14.2 QuestionRepository

为根节点改名和删除保护增加实际需要的查询：

- 根据 `subject` 查询相关 Question。
- 检查是否存在关联指定 KnowledgePoint 的 Question。

F-003 不增加与错题管理无关的其他查询方法。

---

## 15. Service 处理流程

### 15.1 创建流程

```text
接收请求
↓
清理名称首尾空格
↓
检查通用长度
↓
根据 parentId 判断根节点或子节点
↓
根节点执行 50 字符限制和根重名检查
子节点检查父节点存在及同级重名
↓
创建 KnowledgePoint
↓
Repository 保存
↓
转换为 KnowledgePointResponse
```

### 15.2 修改流程

```text
查询目标节点
↓
清理并校验名称
↓
判断目标是根节点还是普通节点
↓
校验 parentId 是否符合根归属规则
↓
普通节点执行自引用、循环和同树移动检查
↓
执行重名检查
↓
如果根节点改名，查询并同步 Question.subject
↓
修改 Entity
↓
事务提交
↓
返回 KnowledgePointResponse
```

### 15.3 删除流程

```text
查询目标节点
↓
检查是否存在直接子节点
↓
检查是否被 Question 引用
↓
全部通过后删除
↓
返回 MessageResponse
```

### 15.4 查询知识树流程

```text
一次按 id 升序查询全部 KnowledgePoint
↓
为每个 Entity 创建响应节点
↓
建立 id → 响应节点映射
↓
根据 parentId 将子节点加入父节点 children
↓
收集 parentId = null 的根节点
↓
返回完整树
```

知识树组装时间复杂度目标为：

```text
O(n)
```

不为当前个人使用规模引入路径字段、闭包表或专用树数据库。

---

## 16. 代码结构计划

预计新增或修改的主要结构：

```text
backend/src/main/java/com/wrongquestion/backend
├── common
│   └── exception
│       ├── ApiErrorResponse.java
│       └── GlobalExceptionHandler.java
│
├── knowledge
│   ├── controller
│   │   └── KnowledgePointController.java
│   ├── dto
│   │   ├── CreateKnowledgePointRequest.java
│   │   ├── UpdateKnowledgePointRequest.java
│   │   ├── KnowledgePointResponse.java
│   │   ├── KnowledgePointTreeNodeResponse.java
│   │   └── MessageResponse.java
│   ├── entity
│   │   └── KnowledgePoint.java
│   ├── exception
│   │   ├── KnowledgePointNotFoundException.java
│   │   └── KnowledgePointConflictException.java
│   ├── repository
│   │   └── KnowledgePointRepository.java
│   └── service
│       └── KnowledgePointService.java
│
└── question
    ├── entity
    │   └── Question.java
    └── repository
        └── QuestionRepository.java
```

最终文件名可以根据实现中的清晰度进行小幅调整，但不得改变已确认的分层职责。

---

## 17. 依赖变更

在 `pom.xml` 中增加 Validation 所需依赖：

```text
spring-boot-starter-validation
```

不增加：

- MapStruct。
- Lombok。
- H2。
- Testcontainers。
- Flyway。
- 其他当前没有实际用途的依赖。

---

## 18. Transaction 设计

### 18.1 写事务

以下 Service 方法使用 `@Transactional`：

- 创建知识点。
- 修改知识点。
- 删除知识点。

### 18.2 只读事务

知识树查询使用：

```text
@Transactional(readOnly = true)
```

### 18.3 根节点改名事务

根节点名称和相关错题 `subject` 必须在同一事务中更新。

不允许：

```text
先提交根节点改名
↓
再单独更新 Question.subject
```

否则第二步失败时会产生数据不一致。

---

## 19. 测试方案

### 19.1 测试原则

F-003 同时使用：

- Service 单元测试。
- Controller 真实 MySQL 集成测试。

单元测试重点验证业务分支，集成测试重点验证真实完整链路。

不要求同一业务规则在两个测试层中完全重复。

### 19.2 Service 单元测试

使用 JUnit 和 Mockito，模拟 Repository。

建议测试类：

```text
KnowledgePointServiceTest
```

至少覆盖：

- 创建根节点成功。
- 创建子节点成功。
- 名称首尾空格被清理。
- 根节点超过 50 字符被拒绝。
- 普通节点超过 100 字符被 Validation 或 Service 拒绝。
- 根节点重名被拒绝。
- 同级子节点重名被拒绝。
- 不同父节点同名允许。
- 父节点不存在被拒绝。
- 同一知识树内移动成功。
- 自引用被拒绝。
- 循环引用被拒绝。
- 跨根节点移动被拒绝。
- 根节点变成子节点被拒绝。
- 子节点升级为根节点被拒绝。
- 根节点改名时相关 Question 的 `subject` 被同步修改。
- 删除普通叶子节点成功。
- 存在子节点时删除被拒绝。
- 被 Question 引用时删除被拒绝。
- 删除不存在的知识点被拒绝。
- 查询结果正确组装为嵌套树。
- 根节点和同级节点按照 ID 升序排列。

### 19.3 Controller 集成测试

继续使用当前项目的真实 MySQL 测试方式：

```text
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
```

建议测试类：

```text
KnowledgePointControllerTest
```

至少覆盖代表性的完整链路：

- `POST` 创建根节点返回 201。
- `POST` 创建子节点返回 201。
- `GET` 返回正确嵌套树和空 `children` 数组。
- `PUT` 改名返回 200。
- `PUT` 同树移动返回 200。
- 根节点改名后真实数据库中的 `Question.subject` 同步变化。
- `DELETE` 成功返回 200 和 `知识点删除成功`。
- 空名称返回 400。
- 超长名称返回 400 或对应业务错误。
- 知识点不存在返回 404。
- 重名返回 409。
- 跨树移动返回 409。
- 删除有子节点的知识点返回 409。
- 删除被错题引用的知识点返回 409。
- 错误响应包含约定的 `status`、`code`、`message` 和 `path`。
- Validation 错误包含 `fieldErrors`。

### 19.4 原有测试保护

以下原有测试必须继续通过：

- `BackendApplicationTests`。
- `HealthControllerTest`。
- `QuestionRepositoryTest`。
- `KnowledgePointRepositoryTest`。

### 19.5 测试数据清理

Controller 集成测试继续使用事务回滚，避免测试数据长期污染开发数据库。

### 19.6 最终测试命令

Windows PowerShell：

```powershell
.\mvnw test
```

最终要求：

```text
BUILD SUCCESS
```

---

## 20. 手工 API 验证

自动化测试通过后，启动后端，至少实际走通一次：

```text
创建临时根节点
↓
创建子节点
↓
查询知识树
↓
修改名称
↓
在同一知识树内移动节点
↓
删除没有引用的叶子节点
↓
清理临时数据
```

同时验证代表性失败场景：

- 根节点或同级名称重名。
- 跨知识树移动。
- 删除存在子节点的知识点。
- 删除被错题引用的知识点。
- 根节点改名后相关错题 `subject` 同步更新。

手工验证可使用 Postman、IDEA HTTP Client 或其他 HTTP 客户端，不限定具体工具。

### 20.1 实际验证结果

2026-09-01 已在用户本地 Java 21.0.12 与 MySQL 9.6 环境完成验证。

自动化测试结果：

```text
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

其中：

- `KnowledgePointServiceTest`：21 个测试；
- `KnowledgePointControllerTest`：11 个真实 MySQL 集成测试；
- 原有应用、Repository 和健康检查测试：4 个；
- Controller 测试数据通过事务回滚。

手工验证已经覆盖：

- 创建根节点和子节点；
- 查询嵌套知识树；
- 修改名称并在同一知识树内移动；
- 根节点及同级重名；
- 跨根节点移动；
- 删除存在子节点的知识点；
- 删除被错题引用的知识点；
- 根节点改名同步 `question.subject`；
- 删除成功返回 `200 OK` 和 JSON 消息；
- 清理全部临时验证数据。

---

## 21. 文档变更计划

### 21.1 ADR 处理

ADR-001 已与真实代码一致，F-003 不新增或修改 ADR。

### 21.2 更新 database-design.md

- 修正 F-002 状态和过期下一步。
- 明确数据库维护时间字段。
- 记录根节点名称最多 50 字符的业务限制。
- 记录禁止跨根节点移动。
- 记录根节点改名同步 `question.subject`。
- 记录严格删除规则由 Service 预检查、数据库约束兜底。

### 21.3 更新 api-design.md

- 记录四个知识点 REST API。
- 记录请求和响应 JSON。
- 记录 HTTP 状态码和错误结构。

如果该文档当前为空，则在本 Feature 中补充知识点 API 章节。

### 21.4 更新 project-status.md

F-003 开始时更新为 Active；F-003 完成时更新为 Completed。

完成时记录：

- 新增的业务层与 API。
- 最终测试数量和结果。
- 实际项目结构。
- 下一阶段仍需单独规划。

### 21.5 更新本 Feature Plan

实现过程中按任务清单更新进度。

F-003 完成后：

```text
docs/plans/active/F-003-knowledge-point-management.md
```

移动为：

```text
docs/plans/completed/F-003-knowledge-point-management.md
```

---

## 22. 实施顺序

### 阶段 0：开发前检查

- [x] 检查 `git status`。
- [x] 确认实现前工作区无意外改动。
- [x] 确认处于 `feature/F-003-knowledge-point-management`。
- [x] 确认 MySQL 服务和 `DB_PASSWORD` 可用。

### 阶段 1：核对技术决策记录

- [x] 确认 ADR-001 已与 JPA/Hibernate 真实实现一致。
- [x] 确认 F-003 不需要新增 ADR。

### 阶段 2：准备依赖与数据访问查询

- [x] 添加 `spring-boot-starter-validation`。
- [x] 为 `KnowledgePointRepository` 增加必要查询。
- [x] 为 `QuestionRepository` 增加必要查询。

### 阶段 3：建立 DTO 与异常响应

- [x] 创建请求 DTO。
- [x] 创建普通响应 DTO。
- [x] 创建知识树响应 DTO。
- [x] 创建删除成功响应 DTO。
- [x] 创建统一错误响应 DTO。
- [x] 创建知识点业务异常。
- [x] 创建全局 Exception Handler。

### 阶段 4：实现 Service

- [x] 实现名称清理和长度规则。
- [x] 实现根节点与同级重名检查。
- [x] 实现创建逻辑。
- [x] 实现同树移动检查。
- [x] 实现自引用和循环检查。
- [x] 实现根节点移动限制。
- [x] 实现根节点改名和 `subject` 同步。
- [x] 实现严格删除检查。
- [x] 实现 O(n) 知识树组装。
- [x] 添加正确事务边界。

### 阶段 5：实现 Controller

- [x] 实现 `GET /api/knowledge-points/tree`。
- [x] 实现 `POST /api/knowledge-points`。
- [x] 实现 `PUT /api/knowledge-points/{id}`。
- [x] 实现 `DELETE /api/knowledge-points/{id}`。
- [x] 按计划设置状态码和响应 DTO。

### 阶段 6：自动化测试

- [x] 编写 Service 单元测试。
- [x] 编写 Controller 真实 MySQL 集成测试。
- [x] 在本地执行并确认新增测试通过。
- [x] 执行全量 Maven 测试。
- [x] 确认所有测试无失败和错误。

### 阶段 7：手工验证

- [x] 启动后端。
- [x] 走通四个知识点 API。
- [x] 验证主要失败场景。
- [x] 验证根节点改名事务。
- [x] 清理临时验证数据。

### 阶段 8：文档与 Git 收尾

- [x] 更新 `api-design.md`。
- [x] 更新 `database-design.md`。
- [x] 更新 `project-status.md`。
- [x] 更新本 Feature Plan 状态。
- [x] 再次执行全量测试。
- [x] 检查 `git diff` 和 `git status`。
- [x] 提交 F-003 代码与文档。
- [x] 推送功能分支。
- [x] 完成后将 Plan 移入 `plans/completed`。

---

## 23. 验收标准

### 23.1 功能验收

- [x] 四个知识点接口均可正常调用。
- [x] 查询接口返回正确的嵌套树。
- [x] 创建根节点和子节点成功。
- [x] 名称清理、长度和重名规则生效。
- [x] 同一知识树内移动成功。
- [x] 自引用和循环引用被拒绝。
- [x] 跨根节点移动被拒绝。
- [x] 根节点不能变成子节点。
- [x] 普通节点不能升级为根节点。
- [x] 根节点改名同步相关错题 `subject`。
- [x] 严格删除规则生效。
- [x] 删除成功返回 200 和 JSON 成功消息。

### 23.2 API 验收

- [x] 创建返回 201。
- [x] 查询返回 200。
- [x] 修改返回 200。
- [x] 删除返回 200。
- [x] Validation 失败返回 400。
- [x] 目标不存在返回 404。
- [x] 业务冲突返回 409。
- [x] 错误响应字段与本计划一致。
- [x] Controller 不直接返回 Entity。

### 23.3 代码验收

- [x] Controller 不直接调用 Repository。
- [x] Validation 只负责通用格式校验。
- [x] Service 统一负责业务规则。
- [x] 写操作具有明确事务边界。
- [x] 根节点改名与错题 `subject` 更新在同一事务。
- [x] 不存在没有真实用途的 Service 接口或 Mapper 框架。
- [x] 不修改数据库表结构。
- [x] 项目可以正常编译和启动。

### 23.4 测试验收

- [x] Service 单元测试覆盖约定业务分支。
- [x] Controller 真实 MySQL 集成测试覆盖代表性链路。
- [x] 原有测试继续通过。
- [x] `.\mvnw.cmd test` 输出 `BUILD SUCCESS`。
- [x] 测试数据通过事务回滚。

### 23.5 文档验收

- [x] 已确认 ADR-001 与真实代码一致，无需新增 ADR。
- [x] `database-design.md` 过期内容已修正。
- [x] 知识点 API 已记录。
- [x] `project-status.md` 与当前实现状态一致。
- [x] 本 Feature Plan 的状态和任务清单已同步。

### 23.6 Git 验收

- [x] 所有 F-003 相关代码和文档已提交。
- [x] 最终工作区干净。
- [x] 功能分支已推送。
- [x] 实现、测试结果和文档状态一致。

---

## 24. 风险与处理

### 24.1 Service 预检查与数据库约束重复

Service 预检查用于返回清晰业务错误，数据库 UNIQUE、FOREIGN KEY 和 RESTRICT 用于兜底。

两者不是重复浪费，而是不同层次的数据保护。

### 24.2 根节点名称与 subject 重复保存

当前 F-002 已确定 `question.subject` 为字符串，同时根节点代表科目。

F-003 通过以下规则降低不一致风险：

- 根节点名称最多 50 字符。
- 根节点改名同步 `question.subject`。
- 同步操作使用事务。
- 禁止跨根节点移动。

F-003 不重新设计 Subject 数据模型。

### 24.3 多次查询或 N+1

知识树查询应一次读取全部知识点，再在 Java 中组装。

实现时不得直接递归查询每个节点的子节点。

### 24.4 真实 MySQL 测试依赖本地环境

当前项目已经采用真实 MySQL 集成测试，本 Feature 延续现状。

如果未来需要自动 CI，再单独评估 Testcontainers 或独立测试数据库，不在 F-003 提前引入。

### 24.5 并发冲突

当前 MVP 为单用户，Service 预检查足够支撑正常使用。

数据库唯一约束和外键继续处理极端竞争情况下的数据完整性。

F-003 不增加分布式锁或复杂并发控制。

---

## 25. 已确认决策汇总

| 决策项 | 结论 |
| --- | --- |
| F-003 主目标 | 完整知识点管理业务层与 REST API |
| API 数量 | 4 个 |
| 查询形式 | 完整嵌套树 |
| 分页 | 不使用 |
| 排序 | 同级按 ID 升序 |
| 根节点最大长度 | 50 字符 |
| 普通节点最大长度 | 100 字符 |
| 英文大小写 | 根据当前数据库排序规则视为不区分 |
| 跨树移动 | 禁止 |
| 根节点变为子节点 | 禁止 |
| 子节点升级为根节点 | 禁止 |
| 根节点改名 | 允许，并同步 Question.subject |
| 删除 | 仅无子节点且无错题引用时允许 |
| 删除成功响应 | 200 + JSON 消息 |
| Service 结构 | 单个具体类，不建立接口/Impl |
| DTO | Java record |
| Entity 是否直接暴露 | 否 |
| 事务 | Service 写事务，查询只读事务 |
| 异常处理 | 统一 Exception Handler |
| 数据库表变更 | 无 |
| 测试 | Service 单元测试 + Controller 真实 MySQL 集成测试 |
| 手工验证 | 必须 |
| ADR | ADR-001 已与真实实现一致，不新增 ADR |

---

## 26. 完成定义

F-003 只有在以下条件全部满足后才能标记为 Completed：

1. 本计划范围内的四个 API 全部实现。
2. 所有已确认业务规则生效。
3. Service、DTO、Validation、Transaction、Controller 和 Exception Handler 职责清晰。
4. Service 单元测试和 Controller 真实 MySQL 集成测试全部通过。
5. 原有测试未被破坏。
6. 全量 Maven 测试为 `BUILD SUCCESS`。
7. 手工 API 验证完成并清理临时数据。
8. API、数据库设计和项目状态文档已同步。
9. 代码、测试结果和文档一致。
10. Git 工作区干净，功能分支已推送。
11. Feature Plan 已从 `plans/active` 移入 `plans/completed`。

当前没有未确认的 F-003 业务或技术决策。
