# 数据库设计

## 1. 当前版本

v1.0 MVP

当前阶段：

```text
F-002 数据库设计
```

状态：

> 数据库逻辑设计已确认，待编写建表 SQL 并建立 Spring Data JPA 映射。

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

F-002 当前设计三张数据表：

```text
question
knowledge_point
question_knowledge_point
```

分别负责：

```text
question
→ 保存错题核心信息

knowledge_point
→ 保存结构化知识树

question_knowledge_point
→ 保存错题和知识点之间的多对多关系
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

具体由：

```text
数据库默认时间
```

还是：

```text
Spring Data JPA
```

负责自动维护，将在 SQL 初始化和 JPA Entity 映射阶段统一确定。

当前数据库逻辑模型只规定：

> 创建时间和更新时间必须可靠存在并保持正确。

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

# 20. 当前不设计复习字段

本阶段暂时不在：

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

原因：

滚动复习的数据模型和复习历史需要在对应 Feature 中根据实际复习规则单独设计。

需要重点区分：

```text
题目的当前复习状态
```

和：

```text
每一次历史复习记录
```

因此不在 F-002 阶段提前假设最终结构。

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

## review_record

用于保存：

```text
每一次复习历史
掌握程度
复习时间
评价结果
```

---

## 复习状态相关数据

用于支持：

```text
下一次复习时间
当前掌握程度
是否已掌握
滚动复习队列
```

具体结构由后续复习 Feature 设计决定。

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

F-002 当前不提前实现：

```text
复习历史表
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

# 29. F-002 下一步

数据库逻辑设计确认后，按以下顺序继续：

```text
1. 根据本文件编写建表 SQL
↓
2. 在 wrong_question_system 中创建数据表
↓
3. 使用 Navicat 验证表结构、主键、外键和索引
↓
4. 创建 Spring Data JPA Entity
↓
5. 创建数据访问层
↓
6. 验证 Spring Boot 可以正常读写数据库
↓
7. 更新 project-status.md
```

本文件作为当前数据库结构设计的主要依据。

后续如果数据库设计发生重要变化，应先讨论设计方案，再同步修改该文档和数据库结构。
