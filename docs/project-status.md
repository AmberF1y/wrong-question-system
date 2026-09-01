# 错题整理 + 滚动复习系统：项目状态

## 1. 文档用途

本文件只记录项目当前真实状态。已经完成的内容、正在进行的内容和尚未完成的内容必须明确区分。

更新时间：2026-09-01

---

## 2. 当前 Feature

| Feature | 状态 | 内容 |
| --- | --- | --- |
| F-001 | Completed | Spring Boot 项目初始化与健康检查 |
| F-002 | Completed | 数据库设计、JPA Entity、Repository 与真实 MySQL 集成测试 |
| F-003 | Active | 知识点管理实现与验证已完成，待 Git 收尾 |

当前开发分支：

```text
feature/F-003-knowledge-point-management
```

F-003 实现前 Git 基线：

```text
fadc129 docs: finalize F-003 knowledge point management plan
```

当前 F-003 代码和文档尚未提交。完成最终差异检查、提交和推送后，才能将 F-003 标记为 Completed。

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
- Hibernate `ddl-auto: validate`

### 当前明确不引入

- MyBatis / MyBatis-Plus
- Flyway
- H2
- Testcontainers
- MapStruct
- Lombok
- Redis、消息队列、微服务等当前无实际需求的组件

ADR-001 已经准确记录 Spring Data JPA 等技术选择，与真实代码一致。F-003 不新增其他 ADR。

---

## 5. 当前数据库结构

已存在三张表：

- `question`
- `knowledge_point`
- `question_knowledge_point`

主要关系：

- `knowledge_point.parent_id` 自关联形成知识树；
- Question 与 KnowledgePoint 通过中间表形成多对多关系；
- `question.subject` 当前以字符串保存；
- `created_time`、`updated_time` 由 MySQL 维护；
- Entity 通过 `insertable = false`、`updatable = false` 读取时间字段。

F-003 未修改数据库表结构和 Entity 映射。

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

2026-09-01 在用户本地环境完成全量测试：

- Java 21.0.12；
- MySQL 9.6；
- 数据库 `wrong_question_system`；
- Maven 命令：`.\mvnw.cmd test`；
- Tests run：36；
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
| QuestionRepositoryTest | 1 |
| HealthControllerTest | 1 |
| 合计 | 36 |

Controller 测试连接真实 MySQL；Service 测试使用单元测试隔离业务分支。原有测试全部继续通过。

---

## 11. 手工 API 验证结果

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

---

## 12. F-003 剩余工作

- 检查 `git diff` 和 `git status`；
- 提交并推送 F-003 实现；
- 更新 F-003 为 Completed；
- 将 Feature Plan 从 `plans/active` 移到 `plans/completed`；
- 提交并推送 Feature 收尾文档；
- 确认最终工作区 clean。

下一阶段尚未规划，不在 F-003 中提前加入功能。
