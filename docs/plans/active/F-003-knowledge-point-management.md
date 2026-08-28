# F-003：知识点管理业务与 REST API

## 1. Feature 基本信息

* Feature ID：F-003
* Feature Name：Knowledge Point Management
* 中文名称：知识点管理业务与 REST API
* 状态：Planned
* 前置 Feature：F-002 Database Design
* 主要对象：`KnowledgePoint`
* 目标：在 F-002 已完成数据库设计、Entity 和 Repository 基础准备的前提下，实现完整的知识点管理后端能力。

---

# 2. 背景

F-002 已完成知识点相关的数据层准备，包括：

* `knowledge_point` 数据表
* `KnowledgePoint` Entity
* `KnowledgePointRepository`
* `Question` 与知识点之间的关联关系
* 对应的 Repository 基础测试

目前系统已经能够通过 JPA 与数据库中的知识点数据交互，但尚未形成完整的业务层和 REST API。

外部调用方目前无法通过 HTTP API 完成：

* 查询知识点
* 新增知识点
* 修改知识点
* 调整知识点层级
* 删除知识点

因此，F-003 的主要任务是：

> 在现有数据层基础上，为 KnowledgePoint 建立完整的 DTO → Controller → Service → Repository → Database 调用链，并实现知识点树相关业务规则。

---

# 3. Feature 目标

F-003 完成后，后端应能够提供完整的知识点管理能力。

包括：

1. 查询完整知识点树
2. 新增根知识点或子知识点
3. 修改知识点名称
4. 调整知识点的父节点
5. 删除满足条件的知识点
6. 防止产生非法的知识点树结构
7. 对请求参数进行校验
8. 对业务异常进行统一处理
9. 返回明确且一致的 HTTP 状态码
10. 为核心业务逻辑和 API 提供自动化测试

最终形成如下基本调用链：

用户 / 前端

↓

HTTP Request

↓

Controller

↓

DTO

↓

Service

↓

Repository

↓

MySQL

结果再通过相反方向返回给调用方。

---

# 4. Feature 范围

## 4.1 本 Feature 实现

F-003 实现以下内容：

* KnowledgePoint REST API
* KnowledgePoint DTO
* KnowledgePoint Service
* KnowledgePoint 业务规则校验
* KnowledgePoint Repository 所需补充查询
* QuestionRepository 中知识点引用检查能力
* Bean Validation 参数校验
* 业务异常
* 全局异常处理
* API 错误响应结构
* Service 层测试
* Controller / API 层测试
* 项目文档同步更新

---

## 4.2 本 Feature 不实现

以下内容不属于 F-003：

* Question 错题增删改查
* ReviewRecord 复习记录管理
* 滚动复习算法
* 掌握率计算
* OCR
* 图片上传
* 用户注册
* 用户登录
* 权限控制
* 前端页面
* 搜索功能
* 知识点分页
* 批量删除
* 批量移动
* 知识点拖拽前端交互

这些功能将在后续 Feature 中独立处理。

---

# 5. 核心业务模型

知识点采用树状结构。

例如：

408

├── 数据结构

│   ├── 树

│   │   ├── 二叉树

│   │   └── AVL 树

│   └── 图

│       ├── 最短路径

│       └── 拓扑排序

└── 计算机组成原理

```
├── Cache

└── 虚拟存储器
```

数据库中并不直接存储一棵树，而是通过每条知识点记录中的父节点关系表示。

例如：

| id | name | parent_id |
| -- | ---- | --------- |
| 1  | 408  | null      |
| 2  | 数据结构 | 1         |
| 3  | 树    | 2         |
| 4  | 二叉树  | 3         |

Service 层负责根据这些记录组装树结构。

---

# 6. REST API 设计

F-003 提供以下 5 个核心 API。

---

## 6.1 查询知识点树

### Request

`GET /api/knowledge-points/tree`

### 功能

查询数据库中的所有知识点，并按照父子关系组装为完整知识点树。

### 成功响应

HTTP：

`200 OK`

响应示例：

```json
[
  {
    "id": 1,
    "name": "数据结构",
    "children": [
      {
        "id": 2,
        "name": "树",
        "children": [
          {
            "id": 3,
            "name": "二叉树",
            "children": []
          }
        ]
      }
    ]
  }
]
```

### 空数据情况

如果数据库中不存在任何知识点：

```json
[]
```

仍返回：

`200 OK`

---

# 7. 新增知识点

## 7.1 Request

`POST /api/knowledge-points`

请求体：

```json
{
  "name": "二叉树",
  "parentId": 8
}
```

### 字段

`name`

知识点名称。

`parentId`

父知识点 ID。

如果：

```json
{
  "name": "数据结构",
  "parentId": null
}
```

表示创建根知识点。

---

## 7.2 成功响应

HTTP：

`201 Created`

Body：

```json
{
  "id": 15,
  "name": "二叉树",
  "parentId": 8
}
```

---

## 7.3 业务规则

新增知识点必须满足：

1. `name` 合法
2. `parentId != null` 时，父知识点必须存在
3. 同一父节点下不能存在同名知识点
4. 根节点之间不能存在同名知识点
5. 校验通过后才允许保存

例如：

数据结构

├── 树

└── 树

不允许。

但是：

数据结构

└── 树

操作系统

└── 树

允许。

原因是两个“树”的父节点不同。

---

# 8. 修改知识点名称

## 8.1 Request

`PATCH /api/knowledge-points/{id}/name`

例如：

`PATCH /api/knowledge-points/15/name`

请求体：

```json
{
  "name": "平衡二叉树"
}
```

---

## 8.2 成功响应

HTTP：

`200 OK`

Body：

```json
{
  "id": 15,
  "name": "平衡二叉树",
  "parentId": 8
}
```

---

## 8.3 业务规则

修改名称时必须满足：

1. 当前知识点必须存在
2. 新名称必须合法
3. 新名称不能与当前父节点下的其他知识点重名
4. 如果当前节点是根节点，则不能与其他根节点重名

如果：

`二叉树 → 二叉树`

即新名称与当前名称完全相同，则：

* 不视为错误
* 不返回重名冲突
* 可以直接视为操作成功

查重时必须排除当前知识点自身。

---

# 9. 移动知识点

## 9.1 Request

`PATCH /api/knowledge-points/{id}/parent`

例如：

`PATCH /api/knowledge-points/20/parent`

请求体：

```json
{
  "parentId": 12
}
```

表示：

将 ID 为 20 的知识点移动到 ID 为 12 的知识点下面。

---

## 9.2 移动为根节点

请求：

```json
{
  "parentId": null
}
```

表示将该知识点移动为根节点。

---

## 9.3 成功响应

HTTP：

`200 OK`

Body：

```json
{
  "id": 20,
  "name": "DFS",
  "parentId": 12
}
```

---

## 9.4 业务规则

移动知识点时必须检查：

1. 当前知识点必须存在
2. `parentId != null` 时，新父节点必须存在
3. 不能把自己设置为自己的父节点
4. 不能把当前知识点移动到自己的子孙节点下面
5. 移动后不能与新位置的兄弟节点重名
6. 移动为根节点后不能与其他根节点重名

---

## 9.5 防止形成环

例如当前结构：

数据结构

└── 树

```
└── 二叉树
```

不允许把：

`数据结构`

移动到：

`二叉树`

下面。

否则会形成：

数据结构

↓

树

↓

二叉树

↓

数据结构

形成循环结构。

因此移动前必须执行环检测。

---

## 9.6 环检测基本思路

假设：

* 当前需要移动的节点为 A
* 新父节点为 B

从 B 开始不断向父节点方向查找：

B

↓

B.parent

↓

B.parent.parent

↓

……

如果过程中遇到 A：

说明 B 本身就是 A 的后代。

此时：

A → B

的移动必须被拒绝。

如果最终到达根节点，并始终没有遇到 A：

则不存在这种循环关系。

具体实现必须依据当前 `KnowledgePoint` Entity 的父节点映射方式确定。

---

## 9.7 原地移动

如果：

当前：

`parentId = 10`

请求仍然为：

`parentId = 10`

则：

* 不视为错误
* 可以直接视为成功
* 不需要产生业务冲突

---

# 10. 删除知识点

## 10.1 Request

`DELETE /api/knowledge-points/{id}`

例如：

`DELETE /api/knowledge-points/15`

---

## 10.2 成功响应

HTTP：

`204 No Content`

不返回 JSON Body。

`204` 本身即表示：

> 删除操作已经成功完成，并且没有需要返回的响应正文。

---

## 10.3 删除规则

删除前必须检查：

1. 当前知识点必须存在
2. 当前知识点不能存在子知识点
3. 当前知识点不能被任何 Question 引用

只有全部满足时才能删除。

---

## 10.4 存在子节点

例如：

树

└── 二叉树

不能直接删除：

`树`

必须先处理其子节点。

F-003 不提供级联删除知识点树功能。

---

## 10.5 被 Question 引用

如果存在：

Question

↓

KnowledgePoint：二叉树

则不能删除：

`二叉树`

避免 Question 对应的知识点关系失效。

---

# 11. DTO 设计

F-003 暂定建立以下 DTO。

---

## 11.1 CreateKnowledgePointRequest

用途：

新增知识点请求。

字段：

* `name`
* `parentId`

示例：

```json
{
  "name": "二叉树",
  "parentId": 8
}
```

---

## 11.2 UpdateKnowledgePointNameRequest

用途：

修改知识点名称。

字段：

* `name`

示例：

```json
{
  "name": "平衡二叉树"
}
```

---

## 11.3 MoveKnowledgePointRequest

用途：

修改知识点父节点。

字段：

* `parentId`

示例：

```json
{
  "parentId": 12
}
```

`parentId = null` 表示移动为根节点。

---

## 11.4 KnowledgePointResponse

用途：

新增、改名、移动成功后的响应。

字段暂定：

* `id`
* `name`
* `parentId`

示例：

```json
{
  "id": 15,
  "name": "二叉树",
  "parentId": 8
}
```

---

## 11.5 KnowledgePointTreeResponse

用途：

知识点树查询响应。

字段：

* `id`
* `name`
* `children`

其中：

`children`

本身是：

`KnowledgePointTreeResponse`

集合。

因此可以形成递归树结构。

---

## 11.6 ErrorResponse

用途：

统一错误响应。

字段暂定：

* `status`
* `message`

示例：

```json
{
  "status": 409,
  "message": "同一父节点下已存在同名知识点"
}
```

F-003 暂时不引入以下字段：

* timestamp
* path
* errorCode
* traceId
* details

如后续项目复杂度增加，再独立扩展。

---

# 12. Entity 与 DTO 边界

F-003 不直接将 `KnowledgePoint` Entity 暴露为 API 请求或响应模型。

原则：

Entity：

> 用于 Java 与数据库之间的数据映射。

DTO：

> 用于 Controller 与外部调用方之间的数据传输。

例如新增知识点只需要：

* name
* parentId

而 Entity 可能还包含：

* id
* parent
* createdAt
* updatedAt
* 其他数据库相关字段

因此 API 层使用 DTO，避免数据库结构直接泄露到接口层。

---

# 13. Service 设计

建立：

`KnowledgePointService`

负责知识点核心业务逻辑。

概念方法：

```text
getTree()

create(...)

rename(...)

move(...)

delete(...)
```

实际 Java 方法签名在编码阶段根据当前 Entity、DTO 和代码风格确定。

---

# 14. Service 职责

Controller 只负责：

* 接收请求
* 参数绑定
* 调用 Service
* 返回 HTTP Response

Controller 不负责：

* 判断知识点是否存在
* 判断是否重名
* 判断是否能够删除
* 判断是否形成环
* 直接进行数据库业务操作

这些均属于 Service。

---

# 15. getTree()

主要流程：

1. Repository 查询全部 KnowledgePoint
2. 根据父子关系组织数据
3. 找出根节点
4. 为每个节点构造 children
5. 转换为 `KnowledgePointTreeResponse`
6. 返回完整知识点树

数据库为空时返回空数组。

---

# 16. create()

主要流程：

1. 校验请求参数
2. 判断 `parentId`
3. 如果存在父节点 ID，则查询父节点
4. 父节点不存在时抛出资源不存在异常
5. 检查同级重名
6. 构建 KnowledgePoint Entity
7. Repository 保存
8. 转换为 `KnowledgePointResponse`
9. 返回结果

---

# 17. rename()

主要流程：

1. 根据 ID 查询当前知识点
2. 不存在则抛出资源不存在异常
3. 校验新名称
4. 如果名称未发生变化，可以直接成功返回
5. 检查同级其他节点是否重名
6. 修改名称
7. 保存
8. 转换为 `KnowledgePointResponse`
9. 返回结果

查重时必须排除当前知识点自身。

---

# 18. move()

主要流程：

1. 查询当前知识点
2. 不存在则抛出资源不存在异常
3. 判断目标 `parentId`
4. 如果目标父节点不为空，则查询目标父节点
5. 新父节点不存在则抛出资源不存在异常
6. 检查是否将自己设为父节点
7. 检查是否形成环
8. 检查移动后的同级重名
9. 如果父节点实际没有发生变化，可以直接视为成功
10. 修改 parent
11. 保存
12. 转换为 `KnowledgePointResponse`
13. 返回

---

# 19. delete()

主要流程：

1. 根据 ID 查询知识点
2. 不存在则抛出资源不存在异常
3. 检查是否存在子节点
4. 有子节点则拒绝删除
5. 检查 Question 是否引用该知识点
6. 存在引用则拒绝删除
7. Repository 删除
8. Controller 返回 `204 No Content`

---

# 20. Repository 设计

Spring Data JPA 已经提供的基础能力继续复用，包括：

* `findById(...)`
* `findAll()`
* `save(...)`
* `delete(...)`

F-003 只补充业务真正需要的查询。

---

# 21. KnowledgePointRepository 所需查询能力

主要需要解决：

### 21.1 普通节点查重

判断：

> 指定父节点下是否已经存在指定名称的知识点。

概念方法：

`existsByParentIdAndName(...)`

---

### 21.2 根节点查重

判断：

> 是否存在 parent 为空，并且名称相同的根节点。

概念方法：

`existsByParentIsNullAndName(...)`

由于数据库 `UNIQUE(parent_id, name)` 对 `NULL` 的处理不能完整保证根节点名称唯一，因此根节点重名必须在业务层主动检查。

---

### 21.3 查重时排除自身

修改名称或移动节点时需要：

> 查找同级同名节点，但忽略当前知识点本身。

概念方法：

`existsByParentIdAndNameAndIdNot(...)`

以及根节点对应查询：

`existsByParentIsNullAndNameAndIdNot(...)`

---

### 21.4 子节点存在检查

删除节点前需要判断：

> 当前节点是否仍然拥有子节点。

概念方法：

`existsByParentId(...)`

只关心是否存在，因此无需查询所有子节点。

---

# 22. QuestionRepository 所需查询能力

删除 KnowledgePoint 前，需要确认是否仍然存在 Question 引用当前知识点。

概念上需要：

> 检查是否存在关联指定 KnowledgePoint 的 Question。

最终 Repository 方法名称必须根据 F-002 当前 `Question` Entity 中实际的字段名称和映射关系确定。

不得脱离实际代码强行使用预设方法名。

---

# 23. Repository 实现原则

本 Feature Plan 中出现的：

* `existsByParentIdAndName`
* `existsByParentIsNullAndName`
* `existsByParentIdAndNameAndIdNot`
* `existsByParentIsNullAndNameAndIdNot`
* `existsByParentId`

均属于设计层面的概念名称。

正式编码前必须重新检查：

* `KnowledgePoint.java`
* `Question.java`
* `KnowledgePointRepository.java`
* `QuestionRepository.java`

根据真实实体属性确定最终 Spring Data JPA 方法名称。

不得为了符合 Feature Plan 而修改已有正确的数据模型。

Feature Plan 应服从现有项目实际代码和已确认架构。

---

# 24. 参数校验

F-003 使用 Bean Validation 对请求 DTO 进行基础参数校验。

重点校验：

`name`

不得：

* 为 null
* 为空字符串
* 全部由空白字符组成

预计使用：

`@NotBlank`

如现有数据库已经对名称长度存在约束，则 DTO 校验应与数据库约束保持一致。

是否增加：

`@Size`

需要根据当前 `knowledge_point.name` 字段长度确定。

不得自行假设长度。

---

# 25. HTTP 状态码设计

成功：

| 场景     | 状态码            |
| ------ | -------------- |
| 查询知识点树 | 200 OK         |
| 新增知识点  | 201 Created    |
| 修改名称   | 200 OK         |
| 移动知识点  | 200 OK         |
| 删除知识点  | 204 No Content |

失败：

| 场景                   | 状态码             |
| -------------------- | --------------- |
| 参数非法                 | 400 Bad Request |
| KnowledgePoint 不存在   | 404 Not Found   |
| 父 KnowledgePoint 不存在 | 404 Not Found   |
| 名称重复                 | 409 Conflict    |
| 自己作为自己的父节点           | 409 Conflict    |
| 移动形成环                | 409 Conflict    |
| 有子节点不能删除             | 409 Conflict    |
| 被 Question 引用不能删除    | 409 Conflict    |

---

# 26. 异常设计

F-003 暂时保持简单的异常体系。

建立：

`ResourceNotFoundException`

用于：

`404 Not Found`

例如：

* 当前知识点不存在
* 指定父节点不存在

---

建立：

`BusinessConflictException`

用于：

`409 Conflict`

例如：

* 同级重名
* 根节点重名
* 将自己设为父节点
* 移动形成环
* 有子节点不能删除
* 被 Question 引用不能删除

---

参数校验失败：

优先通过 Bean Validation 和 Spring 的校验机制处理。

状态：

`400 Bad Request`

不为每一种参数错误单独建立业务异常类。

---

# 27. GlobalExceptionHandler

建立统一异常处理器：

`GlobalExceptionHandler`

负责将 Java 异常转换为明确的 HTTP Response。

例如：

`ResourceNotFoundException`

↓

`404 Not Found`

↓

```json
{
  "status": 404,
  "message": "知识点不存在"
}
```

---

`BusinessConflictException`

↓

`409 Conflict`

↓

```json
{
  "status": 409,
  "message": "同一父节点下已存在同名知识点"
}
```

这样可以避免每一个 Controller 方法内部重复编写 try/catch。

---

# 28. Controller 设计

建立：

`KnowledgePointController`

负责暴露：

```text
GET    /api/knowledge-points/tree

POST   /api/knowledge-points

PATCH  /api/knowledge-points/{id}/name

PATCH  /api/knowledge-points/{id}/parent

DELETE /api/knowledge-points/{id}
```

Controller 主要负责：

* 接收路径参数
* 接收 JSON Body
* Bean Validation
* 调用 KnowledgePointService
* 设置正确的 HTTP Status
* 返回 DTO

不得将核心业务规则直接写入 Controller。

---

# 29. 预计代码结构

最终具体包路径以当前项目已有 package 结构为准。

概念结构如下：

```text
controller/
└── KnowledgePointController

dto/
├── CreateKnowledgePointRequest
├── UpdateKnowledgePointNameRequest
├── MoveKnowledgePointRequest
├── KnowledgePointResponse
├── KnowledgePointTreeResponse
└── ErrorResponse

service/
└── KnowledgePointService

exception/
├── ResourceNotFoundException
├── BusinessConflictException
└── GlobalExceptionHandler

entity/
├── KnowledgePoint
└── Question

repository/
├── KnowledgePointRepository
└── QuestionRepository
```

F-003 不重复创建已经存在的 Entity 或 Repository。

只在现有代码基础上补充所需内容。

---

# 30. 测试策略

F-003 不以“手动请求能成功”为完成标准。

核心业务必须由自动化测试覆盖。

开发采用：

实现一个功能

↓

编写对应测试

↓

运行测试

↓

确认通过

↓

继续下一个功能

而不是最后一次性补测试。

---

# 31. 新增知识点测试

至少覆盖：

### 正常场景

* 新增根节点成功
* 在已有节点下新增子节点成功

### 异常场景

* 父节点不存在
* 普通节点同级重名
* 根节点重名
* name 为 null
* name 为空
* name 全为空格

---

# 32. 修改名称测试

至少覆盖：

### 正常场景

* 正常修改名称
* 修改为当前原名称

### 异常场景

* 当前 KnowledgePoint 不存在
* 新名称为空
* 与兄弟节点重名
* 根节点与其他根节点重名

---

# 33. 移动知识点测试

至少覆盖：

### 正常场景

* 正常移动到其他父节点
* 移动为根节点
* 移动到当前原父节点

### 异常场景

* 当前 KnowledgePoint 不存在
* 新父节点不存在
* 把自己设置为自己的父节点
* 移动到自己的直接子节点下面
* 移动到自己的更深层后代下面
* 移动后与兄弟节点重名
* 移动为根节点后与其他根节点重名

---

# 34. 删除知识点测试

至少覆盖：

### 正常场景

* 无子节点且未被 Question 引用时删除成功

### 异常场景

* KnowledgePoint 不存在
* KnowledgePoint 存在子节点
* KnowledgePoint 被 Question 引用

删除成功后应确认对应数据库记录已经不存在。

---

# 35. 知识点树查询测试

至少覆盖：

### 场景一

数据库为空。

预期：

```json
[]
```

### 场景二

只有一个根节点。

### 场景三

存在多个根节点。

### 场景四

存在两层知识点。

### 场景五

存在三层及以上知识点。

需要确认：

* 根节点正确
* children 正确
* 层级关系正确
* 节点没有丢失
* 节点没有重复

---

# 36. Controller / API 测试

除 Service 层测试外，需要针对 Controller 验证至少以下内容：

* URL 是否正确
* HTTP Method 是否正确
* Request Body 是否能够正常绑定
* 参数校验是否生效
* 成功状态码是否正确
* 失败状态码是否正确
* JSON Response 结构是否正确
* DELETE 成功是否返回 204

---

# 37. 开发顺序

F-003 按以下顺序逐步实现。

## Step 1：检查 F-002 当前代码

正式编码前重新检查：

* `KnowledgePoint.java`
* `Question.java`
* `KnowledgePointRepository.java`
* `QuestionRepository.java`
* `application.yaml`
* `pom.xml`
* 现有测试结构
* 当前 package 结构

目的：

确保 Feature Plan 与真实代码一致。

---

## Step 2：创建 F-003 Feature 分支

建议分支：

`feature/F-003-knowledge-point-management`

---

## Step 3：建立 F-003 Feature Plan

文件建议：

`docs/plans/active/F-003-knowledge-point-management.md`

本文档即作为初始内容。

---

## Step 4：补充 Repository 查询能力

只添加 F-003 当前真正需要的方法。

完成后运行 Repository 相关测试。

---

## Step 5：实现 DTO 与参数校验

优先实现：

* CreateKnowledgePointRequest
* UpdateKnowledgePointNameRequest
* MoveKnowledgePointRequest
* KnowledgePointResponse
* KnowledgePointTreeResponse
* ErrorResponse

---

## Step 6：实现新增知识点

优先选择新增功能打通第一条完整链路：

POST Request

↓

Controller

↓

CreateKnowledgePointRequest

↓

Service

↓

Repository

↓

MySQL

↓

KnowledgePointResponse

↓

201 Created

新增功能作为 F-003 第一条完整 REST 调用链。

---

## Step 7：实现知识点查询树

完成：

`GET /api/knowledge-points/tree`

重点理解：

数据库平面记录

↓

Service

↓

树状 DTO

---

## Step 8：实现修改名称

完成：

`PATCH /api/knowledge-points/{id}/name`

重点实现同级查重以及排除自身。

---

## Step 9：实现移动知识点

完成：

`PATCH /api/knowledge-points/{id}/parent`

重点实现：

* 新父节点验证
* 自引用验证
* 环检测
* 移动后重名验证

该部分预计是 F-003 业务逻辑复杂度最高的部分。

---

## Step 10：实现删除知识点

完成：

`DELETE /api/knowledge-points/{id}`

实现：

* 子节点保护
* Question 引用保护
* 204 No Content

---

## Step 11：实现统一异常处理

建立：

* ResourceNotFoundException
* BusinessConflictException
* GlobalExceptionHandler
* ErrorResponse

统一：

400 / 404 / 409

响应行为。

---

## Step 12：补齐自动化测试

根据前述测试范围补齐：

* Service Test
* Controller Test
* 必要的 Repository Test

运行完整测试：

`mvnw test`

要求全部通过。

---

# 38. 开发原则

F-003 开发过程中遵守以下原则。

### 原则一：以现有代码为唯一实现基础

Feature Plan 属于设计约束，但如果计划中的字段名称、Repository 方法名称等与真实代码不符：

以当前已经确认并测试通过的项目代码为准。

先分析差异，再决定是否修改设计。

不得为了让代码符合文档而破坏已经完成的 F-002 数据模型。

---

### 原则二：一次实现一个小功能

避免一次生成整个 F-003。

推荐：

新增

↓

测试

↓

查询

↓

测试

↓

改名

↓

测试

↓

移动

↓

测试

↓

删除

↓

测试

↓

异常处理与整体整理

---

### 原则三：边开发边理解

对于新的 Java / Spring 概念，例如：

* DTO
* `@RequestBody`
* `@PathVariable`
* `@Valid`
* `@NotBlank`
* Service
* Spring Data JPA 派生查询
* `ResponseEntity`
* `@RestControllerAdvice`
* Exception Handler

在第一次实际使用时解释其作用。

不要求在开发前一次性学习完整理论。

---

### 原则四：Controller 保持轻量

Controller 不实现核心业务判断。

Controller 负责 HTTP。

Service 负责业务。

Repository 负责数据访问。

---

### 原则五：不提前过度设计

F-003 不提前加入：

* 通用 BaseResponse
* 复杂 Result<T>
* 大型异常码体系
* 自定义业务状态码
* 分布式 traceId
* 复杂 Mapper 框架
* CQRS
* DDD 聚合设计
* 事件总线
* 缓存
* Redis

只有后续出现真实需求时再引入。

---

# 39. F-003 完成定义（Definition of Done）

F-003 只有满足以下全部条件才视为完成。

## 功能

* [ ] 可以查询完整知识点树
* [ ] 可以新增根知识点
* [ ] 可以新增子知识点
* [ ] 可以修改知识点名称
* [ ] 可以移动知识点
* [ ] 可以将知识点移动为根节点
* [ ] 可以删除合法知识点

## 业务规则

* [ ] 普通节点同级不能重名
* [ ] 根节点不能重名
* [ ] 修改名称查重时正确排除自身
* [ ] 不能把自己设置为父节点
* [ ] 移动知识点不能形成环
* [ ] 移动后不能产生同级重名
* [ ] 有子节点不能删除
* [ ] 被 Question 引用不能删除

## API

* [ ] GET tree 返回 200
* [ ] POST 创建成功返回 201
* [ ] PATCH 修改名称成功返回 200
* [ ] PATCH 移动成功返回 200
* [ ] DELETE 成功返回 204
* [ ] 参数非法返回 400
* [ ] 资源不存在返回 404
* [ ] 业务冲突返回 409

## 代码结构

* [ ] DTO 与 Entity 分离
* [ ] Controller 不包含核心业务逻辑
* [ ] Service 承担业务规则
* [ ] Repository 只负责数据访问
* [ ] 统一异常处理正常工作

## 测试

* [ ] Repository 所需查询测试通过
* [ ] Service 核心业务测试通过
* [ ] Controller / API 测试通过
* [ ] 环检测测试通过
* [ ] 删除保护测试通过
* [ ] 完整 `mvnw test` BUILD SUCCESS

## 文档

* [ ] F-003 Feature Plan 与最终实现保持一致
* [ ] 实现过程中产生的重要设计变化已经回写 Feature Plan
* [ ] `project-status.md` 已同步
* [ ] F-003 完成后 Feature Plan 从 `active` 移动到 `completed`

---

# 40. F-003 最终预期结果

F-003 完成之后，系统将第一次具备一个相对完整的后端业务模块。

调用方可以通过 REST API 对知识点树进行：

查询

↓

创建

↓

修改

↓

移动

↓

删除

同时 Service 层能够保证数据库中的知识点始终维持合法树结构。

F-003 完成后，KnowledgePoint 将不再只是 F-002 中的数据库 Entity，而会成为一个真正可以被外部系统调用和管理的业务模块。

在此基础上，后续 Question 错题管理功能即可正式使用 KnowledgePoint 作为稳定的知识点关联对象。
