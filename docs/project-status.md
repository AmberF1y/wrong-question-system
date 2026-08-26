# 错题整理 + 滚动复习系统

## 文档用途

本文件用于记录项目当前真实状态。

只记录已经确定、已经实现或当前正在进行的内容，不记录未经确认的未来设计。

当代码、数据库结构、技术方案或 Feature 状态发生重要变化时，应同步更新本文件。

---

# 1. 项目目标

开发一个面向个人学习场景的：

> 错题整理 + 滚动复习系统

系统核心流程：

```text
错题录入
↓
知识点关联
↓
保存
↓
进入复习队列
↓
重新做题
↓
查看答案与解析
↓
自评掌握程度
↓
重新安排复习时间
```

项目同时承担两个目标：

1. 实际可用的个人学习工具
2. 可用于求职展示的完整 Java 后端项目

当前开发原则：

> MVP 优先，先建立能够真实运行的核心闭环，再逐步扩展。

避免：

- 为未来不确定需求提前复杂设计
- 无实际需求的技术堆叠
- 为展示技术栈而引入不必要组件
- 文档与真实代码状态长期不一致

---

# 2. 当前项目阶段

当前 Feature：

```text
F-002 数据库设计与数据层准备
```

当前状态：

```text
Active
```

F-002 核心开发工作已经完成。

目前正在进行：

```text
文档同步
↓
最终测试
↓
Git 提交
↓
Feature 收尾
```

F-002 正式结束后，再单独设计下一阶段 Feature。

---

# 3. 当前技术栈

## 后端

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring Data JPA
- Hibernate
- Maven

## 数据库

- MySQL 9.6
- MySQL Connector/J

## 开发工具

- IntelliJ IDEA
- Navicat
- Git
- GitHub

---

# 4. 当前项目结构

项目根目录：

```text
wrong-question-system
├── backend
├── docs
└── sql
```

后端主要结构：

```text
backend/src/main/java/com/wrongquestion/backend

├── knowledge
│   ├── entity
│   │   └── KnowledgePoint.java
│   └── repository
│       └── KnowledgePointRepository.java
│
├── question
│   ├── entity
│   │   └── Question.java
│   └── repository
│       └── QuestionRepository.java
│
├── system
│   └── health
│       └── HealthController.java
│
└── BackendApplication.java
```

测试结构：

```text
backend/src/test/java/com/wrongquestion/backend

├── BackendApplicationTests.java
│
├── knowledge
│   └── repository
│       └── KnowledgePointRepositoryTest.java
│
├── question
│   └── repository
│       └── QuestionRepositoryTest.java
│
└── system
    └── health
        └── HealthControllerTest.java
```

---

# 5. Git 状态

当前开发分支：

```text
feature/F-002-database-design
```

当前 Feature 开发流程：

```text
建立 Feature 分支
↓
实现功能
↓
测试
↓
同步文档
↓
提交代码
↓
完成 Feature
```

---

# 6. F-001 项目初始化

状态：

```text
Completed
```

F-001 已完成项目基础工程建设。

已完成：

- 创建 Spring Boot 项目
- 使用 Java 21
- 配置 Maven
- 配置 Spring MVC
- 配置 Spring Data JPA
- 配置 MySQL
- 创建 `wrong_question_system` 数据库
- 配置 `DB_PASSWORD` 环境变量
- 建立项目目录结构
- 建立项目文档体系
- 建立 SQL 目录
- 创建健康检查接口
- 创建基础自动化测试

健康检查接口：

```http
GET /api/health
```

返回：

```json
{
  "status": "ok"
}
```

对应测试：

```text
HealthControllerTest
```

---

# 7. F-002 数据库设计与数据层准备

状态：

```text
Active
```

核心开发状态：

```text
Completed
```

当前只剩 Feature 收尾工作。

F-002 的目标是：

- 确定错题核心数据模型
- 确定知识点树模型
- 建立错题与知识点关系
- 创建真实 MySQL 表
- 建立 JPA Entity
- 建立 Repository
- 验证 Java → JPA → MySQL 数据访问链路

---

# 8. 当前数据库结构

数据库：

```text
wrong_question_system
```

字符集：

```text
utf8mb4
```

排序规则：

```text
utf8mb4_unicode_ci
```

当前核心数据表：

```text
question
knowledge_point
question_knowledge_point
```

整体关系：

```text
question
    N
    ↕
question_knowledge_point
    ↕
    N
knowledge_point
```

同时：

```text
knowledge_point.parent_id
        ↓
knowledge_point.id
```

形成知识点树的自关联结构。

---

# 9. question 表

用途：

> 保存一道错题自身的核心信息。

当前字段：

```text
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

其中核心字段包括：

- `question_text`：完整题目内容
- `wrong_answer`：用户的错误答案
- `correct_answer`：正确答案
- `analysis`：题目解析
- `error_reason`：错误原因
- `subject`：所属科目
- `image_path`：原始题目图片相对路径

早期设计中的：

```text
title
```

已经调整为：

```text
question_text
```

因为该字段实际保存完整题干，而不是简短标题。

---

# 10. subject 当前设计

当前：

```text
subject VARCHAR(50)
```

直接保存在 `question` 表中。

暂时不建立独立的：

```text
subject
```

表。

原因：

当前 MVP 中科目只承担简单分类作用，没有独立数据模型的现实需求。

如果未来出现科目独立管理等真实需求，再重新评估。

---

# 11. 图片保存方案

错题允许保存原始题目图片。

当前方案：

```text
实际图片文件
↓
本地文件系统

图片相对路径
↓
question.image_path
```

数据库不保存图片二进制内容。

例如：

```text
questions/2026/08/abc123.png
```

`image_path` 允许为空。

当前 MVP 默认：

> 一道题保存一个原始图片路径。

如果未来出现真实的多图片需求，再考虑建立：

```text
question_image
```

表。

当前不提前设计。

---

# 12. knowledge_point 表

用途：

> 保存结构化知识体系。

例如：

```text
408
└── 计算机网络
    └── 传输层
        └── TCP
            └── 拥塞控制
                └── 慢开始
```

当前字段：

```text
id
name
parent_id
created_time
updated_time
```

通过：

```text
parent_id
```

引用：

```text
knowledge_point.id
```

形成知识点树。

根节点：

```text
parent_id = NULL
```

例如：

```text
408
数学
英语
政治
```

都可以作为不同知识树的根节点。

---

# 13. 知识点名称规则

已经确定：

> 同一个父节点下面不允许存在两个同名知识点。

例如不允许：

```text
TCP
├── 拥塞控制
└── 拥塞控制
```

数据库通过：

```text
UNIQUE(parent_id, name)
```

保护普通子节点。

由于 MySQL 对 `NULL` 参与唯一约束存在特殊处理：

```text
parent_id = NULL
```

时，根节点名称唯一性不能完全依赖该联合唯一约束。

因此：

> 根节点重名检查后续由 Spring Boot 业务层负责。

---

# 14. 知识点树合法性

数据库负责保证：

```text
parent_id
```

引用的父知识点真实存在。

但是以下规则属于业务逻辑：

```text
节点不能把自己设为父节点
不能形成 A → B → A
不能形成更长的循环引用
```

这些规则将在后续 Service 层实现。

F-002 不负责实现复杂知识树业务校验。

---

# 15. question_knowledge_point 表

用途：

> 保存 Question 和 KnowledgePoint 之间的多对多关系。

字段：

```text
question_id
knowledge_point_id
```

联合主键：

```text
(question_id, knowledge_point_id)
```

因此可以避免：

> 同一道题重复关联同一个知识点。

一道题可以关联多个知识点。

例如：

```text
题目1001
├── 慢开始
└── 快速重传
```

---

# 16. 知识点关联原则

系统只保存题目：

> 直接关联的知识点。

例如知识树：

```text
408
└── 计算机网络
    └── TCP
        └── 拥塞控制
            └── 慢开始
```

如果题目直接考查：

```text
慢开始
```

则只保存：

```text
Question → 慢开始
```

不额外保存：

```text
Question → 拥塞控制
Question → TCP
Question → 计算机网络
Question → 408
```

祖先关系通过知识点树本身获取。

这样可以减少冗余数据。

---

# 17. subject 与知识点树

当前：

```text
question.subject
```

与知识点树根节点之间没有建立数据库外键。

后续业务层需要保证：

> Question 的 subject 与其关联知识点所属知识树一致。

例如：

```text
subject = 408
```

的错题不应该关联：

```text
数学
└── 高等数学
```

下面的知识点。

该规则属于后续 Service 层业务逻辑。

---

# 18. 删除规则

## 删除 Question

删除一道错题时：

```text
question
↓
question_knowledge_point
```

对应关联数据自动删除。

数据库使用：

```text
ON DELETE CASCADE
```

但是：

```text
knowledge_point
```

本身不会被删除。

---

## 删除 KnowledgePoint

如果知识点仍然被：

```text
question_knowledge_point
```

引用，则禁止删除。

使用：

```text
ON DELETE RESTRICT
```

如果知识点仍然存在子节点，也禁止直接删除。

知识点自关联同样采用：

```text
ON DELETE RESTRICT
```

避免直接破坏知识树。

---

# 19. 数据库初始化 SQL

数据库初始化脚本：

```text
sql/init.sql
```

当前负责创建：

```text
question
knowledge_point
question_knowledge_point
```

并包含：

- PRIMARY KEY
- FOREIGN KEY
- UNIQUE
- INDEX
- ON DELETE CASCADE
- ON DELETE RESTRICT
- 时间字段默认值
- 字符集配置

---

# 20. 数据库实际验证

已经使用 Navicat 对真实 MySQL 数据库进行验证。

测试知识点树：

```text
408
└── 计算机网络
    └── TCP
        └── 拥塞控制
```

已验证：

- 知识点正常插入
- Question 正常插入
- Question 与 KnowledgePoint 正常建立关系
- 同级知识点重名被 UNIQUE 拒绝
- 被引用知识点删除被 RESTRICT 拒绝
- 删除 Question 后关联记录自动 CASCADE 删除
- 删除 Question 不会删除 KnowledgePoint

数据库物理结构验证通过。

---

# 21. JPA Entity

当前已经建立两个主要 Entity：

```text
Question
KnowledgePoint
```

---

## Question

路径：

```text
backend/src/main/java/com/wrongquestion/backend/question/entity/Question.java
```

映射：

```text
Question
↓
question
```

当前主要字段：

```text
id
questionText
wrongAnswer
correctAnswer
analysis
errorReason
subject
imagePath
knowledgePoints
createdTime
updatedTime
```

主要 JPA 注解：

```text
@Entity
@Table
@Id
@GeneratedValue
@Column
@ManyToMany
@JoinTable
@JoinColumn
```

---

## KnowledgePoint

路径：

```text
backend/src/main/java/com/wrongquestion/backend/knowledge/entity/KnowledgePoint.java
```

映射：

```text
KnowledgePoint
↓
knowledge_point
```

当前主要字段：

```text
id
name
parent
createdTime
updatedTime
```

通过：

```text
@ManyToOne
@JoinColumn(name = "parent_id")
```

映射知识点父节点自关联。

Java 中：

```text
KnowledgePoint.parent
```

对应数据库：

```text
knowledge_point.parent_id
```

---

# 22. Question 与 KnowledgePoint 多对多映射

Question 中维护：

```text
Set<KnowledgePoint> knowledgePoints
```

通过：

```text
@ManyToMany
@JoinTable
```

映射：

```text
question_knowledge_point
```

当前采用：

> 单向多对多。

即：

```text
Question
↓
KnowledgePoint
```

KnowledgePoint 当前不维护：

```text
Set<Question>
```

避免增加现阶段没有必要的双向关系复杂度。

---

# 23. JPA Cascade 策略

Question 与 KnowledgePoint 之间当前不使用：

```text
CascadeType.REMOVE
```

原因：

> KnowledgePoint 生命周期独立于 Question。

删除 Question：

```text
不能删除 KnowledgePoint
```

这与数据库层设计保持一致：

```text
question
↓ CASCADE
question_knowledge_point

knowledge_point
保持不变
```

---

# 24. 时间字段

数据库负责维护：

```text
created_time
updated_time
```

JPA 中对应字段配置为：

```text
insertable = false
updatable = false
```

因此：

> Java 当前只读取时间字段，数据库负责生成和更新时间。

---

# 25. Repository

当前已经建立：

```text
QuestionRepository
KnowledgePointRepository
```

---

## QuestionRepository

路径：

```text
backend/src/main/java/com/wrongquestion/backend/question/repository/QuestionRepository.java
```

继承：

```text
JpaRepository<Question, Long>
```

---

## KnowledgePointRepository

路径：

```text
backend/src/main/java/com/wrongquestion/backend/knowledge/repository/KnowledgePointRepository.java
```

继承：

```text
JpaRepository<KnowledgePoint, Long>
```

Spring Data JPA 已经提供基础 CRUD 能力，包括：

```text
save
findById
findAll
delete
deleteById
count
existsById
```

当前尚未增加自定义查询方法。

---

# 26. Hibernate 数据库结构策略

原配置：

```text
ddl-auto: update
```

已经调整为：

```text
ddl-auto: validate
```

当前：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

数据库结构管理链路：

```text
docs/database-design.md
↓
sql/init.sql
↓
MySQL
```

Hibernate：

> 不负责自动修改数据库结构。

Hibernate 当前只负责启动时检查：

```text
JPA Entity
↕
真实 MySQL 表
```

是否兼容。

---

# 27. Repository 集成测试

当前已经创建：

```text
QuestionRepositoryTest
KnowledgePointRepositoryTest
```

使用真实：

```text
Spring Boot
Spring Data JPA
Hibernate
MySQL
```

数据访问链路。

---

## QuestionRepositoryTest

路径：

```text
backend/src/test/java/com/wrongquestion/backend/question/repository/QuestionRepositoryTest.java
```

验证流程：

```text
创建 KnowledgePoint
↓
KnowledgePointRepository.save()
↓
创建 Question
↓
Question.addKnowledgePoint()
↓
QuestionRepository.save()
↓
写入数据库
↓
EntityManager.flush()
↓
EntityManager.clear()
↓
重新 findById()
↓
验证 Question
↓
验证 KnowledgePoint 关系
```

实际覆盖：

```text
Question Entity
KnowledgePoint Entity
QuestionRepository
KnowledgePointRepository
question
knowledge_point
question_knowledge_point
@ManyToMany
```

---

## KnowledgePointRepositoryTest

路径：

```text
backend/src/test/java/com/wrongquestion/backend/knowledge/repository/KnowledgePointRepositoryTest.java
```

验证流程：

```text
创建根知识点
↓
Repository 保存
↓
创建子知识点
↓
child.parent = root
↓
Repository 保存
↓
EntityManager.flush()
↓
EntityManager.clear()
↓
重新查询 child
↓
验证 parent
```

实际覆盖：

```text
KnowledgePoint Entity
KnowledgePointRepository
@ManyToOne
parent_id
知识点自关联
```

---

# 28. Repository 测试机制

测试使用：

```text
@SpringBootTest
@Transactional
```

`@SpringBootTest`：

> 启动真实 Spring Boot 测试环境，使 Spring、JPA、Hibernate、Repository 和 MySQL 共同参与测试。

`@Transactional`：

> 测试运行在事务中，测试结束后自动回滚，避免测试数据长期污染开发数据库。

测试中的：

```text
entityManager.flush()
```

用于：

> 强制 JPA 将当前修改同步到数据库。

测试中的：

```text
entityManager.clear()
```

用于：

> 清空当前 JPA Persistence Context 中保存的 Entity。

之后重新：

```text
findById()
```

用于更加可靠地验证：

> 数据能够真正从数据库重新读取。

---

# 29. 当前自动化测试

当前测试：

```text
BackendApplicationTests
HealthControllerTest
QuestionRepositoryTest
KnowledgePointRepositoryTest
```

最新测试结果：

```text
Tests run: 4
Failures: 0
Errors: 0
Skipped: 0
```

Maven：

```text
BUILD SUCCESS
```

---

# 30. 当前数据访问链路

已经实际验证：

```text
Java Entity
↓
Spring Data JPA
↓
Hibernate
↓
MySQL
```

写入正常。

同时：

```text
MySQL
↓
Hibernate
↓
Spring Data JPA
↓
Java Entity
```

查询正常。

当前已验证：

```text
Question Entity                  ✅
KnowledgePoint Entity            ✅
QuestionRepository               ✅
KnowledgePointRepository         ✅
KnowledgePoint parent 自关联      ✅
Question-KnowledgePoint 多对多    ✅
Hibernate Schema Validate        ✅
MySQL 数据访问                   ✅
```

---

# 31. 当前项目文档

项目主要文档：

```text
docs/PRODUCT.md
docs/database-design.md
docs/project-status.md
docs/decisions/
docs/plans/
```

---

## PRODUCT.md

负责记录：

> 产品需求和系统目标。

---

## database-design.md

负责记录：

> 当前正式数据库设计。

---

## decisions

负责记录 ADR：

> 重要技术决策以及为什么这样选择。

当前实际数据访问技术已经确定为：

```text
Spring Data JPA
```

---

## plans

用于记录每个 Feature：

```text
目标
范围
任务
完成标准
状态
```

---

# 32. F-002 已完成内容

## 数据库设计

- [x] 设计 Question 数据模型
- [x] 设计 KnowledgePoint 数据模型
- [x] 设计 Question-KnowledgePoint 多对多关系
- [x] 设计 KnowledgePoint parent 自关联
- [x] 确定图片路径保存方案
- [x] 确定 subject 当前保存方案
- [x] 确定同级知识点名称唯一规则
- [x] 确定 CASCADE / RESTRICT 删除规则
- [x] 更新 `database-design.md`

## SQL

- [x] 编写 `sql/init.sql`
- [x] 创建 `question`
- [x] 创建 `knowledge_point`
- [x] 创建 `question_knowledge_point`
- [x] 使用 Navicat 实际执行
- [x] 验证 UNIQUE
- [x] 验证 FOREIGN KEY
- [x] 验证 CASCADE
- [x] 验证 RESTRICT

## JPA

- [x] 创建 `Question` Entity
- [x] 创建 `KnowledgePoint` Entity
- [x] 创建 parent 自关联
- [x] 创建 Question-KnowledgePoint 多对多映射
- [x] 创建 `QuestionRepository`
- [x] 创建 `KnowledgePointRepository`
- [x] 将 Hibernate `ddl-auto` 调整为 `validate`

## 测试

- [x] 创建 `QuestionRepositoryTest`
- [x] 创建 `KnowledgePointRepositoryTest`
- [x] 验证 Question 保存与重新读取
- [x] 验证 KnowledgePoint 保存与重新读取
- [x] 验证 parent 自关联
- [x] 验证多对多关系
- [x] Maven 全部测试通过

---

# 33. F-002 当前剩余工作

F-002 核心代码已经完成。

剩余工作：

```text
同步项目文档
↓
检查 git status
↓
执行最终 Maven 全量测试
↓
Git add
↓
Git commit
↓
Git push
↓
将 F-002 状态调整为 Completed
↓
将 F-002 Plan 移动到 plans/completed
↓
规划下一阶段 Feature
```

---

# 34. 当前尚未实现的内容

以下功能尚未开始正式实现：

```text
Service
Controller

错题新增 REST API
错题查询 REST API
错题修改 REST API
错题删除 REST API

知识点新增 REST API
知识点修改 REST API
知识点删除 REST API
知识树查询 API

DTO
参数校验
统一异常处理
分页

图片上传
图片访问

OCR

review_record
复习队列
复习算法
掌握程度
下一次复习时间

Dashboard
统计分析

前端业务页面
```

这些内容不属于 F-002。

---

# 35. 当前明确不引入的复杂技术

MVP 当前不引入：

```text
微服务
Redis
消息队列
Elasticsearch
分布式事务
Kubernetes
复杂 DDD
CQRS
Event Sourcing
```

没有真实需求前不增加这些组件。

---

# 36. 当前数据库管理原则

数据库正式设计依据：

```text
docs/database-design.md
```

数据库初始化 SQL：

```text
sql/init.sql
```

Hibernate：

```text
ddl-auto: validate
```

只负责验证，不自动修改数据库结构。

当前尚未引入数据库 Migration 工具。

如果后续数据库版本演进明显复杂，再单独评估。

---

# 37. 当前图片功能状态

当前已完成：

```text
question.image_path
```

数据模型预留。

当前方案：

```text
图片文件
↓
本地文件系统

相对路径
↓
MySQL
```

实际图片上传接口尚未实现。

---

# 38. 当前 OCR 状态

OCR 属于产品后续目标。

当前：

```text
未实现
```

F-002 仅为图片录入和未来 OCR 保留数据结构扩展空间。

---

# 39. 当前滚动复习功能状态

滚动复习属于产品核心目标。

当前以下功能尚未实现：

```text
review_record
复习记录
掌握程度
复习调度
下一次复习时间
已掌握状态
```

当前优先完成：

```text
错题基础数据模型
+
知识点基础数据模型
+
后端基础数据访问链路
```

复习模块将在后续 Feature 单独设计。

---

# 40. 当前前端状态

当前：

```text
尚未进入正式前端业务开发
```

目前项目开发主线：

```text
数据库
↓
JPA 数据层
↓
后端业务层
↓
REST API
↓
前端
```

前端将在后端核心接口逐渐稳定后接入。

---

# 41. 当前需要后续业务层处理的规则

数据库当前可以负责：

```text
主键
外键
唯一约束
基础删除安全
```

但以下规则需要后续 Service 层负责：

```text
知识树不能形成环

知识点不能把自己设置为父节点

根知识点不能重名

Question 至少关联一个 KnowledgePoint

Question.subject
必须与关联 KnowledgePoint 所属知识树一致
```

这些规则不由 F-002 数据库层直接实现。

---

# 42. 当前下一步

当前首先完成：

> F-002 收尾。

顺序：

```text
1. 更新 F-002 Feature Plan
2. 更新 project-status.md
3. 检查 git status
4. 执行最终 Maven 测试
5. Git add
6. Git commit
7. Git push
8. F-002 标记 Completed
9. 移动到 plans/completed
10. 单独设计下一阶段 Feature
```

下一阶段具体范围：

```text
尚未正式确定
```

不在 F-002 中提前加入新的业务功能。

---

# 43. 当前状态总结

截至 2026-08-27：

```text
F-001 项目初始化
Completed

F-002 数据库设计与数据层准备
核心开发 Completed
Feature 收尾 Active
```

当前项目已经具备：

```text
Spring Boot 基础工程
+
MySQL 数据库
+
Question 数据模型
+
KnowledgePoint 知识树模型
+
Question-KnowledgePoint 多对多关系
+
Spring Data JPA
+
Repository 数据访问层
+
真实 MySQL 集成测试
```

最新自动化测试：

```text
Tests run: 4
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

当前下一动作：

```text
完成 F-002 文档与 Git 收尾
```