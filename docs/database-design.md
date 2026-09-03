# 数据库设计

## 1. 当前版本

v2.0 MVP

当前阶段：

```text
F-005 固定规则滚动复习已完成并合并到 `main`
```

状态：

> F-001～F-004 的三表结构已成为 Flyway V1。F-005 通过 V2 增加当前复习状态、不可变复习历史、约束、索引和已有错题回填；空测试库迁移、已有开发库 baseline 升级与 Hibernate Schema Validate 均已通过本地验收。

---

# 2. 设计目标

本阶段数据库设计服务于：

> 错题整理 + 滚动复习系统 MVP

当前优先为后续错题管理功能建立稳定的数据基础。

数据库设计遵循以下原则：

1. 优先满足真实 MVP 需求。
2. 不为了未来可能出现的需求提前增加复杂结构。
3. 已经明确属于 MVP 的结构化需求需要正确建模。
4. 保持表结构清晰，方便后续 Spring Data JPA 映射。
5. 数据库负责基础数据完整性，复杂业务规则由 Spring Boot 业务层保证。
6. 后续数据库结构发生重要变化时，应同步更新项目文档。

---

# 3. 数据库基本信息

数据库名称：

```text
wrong_question_system
```

数据库：

```text
MySQL
```

字符集：

```text
utf8mb4
```

排序规则：

```text
utf8mb4_unicode_ci
```

后端数据访问方式：

```text
Spring Data JPA
```

---

# 4. 当前数据模型

当前设计五张数据表：

```text
question
knowledge_point
question_knowledge_point
question_review_state
review_record
```

分别负责：

```text
question
→ 保存错题核心信息

knowledge_point
→ 保存结构化知识树

question_knowledge_point
→ 保存错题和知识点之间的多对多关系

question_review_state
→ 保存每道错题唯一的当前复习状态

review_record
→ 保存一题多条、不可变的复习事件历史
```

整体关系：

```text
┌──────────────────────┐
│       question       │
└──────────┬───────────┘
           │
           │ 1:N
           ▼
┌────────────────────────────┐
│ question_knowledge_point   │
└───────────┬────────────────┘
            │
            │ N:1
            ▼
┌──────────────────────┐
│   knowledge_point    │
│                      │
│ parent_id ───────────┼──┐
└──────────────────────┘  │
          ▲               │
          └───────────────┘
               自关联
```

因此：

```text
question
      N
      ↕
      N
knowledge_point
```

一道错题可以关联多个知识点。

一个知识点也可以关联多道错题。

---

# 5. question 表

## 5.1 用途

`question` 用于保存一道错题自身的核心信息。

包括：

* 题目
* 我的错误答案
* 正确答案
* 解析
* 错误原因
* 所属科目
* 原始题目图片路径
* 创建时间
* 修改时间

知识点不再直接存放在 `question` 表中，而通过：

```text
question_knowledge_point
```

建立关系。

---

## 5.2 字段设计

| 字段             | 类型           | 是否可空 | 说明        |
| -------------- | ------------ | ---- | --------- |
| id             | BIGINT       | 否    | 主键，自增     |
| question_text  | TEXT         | 否    | 完整题目文字    |
| wrong_answer   | TEXT         | 否    | 用户原来的错误答案 |
| correct_answer | TEXT         | 否    | 正确答案      |
| analysis       | TEXT         | 否    | 题目解析      |
| error_reason   | TEXT         | 否    | 用户记录的错误原因 |
| subject        | VARCHAR(50)  | 否    | 所属科目      |
| image_path     | VARCHAR(500) | 是    | 原始题目图片路径  |
| created_time   | DATETIME     | 否    | 创建时间      |
| updated_time   | DATETIME     | 否    | 最近修改时间    |

---

## 5.3 主键

```text
id
```

类型：

```text
BIGINT
```

采用自增主键。

Java 实体中预计使用：

```text
Long
```

与数据库主键对应。

---

## 5.4 question_text

原数据库设计中的：

```text
title
```

调整为：

```text
question_text
```

原因：

该字段实际保存的是完整题目内容，而不是题目标题。

例如：

```text
设函数 f(x)=……

若……

则下列说法正确的是：

A. ...
B. ...
C. ...
D. ...
```

因此 `question_text` 能更准确表达字段含义。

---

## 5.5 核心字段完整性

以下字段属于一道完整错题的核心信息：

```text
question_text
wrong_answer
correct_answer
analysis
error_reason
subject
```

因此数据库中均不允许为空。

这样可以避免出现：

```text
有题目但没有正确答案
```

或者：

```text
有错题但没有解析
```

等不完整数据。

后端业务层仍然需要进行输入校验。

数据库 `NOT NULL` 作为第二层数据完整性保护。

---

## 5.6 subject

当前采用：

```text
VARCHAR(50)
```

直接保存在 `question` 表。

例如：

```text
数学
408
英语
政治
```

当前阶段暂时不建立独立：

```text
subject
```

数据表。

原因：

目前科目仅作为简单分类属性使用，还没有独立配置、复杂层级或其他需要单独建模的业务需求。

如果后续真实需求发生变化，再重新评估是否将科目独立建模。

F-004 请求不允许直接提交 `subject`。Service 根据所选知识点的共同根节点自动生成该字段，避免调用方同时提交两套可能互相矛盾的科目信息。

---

## 5.7 image_path

第一版题目图片采用：

```text
图片文件
→ 保存在本地文件系统

图片路径
→ 保存到 MySQL
```

因此：

```text
image_path
```

只保存图片路径，不保存图片二进制数据。

该字段允许：

```text
NULL
```

因为系统允许存在纯文字录入的错题。

推荐数据库保存相对于图片存储根目录的相对路径，例如：

```text
questions/2026/08/abc123.png
```

不建议保存：

```text
D:\Projects\wrong-question-system\uploads\abc123.png
```

这类与特定电脑绑定的绝对路径。

图片根目录应由后端配置管理。

当前 MVP 默认：

> 一道题最多直接保存一个原始题目图片路径。

如果以后出现一道题需要保存多张图片的真实需求，再考虑拆分：

```text
question_image
```

独立数据表。

---

# 6. knowledge_point 表

## 6.1 用途

`knowledge_point` 用于保存系统中的结构化知识体系。

知识体系采用树结构。

例如：

```text
408
└── 计算机网络
    └── 传输层
        └── TCP
            └── 拥塞控制
                └── 慢开始
```

知识树本身保存在数据库中。

不得将知识体系硬编码在 Java 程序中。

---

## 6.2 字段设计

| 字段           | 类型           | 是否可空 | 说明      |
| ------------ | ------------ | ---- | ------- |
| id           | BIGINT       | 否    | 主键，自增   |
| name         | VARCHAR(100) | 否    | 知识点名称   |
| parent_id    | BIGINT       | 是    | 父知识点 ID |
| created_time | DATETIME     | 否    | 创建时间    |
| updated_time | DATETIME     | 否    | 最近修改时间  |

---

## 6.3 主键

```text
id
```

使用：

```text
BIGINT
```

自增主键。

Java 实体预计对应：

```text
Long
```

---

## 6.4 name

知识点名称。

例如：

```text
计算机网络
传输层
TCP
拥塞控制
慢开始
反常积分
中值定理
```

采用：

```text
VARCHAR(100)
```

并且：

```text
NOT NULL
```

---

# 7. 知识树设计

## 7.1 parent_id

知识树通过：

```text
parent_id
```

实现。

`parent_id` 引用当前表自己的：

```text
knowledge_point.id
```

形成自关联。

例如：

| id | name  | parent_id |
| -: | ----- | --------: |
|  1 | 408   |      NULL |
|  2 | 计算机网络 |         1 |
|  3 | 传输层   |         2 |
|  4 | TCP   |         3 |
|  5 | 拥塞控制  |         4 |
|  6 | 慢开始   |         5 |

即可表示：

```text
408
└── 计算机网络
    └── 传输层
        └── TCP
            └── 拥塞控制
                └── 慢开始
```

---

## 7.2 根节点

如果：

```text
parent_id = NULL
```

表示该知识点是知识树的根节点。

例如：

```text
数学
408
英语
政治
```

都可以作为根节点存在。

根节点是数据库数据的一部分，不在程序中硬编码。

---

## 7.3 调整知识层级

知识树允许调整层级。

例如原来：

```text
计算机网络
└── TCP
```

之后调整为：

```text
计算机网络
└── 传输层
    └── TCP
```

只需要修改：

```text
TCP.parent_id
```

不需要重新建立整个知识树。

---

# 8. knowledge_point 数据规则

## 8.1 父节点必须存在

如果：

```text
parent_id != NULL
```

则对应的：

```text
knowledge_point.id
```

必须真实存在。

数据库通过外键保证该引用关系。

---

## 8.2 禁止自己成为自己的父节点

不允许：

```text
id = 10
parent_id = 10
```

即：

```text
TCP
└── TCP
```

该规则主要由后端业务层进行校验。

---

## 8.3 禁止形成环

不允许出现：

```text
TCP.parent_id = 拥塞控制

拥塞控制.parent_id = TCP
```

否则会形成：

```text
TCP
↑ ↓
拥塞控制
```

导致树结构失效。

普通数据库外键只能保证：

> parent_id 指向的数据存在。

无法简单保证整棵树一定无环。

因此：

> 知识点移动时，由 Spring Boot 业务层负责检查是否会产生环。

---

## 8.4 名称长度

- `knowledge_point.name` 数据库字段最多保存 100 个字符。
- 根节点在业务上最多 50 个字符。
- 普通节点在业务上最多 100 个字符。

根节点同时承担科目名称作用，需要能够同步到 `question.subject VARCHAR(50)`，因此根节点使用更严格的 50 字符限制。该规则由 Service 校验。

---

## 8.5 知识树归属

- 根节点代表一棵知识树和一个科目边界。
- 普通节点只允许在同一根节点内部移动。
- 禁止跨根节点移动。
- 根节点不能变成其他节点的子节点。
- 普通节点不能升级为根节点。

这些规则依赖当前树结构，由 Service 校验，不增加额外数据库字段。

---

## 8.6 根节点改名

允许根节点改名。改名时必须在同一事务中更新 `knowledge_point.name` 和相关 `question.subject`，任意一步失败时全部回滚。

---

## 8.7 名称清理与唯一性

- 保存前去除名称首尾空格。
- 不删除或合并内部空格。
- 根节点之间不能重名。
- 同一父节点下不能重名。
- 不同父节点下允许同名。
- `utf8mb4_unicode_ci` 下英文大小写不区分。

数据库联合唯一约束保护普通同级节点；根节点的 `parent_id` 为 `NULL`，MySQL 联合唯一约束不能阻止多个同名根节点，因此根节点重名必须由 Service 额外检查。

---

## 8.8 查询与排序

F-003 一次查询全部知识点，并在 Java 中以 O(n) 方式组装完整知识树。根节点和同级节点均按 ID 升序排列，不增加手工排序字段。

---

# 9. 同级知识点名称唯一

当前业务规则：

> 同一个父知识点下面，不允许存在两个同名知识点。

例如不允许：

```text
TCP
├── 拥塞控制
└── 拥塞控制
```

但是不同父节点下面允许同名：

```text
知识树 A
└── 极限

知识树 B
└── 极限
```

数据库普通子节点使用：

```text
(parent_id, name)
```

联合唯一约束保护。

需要注意：

MySQL 的唯一约束对：

```text
NULL
```

存在特殊处理。

因此多个：

```text
parent_id = NULL
```

的根节点无法完全依靠普通：

```text
UNIQUE(parent_id, name)
```

阻止同名。

MVP 阶段采用：

```text
普通子节点
→ 数据库唯一约束保证

根节点
→ Spring Boot 业务层额外检查
```

暂时不为了根节点唯一性引入复杂生成列等数据库设计。

---

# 10. knowledge_point 暂不保存的字段

当前版本暂时不添加：

```text
level
depth
path
description
sort_order
is_leaf
deleted
subject
```

---

## 10.1 level / depth

知识点层级可以根据：

```text
parent_id
```

关系计算。

如果同时保存：

```text
level
```

可能产生数据冗余和一致性问题。

---

## 10.2 path

暂时不保存：

```text
/408/计算机网络/传输层/TCP/
```

等完整路径。

当前个人使用场景中的知识点数量较小，没有必要为了树查询性能增加冗余字段。

---

## 10.3 description

当前系统目标是：

> 管理知识结构。

不是建立知识百科。

因此第一版不为每个知识点增加长描述字段。

---

## 10.4 subject

当前不在每一个知识点中重复保存：

```text
subject
```

例如不采用：

```text
TCP.subject = 408
拥塞控制.subject = 408
慢开始.subject = 408
```

知识点所属科目可以通过其根节点判断。

避免整个知识树重复保存科目信息。

---

# 11. question_knowledge_point 表

## 11.1 用途

`question_knowledge_point` 是：

```text
question
```

和：

```text
knowledge_point
```

之间的关联表。

因为：

```text
一道题可以关联多个知识点
```

同时：

```text
一个知识点可以对应多道错题
```

因此二者属于：

```text
多对多关系
```

---

## 11.2 字段设计

| 字段                 | 类型     | 是否可空 | 说明     |
| ------------------ | ------ | ---- | ------ |
| question_id        | BIGINT | 否    | 错题 ID  |
| knowledge_point_id | BIGINT | 否    | 知识点 ID |

---

# 12. 联合主键

`question_knowledge_point` 不单独增加：

```text
id
```

字段。

采用：

```text
(question_id, knowledge_point_id)
```

作为联合主键。

例如：

| question_id | knowledge_point_id |
| ----------: | -----------------: |
|        1001 |                 10 |
|        1001 |                 11 |
|        1002 |                 10 |

表达：

```text
题目1001
→ 知识点10
→ 知识点11

题目1002
→ 知识点10
```

联合主键同时保证：

同一道题不能重复关联同一个知识点。

以下数据不允许出现两次：

```text
question_id = 1001
knowledge_point_id = 10
```

---

# 13. 知识点关联规则

## 13.1 只保存直接关联知识点

如果知识树为：

```text
408
└── 计算机网络
    └── 传输层
        └── TCP
            └── 拥塞控制
                └── 慢开始
```

某道题直接考查：

```text
慢开始
```

关系表只保存：

```text
题目 → 慢开始
```

不自动额外保存：

```text
题目 → 拥塞控制
题目 → TCP
题目 → 传输层
题目 → 计算机网络
题目 → 408
```

原因：

祖先关系已经由知识树表达。

如果未来需要统计：

> TCP 下面共有多少错题

则查询 TCP 节点及其后代知识点对应的错题。

这种设计避免保存重复关系。

---

## 13.2 一道题可以直接关联多个知识点

如果一道题同时涉及：

```text
慢开始
快速重传
```

可以保存：

```text
题目1001 → 慢开始
题目1001 → 快速重传
```

因此：

> “只保存直接关联知识点”不代表一道题只能关联一个知识点。

---

# 14. 科目与知识点一致性

由于：

```text
question.subject
```

直接保存科目。

同时知识树根节点也可能表示：

```text
数学
408
英语
政治
```

因此业务层需要保证二者一致。

例如：

```text
question.subject = 408
```

关联：

```text
拥塞控制
```

沿知识树向上查找：

```text
拥塞控制
↑
TCP
↑
传输层
↑
计算机网络
↑
408
```

根节点：

```text
408
```

因此合法。

如果出现：

```text
question.subject = 408
```

但关联知识点最终属于：

```text
数学
```

则属于非法数据。

该规则暂时由 Spring Boot 业务层校验。

F-004 的具体处理为：

1. 校验至少选择一个知识点；
2. 批量加载全部直接选择的知识点；
3. 分别向上查找各知识点的根节点；
4. 根节点不一致时拒绝请求；
5. 根节点一致时将根节点名称写入 `question.subject`。

修改错题时允许整体替换为另一棵知识树中的知识点集合，因此可以切换科目，但一次请求中的知识点仍不能跨根节点混合。

---

# 15. 每道错题至少关联一个知识点

系统业务要求：

> 一道错题必须关联一个或多个知识点。

数据库结构本身无法通过简单外键完全保证：

```text
question
```

创建后一定存在至少一条：

```text
question_knowledge_point
```

记录。

因此新增错题时应由后端在同一个事务中完成：

```text
验证错题核心字段
↓
验证至少选择一个知识点
↓
验证知识点存在
↓
验证知识点和 subject 一致
↓
保存 question
↓
保存 question_knowledge_point
↓
提交事务
```

如果其中任何步骤失败：

```text
整个操作回滚
```

避免出现：

```text
有错题
但没有任何知识点
```

的不完整业务数据。

---

# 16. 外键与删除规则

## 16.1 question 删除

关系：

```text
question_knowledge_point.question_id
→ question.id
```

删除一道错题时：

```text
question
```

不存在后，它和知识点之间的关系也没有继续存在的价值。

因此采用：

```text
ON DELETE CASCADE
```

效果：

```text
删除 question
↓
自动删除对应 question_knowledge_point
```

不会删除：

```text
knowledge_point
```

本身。

---

## 16.2 knowledge_point 删除

关系：

```text
question_knowledge_point.knowledge_point_id
→ knowledge_point.id
```

如果某个知识点仍然被错题引用：

```text
不允许直接删除
```

避免：

```text
删除知识点
↓
大量错题的知识分类静默丢失
```

因此采用限制删除策略：

```text
ON DELETE RESTRICT
```

用户未来需要删除一个仍被使用的知识点时，应先：

```text
迁移关联错题
```

或者：

```text
解除相关关联
```

之后再删除。

F-003 的删除 API 不自动执行迁移或解除关联。存在任何错题引用时直接返回业务冲突，由数据库 `ON DELETE RESTRICT` 继续兜底。

---

## 16.3 存在子知识点时禁止删除

由于：

```text
knowledge_point.parent_id
→ knowledge_point.id
```

形成自关联。

如果：

```text
TCP
└── 拥塞控制
```

则 TCP 仍然拥有子节点。

此时不允许直接删除：

```text
TCP
```

需要先：

```text
移动子节点
```

或者：

```text
删除子节点
```

之后才能删除父节点。

---

# 17. 索引设计

## 17.1 question

当前主要使用主键：

```text
PRIMARY KEY(id)
```

F-002 阶段暂时不为了未来搜索功能提前建立全文索引等复杂索引。

随着后续真实查询需求增加，再根据具体 SQL 和数据量增加索引。

---

## 17.2 knowledge_point

需要支持：

```text
查询某个节点的直接子节点
```

因此：

```text
parent_id
```

需要索引。

同时：

```text
(parent_id, name)
```

用于保证普通节点：

> 同一父节点下名称不可重复。

---

## 17.3 question_knowledge_point

联合主键：

```text
(question_id, knowledge_point_id)
```

本身可以高效支持：

> 查询一道题关联了哪些知识点。

同时增加：

```text
knowledge_point_id
```

索引。

用于反向查询：

> 某个知识点关联了哪些错题。

---

# 18. 时间字段

当前：

```text
question
knowledge_point
```

均包含：

```text
created_time
updated_time
```

用于记录：

```text
数据创建时间
最近修改时间
```

二者均不能为空。

时间字段已经确定由 MySQL 维护：

- `created_time` 使用 `DEFAULT CURRENT_TIMESTAMP`。
- `updated_time` 使用 `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`。
- JPA Entity 通过 `insertable = false`、`updatable = false` 读取时间，不主动写入。

F-004 创建错题后会刷新持久化实体，以读取 MySQL 实际生成的时间。修改错题时会显式触发 `question.updated_time` 更新；即使只替换多对多关联而文本未变化，也能让修改时间反映本次业务操作。该处理不增加新字段或新时间管理框架。

---

# 19. 当前不采用逻辑删除

当前阶段没有：

```text
deleted
is_deleted
deleted_time
```

等字段。

错题删除当前按照真实删除处理。

原因：

第一版为单用户 MVP，目前没有回收站、数据恢复等真实产品需求。

如果未来出现：

```text
误删恢复
回收站
历史数据保留
```

等需求，再考虑逻辑删除。

---

# 20. F-002 阶段未提前设计复习字段（历史决策）

F-002 阶段没有直接在：

```text
question
```

中增加：

```text
mastery_level
next_review_time
last_review_time
review_count
review_status
```

等字段。

原因是当时尚未确认滚动复习规则，需要在对应 Feature 中区分：

```text
题目的当前复习状态
```

和：

```text
每一次历史复习记录
```

F-005 已据此采用独立的 `question_review_state` 和 `review_record`
两张表，具体当前设计见第 30～33 节。以上内容只保留为 F-002
没有提前把复习字段塞入 `question` 表的历史原因，不再表示复习数据尚未设计。

---

# 21. 当前不设计 OCR 数据

当前暂时不建立：

```text
ocr_result
ocr_task
```

等数据结构。

OCR 是后续增强功能。

F-002 只通过：

```text
image_path
```

保证题目可以保存原始图片，并为未来 OCR 能力保留数据来源。

---

# 22. 当前不设计独立 question_image 表

MVP 当前按照：

```text
一道错题
→ 最多一个原始图片路径
```

处理。

因此：

```text
image_path
```

直接保存在：

```text
question
```

中。

如果以后真实使用出现：

```text
一道题多张图片
题干图片
图表图片
答案截图
多页题目
```

再拆分：

```text
question_image
```

表。

---

# 23. 当前不设计独立 subject 表

第一版：

```text
subject
```

继续保存在：

```text
question.subject
```

中。

暂时不建立：

```text
subject
```

独立数据表。

未来如果出现：

```text
科目配置
科目排序
科目属性
科目新增删除管理
更多复杂关联
```

再重新评估。

---

# 24. 当前数据库结构总结

## question

```text
question

id
question_text
wrong_answer
correct_answer
analysis
error_reason
subject
image_path
created_time
updated_time
```

---

## knowledge_point

```text
knowledge_point

id
name
parent_id
created_time
updated_time
```

---

## question_knowledge_point

```text
question_knowledge_point

question_id
knowledge_point_id
```

---

# 25. 当前关系总结

```text
question
    │
    │ 一道题可以关联多个知识点
    │
    ▼
question_knowledge_point
    ▲
    │
    │ 一个知识点可以关联多道题
    │
knowledge_point
```

知识点内部：

```text
knowledge_point.parent_id
        ↓
knowledge_point.id
```

形成知识树。

---

# 26. 当前数据库设计能够支持的功能

当前结构可以支持：

## 错题管理

```text
新增错题
查看错题
修改错题
删除错题
```

## 图片

```text
保存原始题目图片路径
```

## 科目分类

```text
按照 subject 对错题分类
```

## 知识体系

```text
创建知识点
修改知识点
建立知识树
调整知识点层级
```

## 错题与知识点

```text
一道题关联一个或多个知识点
一个知识点查询对应的多道错题
```

## 后续薄弱知识点统计基础

通过：

```text
question
+
question_knowledge_point
+
knowledge_point
```

可以为未来：

```text
薄弱知识点分析
知识点专项训练
知识点错题统计
```

提供数据基础。

---

# 27. 后续可能扩展的数据表

根据真实需求，未来可能增加：

复习状态与复习历史已在 F-005 中实现，不再属于未来扩展项。后续可能在现有 `review_record` 上增加历史查询与统计，但不得通过改写既有迁移文件完成。

---

## question_image

用于：

```text
一道题保存多张图片
```

---

## OCR 相关数据

用于：

```text
OCR原始识别结果
OCR任务状态
OCR修改结果
```

---

## subject

如果未来科目本身出现独立管理需求，再考虑单独建模。

---

# 28. 当前阶段明确不做

当前仍不提前实现：

```text
复杂复习算法
OCR任务表
AI解析数据
全文搜索
复杂标签系统
逻辑删除
多用户数据隔离
权限系统
复杂统计数据表
```

这些功能在出现真实需求时再独立设计。

---

# 29. 当前实现状态

F-002 完成的三张表已原样转为 `V1__initial_schema.sql`。`sql/init.sql` 不再作为结构来源。

F-003 在现有表结构上实现知识点 Service、事务、REST API 和严格业务校验，不新增或修改数据库表。

F-004 在同一结构上实现错题创建、详情、分页、修改和删除：

- 创建和修改由 Service 保证核心文本、知识点集合与 `subject` 一致；
- 分页先查询 `question.id`，再批量加载错题及知识点，避免多对多 fetch join 直接分页造成结果失真；
- 删除 `question` 后由现有外键级联清理关联表，不删除知识点；
- `spring.jpa.open-in-view=false`，响应映射所需关联必须在 Service 只读事务中明确加载；
- F-004 当时未修改数据库结构；该历史事实不因 F-005 改变。

F-005 新增 Flyway 和两张复习表。应用启动顺序为：

```text
Flyway 执行版本迁移
→ Hibernate ddl-auto=validate 校验 Entity 映射
→ Spring 应用开始提供接口
```

本文件作为当前数据库结构设计的主要依据。

后续如果数据库设计发生重要变化，应先讨论设计方案，再同步修改该文档和数据库结构。

---

# 30. Flyway 迁移来源

业务表结构只从以下迁移文件产生：

```text
backend/src/main/resources/db/migration/V1__initial_schema.sql
backend/src/main/resources/db/migration/V2__add_rolling_review.sql
```

`sql/create-database.sql` 和 `sql/create-test-database.sql` 只创建空数据库，不创建业务表。

V1 是 F-004 完成时的三表基线；V2 创建复习结构、增加 `question(subject)` 索引，并为每道已有错题回填一条 `ACTIVE` 状态：

```text
next_review_date = DATE(question.created_time) + 1 天
consecutive_proficient_count = 0
last_reviewed_at = NULL
version = 0
```

V1、V2 一旦应用后不可回写，后续修改通过 V3、V4 等新迁移完成。

---

# 31. question_review_state

一题恰好一条当前状态，`question_id` 同时是主键和外键：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `question_id` | BIGINT | 否 | 共享主键，指向 `question.id` |
| `review_status` | VARCHAR(20) | 否 | `ACTIVE` / `MASTERED` |
| `next_review_date` | DATE | 是 | 活动题必填，已掌握题为空 |
| `consecutive_proficient_count` | INT | 否 | 0、1 或已掌握时的 2 |
| `last_reviewed_at` | DATETIME(6) | 是 | 最后一次真实评价时刻，UTC 语义 |
| `version` | BIGINT | 否 | JPA 乐观锁版本 |

一致性约束：

- `ACTIVE`：下一次日期非空，连续熟练次数为 0 或 1；
- `MASTERED`：下一次日期为空，连续熟练次数为 2，最后评价时间非空；
- `version >= 0`。

队列索引：

```text
(review_status, next_review_date, question_id)
```

删除 Question 时通过外键 `ON DELETE CASCADE` 删除状态。

---

# 32. review_record

每次评价或重新加入写入一条不可变历史：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 否 | 自增主键 |
| `question_id` | BIGINT | 否 | 关联错题 |
| `event_type` | VARCHAR(20) | 否 | `EVALUATION` / `REACTIVATION` |
| `rating` | VARCHAR(30) | 是 | 四级评价；重新加入时为空 |
| `business_date` | DATE | 否 | 配置时区下的业务日期 |
| `occurred_at` | DATETIME(6) | 否 | UTC 语义的实际时刻 |
| `scheduled_review_date` | DATE | 是 | 评价前原到期日；重新加入时为空 |
| `resulting_status` | VARCHAR(20) | 否 | 事件后的状态 |
| `resulting_next_review_date` | DATE | 是 | 事件后的下一次日期 |
| `resulting_proficient_count` | INT | 否 | 事件后的连续熟练次数 |

历史不保存题目内容快照，不提供修改或单独删除接口。删除 Question 时级联删除全部历史。

历史索引：

```text
(question_id, occurred_at, id)
```

---

# 33. 时间与并发语义

- 业务日期使用 `LocalDate`；
- 事件时刻使用 `Instant`，数据库按 UTC 语义保存；
- 默认业务时区为 `Asia/Shanghai`；
- Hibernate JDBC 时区固定为 UTC；
- `question_review_state.version` 由 JPA `@Version` 管理；
- 状态更新与历史插入处于同一事务，任何失败整体回滚。
