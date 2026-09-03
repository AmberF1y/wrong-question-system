# F-005 固定规则滚动复习核心闭环

## 1. Feature 基本信息

| 项目 | 内容 |
| --- | --- |
| Feature ID | F-005 |
| Feature 名称 | 固定规则滚动复习核心闭环 |
| 状态 | Planned |
| 规划日期 | 2026-09-04 |
| 规划基线 | `main`，用户确认 HEAD 为 `bba3122` |
| 计划分支 | `feature/F-005-rolling-review` |
| 计划文件 | `docs/plans/active/F-005-rolling-review.md` |
| 前置 Feature | F-001、F-002、F-003、F-004 均已完成并合并到 `main` |
| 基线测试 | 65 个测试全部通过，由用户提供的真实仓库状态与项目文档共同确认 |

> 本计划生成时尚未创建 F-005 功能分支，也未编写实现代码。用于规划的完整仓库压缩包不包含 `.git`，因此无法仅凭压缩包独立验证 `bba3122`、工作区 clean、远端同步或分支状态。开始实施前必须在真实本地 Git 仓库重新核对这些信息。

---

## 2. 文档用途

本文件是 F-005 的正式 Feature Plan，用于约束本阶段的：

- 产品目标；
- 功能范围；
- 复习业务规则；
- 状态转换；
- 日期与时区语义；
- 数据库迁移；
- 当前状态表与历史表；
- REST API；
- DTO；
- Validation；
- Service 与事务；
- Repository 查询；
- JPA 关联与乐观锁；
- 测试范围；
- 人工迁移验收；
- 文档同步；
- Git 与完成标准。

未写入本计划的前端、图片上传、OCR、Dashboard、自适应算法、已掌握题抽查等功能，不得在实现过程中临时加入 F-005。

如果实现过程中发现新需求，应先停止扩展，更新并重新确认计划，而不是直接修改代码扩大范围。

---

## 3. 规划依据

F-005 基于当前完整仓库压缩包中的以下真实文件重新规划：

- `README.md`
- `docs/PRODUCT.md`
- `docs/database-design.md`
- `docs/project-status.md`
- `docs/api-design.md`
- `docs/decisions/ADR-001-initial-tech-stack-and-architecture.md`
- `docs/plans/completed/F-001-project-initialization.md`
- `docs/plans/completed/F-002-database-design.md`
- `docs/plans/completed/F-003-knowledge-point-management.md`
- `docs/plans/completed/F-004-question-management.md`
- `sql/init.sql`
- `backend/pom.xml`
- `backend/src/main/resources/application.yaml`
- 当前全部生产 Java 代码；
- 当前全部测试 Java 代码。

规划同时以本次讨论中逐项确认的业务规则、技术方案、测试方案和完成标准为约束。

规划不把早期文档中的远期设想当作已实现能力，也不根据项目名称虚构复习模块。

---

## 4. 当前真实项目基线

### 4.1 已完成能力

当前系统已经具备：

- Java 21；
- Spring Boot 4.1.1；
- Spring MVC；
- Spring Data JPA；
- Hibernate；
- Jakarta Bean Validation；
- Maven Wrapper；
- MySQL 9.6 数据源；
- `GET /api/health`；
- 知识点完整树查询；
- 知识点创建、改名、同树移动和严格删除；
- 错题创建、详情查询、分页查询、科目筛选、完整修改和删除；
- 错题与知识点多对多关联；
- 根据知识点共同根节点自动维护 `question.subject`；
- Service 事务；
- 统一 `ApiErrorResponse`；
- Service 单元测试；
- Controller 和 Repository 的真实 MySQL 集成测试。

当前生产代码共使用三张业务表：

- `question`；
- `knowledge_point`；
- `question_knowledge_point`。

### 4.2 当前不存在的复习能力

当前代码和真实表结构中不存在：

- 复习当前状态实体；
- 复习历史实体；
- 下一次复习日期；
- 最后一次复习时间；
- 掌握状态；
- 连续熟练次数；
- 待复习队列；
- 评价接口；
- 重新加入复习接口；
- 复习历史查询接口；
- 时区配置；
- 并发评价保护。

`docs/database-design.md` 只提出后续可能增加复习相关数据，并明确要求后续 Feature 区分“当前复习状态”和“每一次历史复习记录”，没有预先确定最终表结构。

### 4.3 当前数据库管理方式

当前项目：

- 通过 `sql/init.sql` 一次性建立三张表；
- 尚未使用 Flyway 或 Liquibase；
- 使用 `spring.jpa.hibernate.ddl-auto: validate`；
- Hibernate 只校验表结构，不自动创建或修改表。

F-005 是第一次需要在已有数据库上新增表、索引、约束并回填旧数据，因此必须引入可重复、可追踪的数据库版本迁移。

### 4.4 当前测试环境问题

当前 Service 单元测试使用 Mockito，不连接数据库。

当前 Controller、Repository 和应用启动测试使用 `@SpringBootTest` 和真实 MySQL，大多数集成测试通过类级别 `@Transactional` 回滚测试数据。

仓库当前没有测试专用数据源配置，因此集成测试会连接日常使用的 `wrong_question_system` 数据库。F-005 引入 Flyway 后不能继续保留这种风险，必须切换到独立测试库。

---

## 5. Feature 目标

F-005 的目标是打通固定规则滚动复习的第一条完整后端闭环：

```text
创建错题
→ 自动安排首次复习
→ 动态获取下一道到期题
→ 通过错题详情查看答案
→ 提交掌握程度评价
→ 写入不可变历史
→ 计算下一次复习日期
→ 连续两次熟练后进入已掌握
→ 必要时手动重新加入复习
```

完成后，系统不再只是“保存错题”，而是能够根据明确日期持续推动错题重新出现，并保存每次状态变化的事实。

---

## 6. 为什么 F-005 选择固定规则滚动复习

当前产品核心闭环是：

```text
录入
→ 保存
→ 调度
→ 复习
→ 评价
→ 再调度
```

F-004 已经完成录入和管理链路。此时继续做图片、OCR 或 Dashboard 只能增强外围体验，仍不能形成核心复习循环。

固定规则方案具备以下特点：

- 直接产生用户价值；
- 与现有错题 CRUD 有明确依赖关系；
- 规则简单、可验证；
- 不需要提前引入复杂算法；
- 能自然引出数据库迁移、事务、历史记录、时间抽象和并发控制；
- 能为后续统计、已掌握抽查和自适应算法保留真实历史数据。

因此 F-005 不实现复杂间隔重复算法，只实现已确认的固定间隔闭环。

---

## 7. 范围

### 7.1 包含范围

F-005 包含：

- 新错题复习状态初始化；
- 已有错题复习状态回填；
- `ACTIVE` 与 `MASTERED` 两种复习状态；
- 四级评价；
- 固定间隔计算；
- 连续熟练计数；
- 已掌握判定；
- 待复习题动态逐题获取；
- 到期和逾期题查询；
- 待复习队列科目筛选；
- 待复习数量；
- 提交复习评价；
- 手动重新加入复习；
- `EVALUATION` 与 `REACTIVATION` 历史事件；
- 复习摘要加入现有错题响应；
- 错题分页按复习状态筛选；
- 修改错题保留复习数据；
- 删除错题级联删除复习数据；
- JPA 乐观锁并发保护；
- 后端业务时区配置；
- 可注入 `Clock`；
- Flyway 数据库迁移；
- 独立本地 MySQL 测试库；
- 自动化测试；
- 旧数据库人工迁移验收；
- 手工 API 冒烟验证；
- API、数据库、状态、ADR 和 Feature Plan 文档同步。

### 7.2 不包含范围

F-005 不包含：

- 前端页面；
- 图片上传或图片访问接口；
- OCR；
- AI 自动识别或解题；
- Dashboard；
- 各科目待复习统计接口；
- 薄弱知识点分析；
- 掌握率趋势接口；
- 复习历史查询接口；
- 复习历史编辑或单独删除；
- 历史题目内容快照；
- 已掌握题随机抽查；
- 自动让已掌握题重新进入队列；
- 自适应间隔算法；
- SM-2、FSRS 等算法；
- 用户自定义复习间隔；
- 用户级时区配置；
- 多用户、认证和权限；
- 前端会话快照或每日固定题单；
- Redis；
- 消息队列；
- 定时任务；
- 微服务拆分；
- H2；
- Testcontainers。

---

## 8. 核心术语

### 8.1 当前复习状态

每道错题恰好有一行当前复习状态，用于回答：

- 是否仍参与常规复习；
- 下一次何时复习；
- 已连续几次评价为熟练；
- 最后一次真实评价是什么时间。

当前状态会被后续评价覆盖更新。

### 8.2 复习历史

每次评价或重新加入都是一条只追加的历史事实。

历史记录用于回答：

- 何时发生了什么事件；
- 评价等级是什么；
- 事件前原计划何时复习；
- 事件后状态、日期和连续熟练次数是什么。

历史记录不保存题目内容快照。

### 8.3 到期与逾期

到期性不是持久化状态，而是计算结果：

```text
reviewStatus == ACTIVE
且
nextReviewDate <= 今天
```

其中：

- `nextReviewDate == 今天`：今日到期；
- `nextReviewDate < 今天`：已经逾期；
- `nextReviewDate > 今天`：尚未到期。

### 8.4 当前到期周期

题目从进入到期状态开始，直到一次评价成功后被安排到未来日期或进入 `MASTERED`，构成一个到期周期。

同一道题在同一到期周期只允许一次评价成功。

---

## 9. 评价等级与固定间隔

### 9.1 API 枚举

| 中文等级 | API / Java 枚举 | 间隔 |
| --- | --- | --- |
| 不会 | `NOT_KNOWN` | 1 天 |
| 模糊 | `FUZZY` | 3 天 |
| 基本掌握 | `BASICALLY_MASTERED` | 7 天 |
| 熟练 | `PROFICIENT` | 14 天，或第二次连续熟练后进入已掌握 |

### 9.2 非熟练评价

当评价为：

- `NOT_KNOWN`；
- `FUZZY`；
- `BASICALLY_MASTERED`；

系统必须：

- 保持 `ACTIVE`；
- 将连续熟练次数重置为 0；
- 从实际完成复习的业务日期起，分别增加 1、3、7 天；
- 更新 `lastReviewedAt`；
- 写入一条 `EVALUATION` 历史。

### 9.3 第一次连续熟练

当当前连续熟练次数为 0，评价为 `PROFICIENT` 时：

- 保持 `ACTIVE`；
- 连续熟练次数更新为 1；
- 下一次复习日期为实际完成日加 14 天；
- 更新 `lastReviewedAt`；
- 写入一条 `EVALUATION` 历史。

### 9.4 第二次连续熟练

当当前连续熟练次数为 1，评价为 `PROFICIENT` 时：

- 状态更新为 `MASTERED`；
- 连续熟练次数更新为 2；
- `nextReviewDate` 更新为 `null`；
- 更新 `lastReviewedAt`；
- 写入一条 `EVALUATION` 历史；
- 题目退出常规复习队列。

### 9.5 连续性的定义

只有相邻的两次真实评价均为 `PROFICIENT` 才算连续两次熟练。

中间只要出现其他评价，连续熟练次数立即归零。

`REACTIVATION` 不是评价；重新加入时直接按业务规则把连续熟练次数设为 0。

---

## 10. 日期与时区规则

### 10.1 业务时区

F-005 使用后端配置项：

```yaml
app:
  review:
    zone-id: Asia/Shanghai
```

默认值为 `Asia/Shanghai`。

当前不建立用户表，也不实现用户级时区。

### 10.2 时间来源

Spring 根据配置创建可注入的 Java `Clock`。

业务代码不得直接依赖：

- 服务器默认时区；
- 无参数 `LocalDate.now()`；
- 数据库 `CURRENT_DATE`；
- 测试执行机器的本地日期。

业务代码统一通过注入的 `Clock` 获取：

- 当前绝对时刻；
- 配置时区下的今天。

### 10.3 新错题首次复习

新错题创建时：

```text
nextReviewDate = 配置时区下的创建业务日期 + 1 天
```

状态初始化为：

```text
reviewStatus = ACTIVE
consecutiveProficientCount = 0
lastReviewedAt = null
```

### 10.4 已有错题首次复习

V2 迁移为每道已有错题建立状态：

```text
next_review_date = DATE(question.created_time) + 1 DAY
```

旧 `created_time` 使用 MySQL `DATETIME`，不携带时区信息。迁移按数据库中原有本地时间解释，不猜测或转换未知时区。

创建时间较早的题目会在迁移后立即成为逾期题，这是预期行为。

### 10.5 重新加入复习

已掌握题目手动重新加入时：

- 以操作发生时配置时区下的今天作为 `nextReviewDate`；
- 因此题目当天立即进入待复习队列；
- 连续熟练次数重置为 0；
- `lastReviewedAt` 保留最后一次真实评价时间；
- 重新加入的操作时刻写入 `REACTIVATION` 历史。

### 10.6 数据库时间类型

- `next_review_date`、`business_date`、`scheduled_review_date`、`resulting_next_review_date` 使用 MySQL `DATE` 和 Java `LocalDate`；
- `last_reviewed_at`、`occurred_at` 使用支持微秒精度的时间列；
- Java 对事件时刻使用 `Instant`；
- 新增事件时刻统一按 UTC 语义持久化和返回；
- Hibernate JDBC 时区明确配置为 UTC；
- 当前已有 `question.created_time` 和 `updated_time` 的行为不在 F-005 中重构。

---

## 11. 状态转换表

| 当前状态 | 当前连续熟练 | 操作 | 结果状态 | 结果连续熟练 | 结果日期 |
| --- | ---: | --- | --- | ---: | --- |
| `ACTIVE` | 0 或 1 | `NOT_KNOWN` | `ACTIVE` | 0 | 完成日 + 1 天 |
| `ACTIVE` | 0 或 1 | `FUZZY` | `ACTIVE` | 0 | 完成日 + 3 天 |
| `ACTIVE` | 0 或 1 | `BASICALLY_MASTERED` | `ACTIVE` | 0 | 完成日 + 7 天 |
| `ACTIVE` | 0 | `PROFICIENT` | `ACTIVE` | 1 | 完成日 + 14 天 |
| `ACTIVE` | 1 | `PROFICIENT` | `MASTERED` | 2 | `null` |
| `MASTERED` | 2 | `REACTIVATION` | `ACTIVE` | 0 | 当天 |

以下转换不允许：

- 对尚未到期的 `ACTIVE` 题提交评价；
- 对 `MASTERED` 题提交普通评价；
- 对 `ACTIVE` 题执行重新加入；
- 同一到期周期重复成功评价。

---

## 12. 数据库表设计

### 12.1 `question_review_state`

用途：保存每道错题的当前复习状态，一道题恰好一行。

| 字段 | 类型建议 | 允许空 | 说明 |
| --- | --- | --- | --- |
| `question_id` | `BIGINT` | 否 | 主键，同时是指向 `question.id` 的外键 |
| `review_status` | `VARCHAR(20)` | 否 | `ACTIVE` 或 `MASTERED` |
| `next_review_date` | `DATE` | 是 | `ACTIVE` 必须有值，`MASTERED` 必须为空 |
| `consecutive_proficient_count` | `INT` | 否 | 0、1 或 2 |
| `last_reviewed_at` | `DATETIME(6)` | 是 | 最后一次真实评价时刻，UTC 语义 |
| `version` | `BIGINT` | 否 | JPA `@Version` 乐观锁字段 |

外键：

```text
question_review_state.question_id
→ question.id
ON DELETE CASCADE
ON UPDATE RESTRICT
```

状态一致性检查至少保证：

```text
ACTIVE:
next_review_date IS NOT NULL
consecutive_proficient_count IN (0, 1)

MASTERED:
next_review_date IS NULL
consecutive_proficient_count = 2
last_reviewed_at IS NOT NULL
```

队列索引：

```text
(review_status, next_review_date, question_id)
```

该索引对应待复习核心条件和排序前缀。

### 12.2 `review_record`

用途：保存不可变的复习事件历史，一道题可以有多行。

| 字段 | 类型建议 | 允许空 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 否 | 历史主键 |
| `question_id` | `BIGINT` | 否 | 关联错题 |
| `event_type` | `VARCHAR(20)` | 否 | `EVALUATION` 或 `REACTIVATION` |
| `rating` | `VARCHAR(30)` | 是 | 四级评价；重新加入时为空 |
| `business_date` | `DATE` | 否 | 事件所属业务日期 |
| `occurred_at` | `DATETIME(6)` | 否 | 事件实际发生时刻，UTC 语义 |
| `scheduled_review_date` | `DATE` | 是 | 事件发生前原本的到期日期 |
| `resulting_status` | `VARCHAR(20)` | 否 | 事件后的状态 |
| `resulting_next_review_date` | `DATE` | 是 | 事件后的下一次复习日期 |
| `resulting_proficient_count` | `INT` | 否 | 事件后的连续熟练次数 |

外键：

```text
review_record.question_id
→ question.id
ON DELETE CASCADE
ON UPDATE RESTRICT
```

事件一致性检查至少保证：

```text
EVALUATION:
rating IS NOT NULL
scheduled_review_date IS NOT NULL

REACTIVATION:
rating IS NULL
scheduled_review_date IS NULL
```

结果状态、结果日期和结果次数必须满足与当前状态表相同的一致性关系。

历史索引：

```text
(question_id, occurred_at, id)
```

F-005 不提供历史查询 API，但该索引为后续单题时间线与统计提供基础。

### 12.3 `question` 新增索引

V2 为现有科目筛选和待复习科目过滤增加：

```text
question(subject)
```

F-005 不修改 `question` 的业务字段。

### 12.4 历史不可变规则

应用层不提供：

- 修改历史接口；
- 单独删除历史接口；
- 覆盖历史的 Service 方法。

历史只允许：

- 在评价事务中插入；
- 在重新加入事务中插入；
- 删除错题时由外键整体级联删除。

---

## 13. Flyway 迁移设计

### 13.1 依赖

根据 Spring Boot 4.1.1 的 Flyway 集成方式，增加：

- Spring Boot Flyway starter；
- Flyway MySQL 数据库模块。

具体版本由 Spring Boot 依赖管理，不在 `pom.xml` 手工锁定与父 POM 冲突的版本。

### 13.2 迁移目录

使用默认目录：

```text
backend/src/main/resources/db/migration
```

### 13.3 V1 基线迁移

新增：

```text
V1__initial_schema.sql
```

内容等价于当前 `sql/init.sql` 中三张业务表的真实定义，但不包含绑定固定数据库名的 `USE wrong_question_system`。

V1 是当前 `bba3122` 结构的版本化基线。

### 13.4 V2 复习迁移

新增：

```text
V2__add_rolling_review.sql
```

V2 负责：

- 创建 `question_review_state`；
- 创建 `review_record`；
- 创建外键、检查约束和索引；
- 为 `question.subject` 增加索引；
- 为所有已有错题插入初始状态；
- 保证一题一行状态。

回填不创建虚假的复习历史，只创建当前状态。

### 13.5 数据库创建脚本

将当前：

```text
sql/init.sql
```

改名为：

```text
sql/create-database.sql
```

它只负责创建空的日常开发数据库，不再创建业务表。

另新增：

```text
sql/create-test-database.sql
```

它只负责创建空的 `wrong_question_system_test` 数据库。

业务表统一由 Flyway 创建，避免两套完整 DDL 长期漂移。

### 13.6 现有数据库首次接管

配置原则：

```text
baseline-on-migrate 默认 false
baseline-version = 1
```

首次升级当前非空开发数据库时：

1. 确认 Git 和代码基线；
2. 备份数据库；
3. 记录 `question` 行数；
4. 临时显式开启 `baseline-on-migrate`；
5. 启动应用；
6. Flyway 将现有非空结构登记为 V1；
7. Flyway 执行 V2；
8. 关闭临时开关；
9. 检查 Flyway 历史、表结构和回填结果。

不在默认配置中永久开启 `baseline-on-migrate`。

### 13.7 迁移文件不可回写

V1、V2 一旦在共享数据库或已合并代码中执行，不再直接修改其内容。

后续表结构变化必须增加 V3、V4 等新迁移，避免 Flyway checksum 失配。

---

## 14. JPA Entity 设计

### 14.1 `QuestionReviewState`

核心映射：

- `@Entity`；
- `@Table(name = "question_review_state")`；
- `@Id questionId`；
- 单向 `@OneToOne(fetch = LAZY)` 指向 `Question`；
- `@MapsId` 让 `question_id` 同时作为主键和外键；
- `@Enumerated(EnumType.STRING)` 保存状态；
- `@Version` 映射 `version`。

实体提供有业务含义的方法，用于：

- 应用评价结果；
- 重新加入复习；
- 读取当前状态。

不公开允许任意组合写入状态、日期和次数的通用 setter。

### 14.2 `ReviewRecord`

核心映射：

- `@Entity`；
- `@Table(name = "review_record")`；
- 自增主键；
- 单向 `@ManyToOne(fetch = LAZY)` 指向 `Question`；
- 枚举使用字符串持久化；
- 字段在构造后不提供修改方法。

通过工厂方法区分：

- 创建评价历史；
- 创建重新加入历史。

### 14.3 `Question` 不建立反向复习集合

`Question` 不增加：

- `reviewState` 双向字段；
- `reviewRecords` 集合；
- JPA 级联删除复习数据。

这样可以避免：

- 加载错题时意外加载全部历史；
- 双向关系同步错误；
- JSON 序列化循环；
- 删除时 JPA 与数据库双重级联。

复习数据删除只依赖数据库外键。

---

## 15. 枚举与纯规则结果

### 15.1 `ReviewStatus`

```text
ACTIVE
MASTERED
```

不建立 `DUE` 或 `OVERDUE` 枚举。

### 15.2 `ReviewRating`

```text
NOT_KNOWN
FUZZY
BASICALLY_MASTERED
PROFICIENT
```

### 15.3 `ReviewEventType`

```text
EVALUATION
REACTIVATION
```

### 15.4 `ReviewSchedulingPolicy`

使用一个具体、无数据库依赖的规则类，不提前建立可插拔策略接口体系。

输入至少包括：

- 当前连续熟练次数；
- 本次评价；
- 实际完成业务日期。

输出一个不可变结果，至少包括：

- 结果状态；
- 结果下一次复习日期；
- 结果连续熟练次数。

规则常量：

```text
NOT_KNOWN_DAYS = 1
FUZZY_DAYS = 3
BASICALLY_MASTERED_DAYS = 7
PROFICIENT_DAYS = 14
MASTERED_PROFICIENT_COUNT = 2
```

间隔和阈值不放入 `application.yaml` 或数据库配置表。

---

## 16. REST API 总览

| 方法 | 路径 | 功能 | 成功状态 |
| --- | --- | --- | --- |
| GET | `/api/reviews/due/next` | 获取下一道待复习题及数量 | `200 OK` |
| POST | `/api/reviews/{questionId}/evaluations` | 提交四级评价 | `200 OK` |
| POST | `/api/reviews/{questionId}/reactivate` | 重新加入已掌握题目 | `200 OK` |

答案继续通过已有接口获取：

```text
GET /api/questions/{id}
```

F-005 不增加专门的 reveal 接口，也不记录“查看答案”事件。

---

## 17. 获取下一道待复习题

### 17.1 请求

```http
GET /api/reviews/due/next
```

可选科目筛选：

```http
GET /api/reviews/due/next?subject=408
```

### 17.2 `subject` 规则

- 可省略；
- 省略时跨全部科目；
- 提交时去除首尾空白；
- 空白字符串返回 `400`；
- 使用 `question.subject` 精确匹配；
- 不做模糊搜索；
- 不按知识点后代展开。

### 17.3 查询条件

```text
review_status = ACTIVE
next_review_date <= today
```

指定科目时再增加：

```text
question.subject = subject
```

### 17.4 排序

```text
next_review_date ASC
question_id ASC
```

即：

- 最早到期优先；
- 同一天按错题 ID 升序。

### 17.5 动态消费

接口每次只获取当前数据库状态下的第一题。

系统不保存：

- 每日题单快照；
- 当前复习会话；
- offset；
- 已取但未提交标记。

评价成功后，客户端再次调用本接口获取下一题。

### 17.6 成功响应

有待复习题时：

```json
{
  "dueCount": 3,
  "question": {
    "id": 42,
    "questionText": "题目内容",
    "imagePath": null,
    "subject": "408",
    "nextReviewDate": "2026-09-01"
  }
}
```

`dueCount`：

- 表示当前筛选条件下仍到期或逾期的题目总数；
- 包含本次响应中的当前题；
- 当前题评价成功后，再次查询时数量减少 1。

### 17.7 空队列响应

```json
{
  "dueCount": 0,
  "question": null
}
```

空队列是正常结果，返回 `200 OK`，不返回 `204` 或 `404`。

### 17.8 隐藏内容

待复习响应不得返回：

- `knowledgePoints`；
- `wrongAnswer`；
- `correctAnswer`；
- `analysis`；
- `errorReason`；
- `createdTime`；
- `updatedTime`。

响应只允许提前暴露题目、图片路径、科目和到期日期。

---

## 18. 提交复习评价

### 18.1 请求

```http
POST /api/reviews/{questionId}/evaluations
Content-Type: application/json
```

```json
{
  "rating": "BASICALLY_MASTERED"
}
```

`rating` 必填，必须是四个枚举值之一。

### 18.2 允许条件

只有同时满足以下条件才能评价：

- 错题存在；
- 当前状态为 `ACTIVE`；
- `nextReviewDate <= 今天`。

### 18.3 成功处理

同一事务中：

1. 读取状态；
2. 验证状态和到期性；
3. 通过 `ReviewSchedulingPolicy` 计算结果；
4. 更新当前状态；
5. 更新 `lastReviewedAt`；
6. 插入 `EVALUATION` 历史；
7. flush 并完成事务。

### 18.4 成功响应

```json
{
  "questionId": 42,
  "eventType": "EVALUATION",
  "rating": "BASICALLY_MASTERED",
  "occurredAt": "2026-09-03T10:20:30Z",
  "reviewStatus": "ACTIVE",
  "nextReviewDate": "2026-09-10",
  "consecutiveProficientCount": 0,
  "lastReviewedAt": "2026-09-03T10:20:30Z"
}
```

响应不自动包含下一道题，也不返回 `dueCount`。客户端提交成功后重新请求动态队列。

---

## 19. 重新加入复习

### 19.1 请求

```http
POST /api/reviews/{questionId}/reactivate
```

请求不需要 JSON body。

### 19.2 允许条件

- 错题存在；
- 当前状态为 `MASTERED`。

### 19.3 成功处理

同一事务中：

- 状态更新为 `ACTIVE`；
- 下一次复习日期更新为当天；
- 连续熟练次数更新为 0；
- `lastReviewedAt` 保持不变；
- 插入 `REACTIVATION` 历史；
- 通过乐观锁防止并发覆盖。

### 19.4 成功响应

响应复用评价操作的结果结构：

```json
{
  "questionId": 42,
  "eventType": "REACTIVATION",
  "rating": null,
  "occurredAt": "2026-09-03T10:20:30Z",
  "reviewStatus": "ACTIVE",
  "nextReviewDate": "2026-09-03",
  "consecutiveProficientCount": 0,
  "lastReviewedAt": "2026-09-01T09:00:00Z"
}
```

---

## 20. 现有错题 API 扩展

### 20.1 响应新增字段

以下响应统一在原有顶层字段末尾增加：

```json
{
  "reviewStatus": "ACTIVE",
  "nextReviewDate": "2026-09-04",
  "consecutiveProficientCount": 0,
  "lastReviewedAt": null
}
```

受影响响应：

- 创建错题响应；
- 查询错题详情响应；
- 错题分页列表 item；
- 修改错题响应。

字段不嵌套为 `review` 对象。

`MASTERED` 题目的 `nextReviewDate` 为 `null`。

### 20.2 分页状态筛选

现有接口增加可选参数：

```http
GET /api/questions?reviewStatus=MASTERED
```

并允许组合：

```http
GET /api/questions?page=0&size=20&subject=408&reviewStatus=ACTIVE
```

规则：

- `reviewStatus` 可省略；
- 省略时不过滤状态；
- 只接受 `ACTIVE`、`MASTERED`；
- 可与 `subject` 同时使用；
- 保持现有按 ID 降序分页行为；
- 不新增专门的已掌握列表接口。

### 20.3 修改规则

`PUT /api/questions/{id}` 只修改现有题目内容与知识点。

修改不得：

- 重置状态；
- 重置下一次日期；
- 重置连续熟练次数；
- 修改最后复习时间；
- 删除或伪造历史。

### 20.4 删除规则

`DELETE /api/questions/{id}` 继续真实删除错题。

数据库自动级联删除：

- 当前复习状态；
- 全部复习历史；
- 原有错题知识点关联。

知识点本身不得被删除。

---

## 21. DTO 设计

### 21.1 `DueReviewResponse`

- `long dueCount`；
- `DueQuestionResponse question`，允许为 `null`。

### 21.2 `DueQuestionResponse`

- `Long id`；
- `String questionText`；
- `String imagePath`；
- `String subject`；
- `LocalDate nextReviewDate`。

### 21.3 `SubmitReviewEvaluationRequest`

- `ReviewRating rating`；
- 使用 Bean Validation 校验非空。

### 21.4 `ReviewActionResponse`

- `Long questionId`；
- `ReviewEventType eventType`；
- `ReviewRating rating`，重新加入时允许为 `null`；
- `Instant occurredAt`；
- `ReviewStatus reviewStatus`；
- `LocalDate nextReviewDate`；
- `int consecutiveProficientCount`；
- `Instant lastReviewedAt`。

### 21.5 现有 Question DTO

`QuestionDetailResponse` 与 `QuestionSummaryResponse` 增加：

- `ReviewStatus reviewStatus`；
- `LocalDate nextReviewDate`；
- `int consecutiveProficientCount`；
- `Instant lastReviewedAt`。

`QuestionPageResponse` 外层分页结构不变。

---

## 22. HTTP 状态与错误码

继续使用现有 `ApiErrorResponse`，不增加另一层通用响应包装。

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| `400 Bad Request` | `VALIDATION_FAILED` | rating 为空、subject 空白等参数校验失败 |
| `400 Bad Request` | `MALFORMED_REQUEST_BODY` | JSON 非法或评价枚举值不存在 |
| `404 Not Found` | `QUESTION_NOT_FOUND` | 指定错题不存在 |
| `409 Conflict` | `REVIEW_NOT_DUE` | `ACTIVE` 题目尚未到期 |
| `409 Conflict` | `REVIEW_ALREADY_MASTERED` | 对 `MASTERED` 题提交普通评价 |
| `409 Conflict` | `REVIEW_NOT_MASTERED` | 对 `ACTIVE` 题执行重新加入 |
| `409 Conflict` | `REVIEW_CONCURRENT_MODIFICATION` | 乐观锁发现并发或重复修改 |

实现新增复习领域异常类型，并在 `GlobalExceptionHandler` 中映射。

数据库结构缺少状态行属于内部数据不变量破坏，不得静默创建默认状态掩盖问题。

---

## 23. 技术分层

```text
HTTP / JSON
↓
ReviewController
↓
ReviewService + Transaction
├── ReviewSchedulingPolicy
├── QuestionReviewStateRepository
├── ReviewRecordRepository
└── QuestionRepository
↓
QuestionReviewState / ReviewRecord / Question
↓
MySQL + Flyway
```

### 23.1 Controller

职责：

- 接收路径、查询参数和 JSON；
- 触发 Validation；
- 调用 Service；
- 返回 DTO。

Controller 不直接访问 Repository，也不计算复习日期。

### 23.2 Service

职责：

- 规范化 subject；
- 计算配置时区下的今天；
- 校验题目、状态和到期性；
- 调用纯规则类；
- 协调状态与历史；
- 控制事务；
- 映射响应 DTO。

### 23.3 Scheduling Policy

职责：

- 根据评价和连续熟练次数计算结果；
- 不访问数据库；
- 不依赖 HTTP；
- 不读取系统默认时间；
- 可通过纯 JUnit 测试完整验证。

### 23.4 Repository

职责：

- 到期数量查询；
- 下一题查询；
- 状态批量查询；
- 状态筛选分页所需查询；
- 状态保存；
- 历史插入。

### 23.5 Entity

职责：

- 映射真实数据库结构；
- 维护实体内部合法状态；
- 通过 `@Version` 提供并发检查。

Entity 不直接作为 API DTO 返回。

---

## 24. Transaction 设计

### 24.1 获取下一题

使用：

```text
@Transactional(readOnly = true)
```

同一只读事务内：

- 计算今天；
- 查询 `dueCount`；
- 查询排序后的第一题；
- 映射隐藏答案的 DTO。

### 24.2 提交评价

使用写事务。

状态更新与历史插入必须原子：

- 全部成功后提交；
- 任一失败全部回滚；
- 乐观锁冲突时历史插入也必须回滚。

### 24.3 重新加入

使用写事务。

状态恢复与 `REACTIVATION` 历史必须原子。

### 24.4 创建错题

现有 `QuestionService.create` 写事务内增加初始状态创建。

若状态创建失败，错题和知识点关联必须一起回滚，不允许产生无状态错题。

### 24.5 修改错题

沿用现有写事务，只读取状态用于响应，不修改状态或历史。

### 24.6 删除错题

沿用现有写事务删除错题，flush 后由数据库外键级联。

---

## 25. 乐观锁与重复提交

### 25.1 `@Version` 原理

状态表保存版本号。

更新状态时，Hibernate 生成的 SQL 会同时检查：

```text
question_id = ?
AND version = 读取时的版本
```

首个事务更新成功后版本递增。

另一个持有旧版本的事务更新行数为 0，JPA 抛出乐观锁异常。

### 25.2 并发评价

两个请求同时读取同一到期状态时：

- 只允许一个事务成功；
- 失败事务的状态和历史全部回滚；
- 返回 `409 REVIEW_CONCURRENT_MODIFICATION`。

### 25.3 顺序重复评价

第一个请求成功后，题目已被安排到未来或进入已掌握。

后续请求根据最新状态返回：

- `REVIEW_NOT_DUE`；或
- `REVIEW_ALREADY_MASTERED`；

不得插入第二条历史。

### 25.4 重新加入并发

评价与重新加入、两个重新加入请求同时发生时，同样依赖版本号防止丢失更新。

---

## 26. Repository 查询计划

### 26.1 待复习查询

Repository 提供：

- 全科目到期数量；
- 指定科目到期数量；
- 全科目第一道到期题；
- 指定科目第一道到期题。

第一题查询必须包含固定排序：

```text
nextReviewDate ASC, questionId ASC
```

不使用 offset 分页模拟队列。

### 26.2 错题分页状态筛选

延续现有“两步分页”方式：

1. 先分页查询错题 ID；
2. 再批量 fetch 知识点；
3. 再批量查询复习状态；
4. 按 ID 页中的顺序映射响应。

为以下组合提供明确查询：

- 无 subject、无 reviewStatus；
- 有 subject、无 reviewStatus；
- 无 subject、有 reviewStatus；
- 有 subject、有 reviewStatus。

不为本 Feature 引入 Criteria API、Querydsl 或新的动态查询框架。

### 26.3 状态批量装配

列表响应不得逐题执行状态查询。

Repository 应一次查询当前页全部 question ID 对应的状态，转换为 Map 后装配，避免 N+1。

详情、创建和修改响应可以按单题读取状态。

### 26.4 缺失状态处理

业务入口创建的新题和 V2 回填后的旧题都必须有状态。

如果响应装配时发现状态缺失：

- 视为数据库不变量损坏；
- 不临时补一行默认状态；
- 失败并要求修复数据或迁移。

---

## 27. 配置设计

### 27.1 主配置

新增：

- `app.review.zone-id`，默认 `Asia/Shanghai`；
- Hibernate JDBC UTC 配置；
- Flyway baseline version；
- 通过环境变量控制的一次性 baseline 开关。

数据库密码继续使用：

```text
${DB_PASSWORD}
```

不得提交真实密码。

### 27.2 测试配置

新增测试资源配置，使测试连接：

```text
wrong_question_system_test
```

测试继续使用真实 MySQL 和环境变量密码。

测试时注入固定 `Clock`，确保：

- 日期断言固定；
- 午夜执行不漂移；
- UTC 与北京时间边界可重复；
- 不需要 Thread.sleep 等待时间变化。

### 27.3 非法时区

配置的 zone ID 无法解析时，应用应启动失败，而不是悄悄回退到服务器默认时区。

---

## 28. 计划代码结构

```text
backend/src/main/java/com/wrongquestion/backend
├── common
│   └── exception
│       └── GlobalExceptionHandler.java
├── question
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
└── review
    ├── config
    │   ├── ReviewProperties.java
    │   └── ReviewTimeConfiguration.java
    ├── controller
    │   └── ReviewController.java
    ├── dto
    │   ├── DueQuestionResponse.java
    │   ├── DueReviewResponse.java
    │   ├── ReviewActionResponse.java
    │   └── SubmitReviewEvaluationRequest.java
    ├── entity
    │   ├── QuestionReviewState.java
    │   ├── ReviewEventType.java
    │   ├── ReviewRating.java
    │   ├── ReviewRecord.java
    │   └── ReviewStatus.java
    ├── exception
    │   └── ReviewConflictException.java
    ├── repository
    │   ├── QuestionReviewStateRepository.java
    │   └── ReviewRecordRepository.java
    └── service
        ├── ReviewSchedulingPolicy.java
        └── ReviewService.java
```

测试结构对应增加：

```text
backend/src/test/java/com/wrongquestion/backend/review
├── controller
├── repository
└── service
```

实际文件名允许在不改变职责和 API 契约的前提下做小幅调整，但不得把全部逻辑合并进 Controller 或 Entity。

---

## 29. 自动化测试方案

### 29.1 测试原则

- 不以测试数量为目标；
- 每条关键规则必须有可定位测试；
- 纯计算优先使用快速单元测试；
- Repository、约束、迁移和锁必须使用真实 MySQL；
- Controller 测试验证真实 Spring、JSON、事务和数据库链路；
- 原有 65 个测试必须全部继续通过；
- 不删除断言或跳过测试换取成功。

### 29.2 `ReviewSchedulingPolicyTest`

至少覆盖：

- `NOT_KNOWN` 从完成日加 1 天；
- `FUZZY` 从完成日加 3 天；
- `BASICALLY_MASTERED` 从完成日加 7 天；
- 第一次 `PROFICIENT` 加 14 天且次数为 1；
- 第二次连续 `PROFICIENT` 进入 `MASTERED`；
- 已有一次熟练后评价为其他等级，次数归零；
- 逾期题从实际完成日而非旧计划日计算；
- 月末跨月；
- 年末跨年；
- 闰日。

### 29.3 时间配置测试

至少覆盖：

- 默认 `Asia/Shanghai`；
- 固定 Instant 在 UTC 和北京时间对应不同日期时，业务日期使用北京时间；
- 非法 zone ID 导致配置失败；
- 测试不依赖运行机器默认时区。

### 29.4 `ReviewServiceTest`

使用 Mockito 和固定 Clock，至少覆盖：

- 获取全科目下一题；
- 去除 subject 首尾空格；
- 空白 subject 拒绝；
- 空队列返回 `dueCount=0` 和 `question=null`；
- dueCount 包含当前题；
- 待复习 DTO 不包含答案和知识点；
- 四类评价调用规则并写入历史；
- 评价使用实际完成日；
- 尚未到期拒绝；
- 已掌握题普通评价拒绝；
- 错题不存在返回 not found；
- 重新加入成功；
- 重新加入保留 lastReviewedAt；
- 对 active 题重新加入拒绝；
- 历史结果字段完整；
- 异常分支不保存历史。

### 29.5 `QuestionServiceTest` 扩展

至少覆盖：

- 创建错题同步创建状态；
- 初始日期为业务日加 1；
- 状态创建失败时不返回成功；
- 详情响应装配复习摘要；
- 列表批量装配复习摘要；
- 科目与状态四种分页筛选组合；
- 修改响应保留原复习摘要；
- 缺失状态不静默补默认值。

### 29.6 Repository 真实 MySQL 测试

至少覆盖：

- `@MapsId` 共享主键；
- 一题最多一行状态；
- 状态和历史枚举字符串映射；
- 到期日小于今天被选中；
- 到期日等于今天被选中；
- 未来日期不被选中；
- `MASTERED` 不被选中；
- 最早日期优先；
- 同日小 ID 优先；
- subject 精确过滤；
- dueCount 与筛选条件一致；
- reviewStatus 分页筛选；
- subject 与 reviewStatus 组合；
- 状态批量查询；
- 数据库拒绝非法状态与日期组合；
- 数据库拒绝非法连续熟练次数；
- 数据库拒绝非法事件与 rating 组合；
- 删除错题级联删除状态和历史。

### 29.7 `ReviewControllerTest`

使用 `@SpringBootTest`、MockMvc、真实 MySQL 和事务回滚，至少覆盖：

- 有题队列 JSON；
- 空队列 JSON；
- subject 筛选；
- 队列排序；
- 隐藏知识点和所有答案相关字段；
- 详情接口仍返回完整答案；
- 提交评价成功响应；
- 重新加入成功响应；
- rating 为空；
- rating 非法；
- subject 空白；
- question 不存在；
- 尚未到期；
- 已掌握题被普通评价；
- active 题被重新加入；
- 顺序重复评价；
- 错误响应 code、status、path。

### 29.8 完整生命周期测试

至少有一条贯穿真实数据库的生命周期：

```text
创建题目
→ 验证次日首次复习
→ 设置/推进到到期
→ 获取队列
→ 查看详情
→ 第一次熟练
→ 下一到期日评价第二次熟练
→ MASTERED
→ 从队列消失
→ 按 MASTERED 筛选找到
→ 重新加入
→ 当天重新出现在队列
```

测试不通过等待真实 14 天完成，使用固定 Clock 和受控测试数据推进日期。

### 29.9 修改与删除测试

修改测试必须验证：

- 状态值不变；
- nextReviewDate 不变；
- 连续熟练次数不变；
- lastReviewedAt 不变；
- 历史行数和内容不变。

删除测试必须验证：

- question 删除；
- question_review_state 删除；
- review_record 全部删除；
- question_knowledge_point 关联删除；
- knowledge_point 保留。

### 29.10 乐观锁测试

第一层使用两个独立事务：

- 同时基于同一 version 读取状态；
- 第一个事务更新成功；
- 第二个事务更新失败；
- 最终 version 只递增一次。

第二层验证重复/并发业务结果：

- 两个评价提交只有一个成功；
- 另一个返回冲突；
- 当前状态只推进一次；
- 只存在一条 `EVALUATION`；
- 失败事务没有残留历史。

并发测试不使用类级别外层 `@Transactional`，使用显式独立事务和明确 ID 清理。

### 29.11 Flyway 自动验证

独立测试库首次创建为空库。

自动验证：

- 应用启动执行 V1、V2；
- `flyway_schema_history` 记录正确；
- 当前版本为 V2；
- Hibernate validate 通过；
- 两张新表可由 Repository 使用；
- 约束、外键与索引存在并生效。

不在自动测试中创建或删除任意数据库。

### 29.12 原有测试保护

原有 65 个测试必须调整到测试库后继续通过。

因 Question 响应增加字段而修改现有断言时，只能增加新字段验证，不得削弱原行为断言。

直接通过 Repository 创建 Question 的测试辅助数据，如果需要进入复习业务，必须显式建立对应状态，不能依赖不存在的数据库触发器。

### 29.13 最终测试命令

Windows PowerShell：

```powershell
cd D:\Projects\wrong-question-system\backend
$env:DB_PASSWORD = "<你的本地 MySQL 密码>"
.\mvnw.cmd clean test
```

最终记录：

- Tests run；
- Failures；
- Errors；
- Skipped；
- BUILD SUCCESS / FAILURE；
- Java、MySQL 和 Spring Boot 版本。

---

## 30. 旧数据库迁移人工验收

### 30.1 迁移前

必须：

- 确认连接的是日常开发库；
- 导出备份；
- 记录三张原表行数；
- 特别记录 `question` 行数；
- 确认当前表结构与 V1 基线一致；
- 确认没有手工新增但未写入仓库的字段或表。

### 30.2 执行

仅首次接管时临时开启 baseline。

启动日志必须确认：

- 已创建 Flyway schema history；
- V1 被登记为 baseline；
- V2 成功执行；
- Hibernate validate 成功；
- 应用正常启动。

### 30.3 迁移后

核对：

```text
count(question_review_state) = count(question)
count(review_record) = 0
```

抽查多道旧题：

```text
review_status = ACTIVE
consecutive_proficient_count = 0
last_reviewed_at IS NULL
next_review_date = DATE(created_time) + 1 DAY
```

同时确认：

- 原 question 内容未变化；
- 原 knowledge_point 未变化；
- 原 question_knowledge_point 未变化；
- baseline 临时开关已经关闭。

### 30.4 失败处理

迁移失败时：

- 不继续手工补表掩盖失败；
- 不执行 Flyway repair 直到明确失败原因；
- 不合并功能分支；
- 保存日志；
- 评估是否从备份恢复；
- 修正尚未发布的迁移后重新验证。

---

## 31. 手工 API 验证

在独立测试库或明确的开发验证数据上，至少验证：

1. 创建错题并查看初始复习摘要；
2. 查询尚未到期时不出现在队列；
3. 将受控测试题设置为到期并获取下一题；
4. 确认响应没有知识点、错误答案、正确答案、解析和错误原因；
5. 调用错题详情查看完整内容；
6. 提交四类评价并核对日期；
7. 验证 dueCount 在成功提交后减少；
8. 验证最早到期和同日 ID 排序；
9. 验证 subject 筛选；
10. 连续两次熟练后进入 MASTERED；
11. 验证 MASTERED 从常规队列消失；
12. 按 reviewStatus 筛选已掌握题；
13. 手动重新加入并验证当天重新出现；
14. 验证尚未到期、已掌握普通评价、active 重新加入等 409；
15. 修改错题并确认进度和历史保留；
16. 删除错题并确认状态和历史级联清理。

手工验证不得依赖新增仅用于测试的生产 API。

---

## 32. 文档变更计划

### 32.1 `README.md`

更新：

- 当前能力；
- Flyway 启动方式；
- 日常数据库创建；
- 测试数据库创建；
- 一次性 baseline 提示；
- 测试命令；
- 复习 API 概览。

### 32.2 `docs/PRODUCT.md`

更新固定规则闭环的已实现范围和明确未实现范围。

### 32.3 `docs/api-design.md`

新增：

- 三个复习接口；
- DTO 示例；
- 队列、筛选、隐藏字段和空队列规则；
- 现有 Question 响应新增字段；
- reviewStatus 筛选；
- 错误码。

### 32.4 `docs/database-design.md`

更新：

- Flyway 成为结构来源；
- 两张新表；
- 字段、主键、外键、检查约束和索引；
- 当前状态与历史区别；
- 删除和时间规则；
- 旧题回填。

### 32.5 `docs/project-status.md`

实施期间记录 F-005 为 In Progress。

完成后记录：

- 已实现能力；
- 当前表结构；
- 当前 API；
- 最终测试数量；
- 功能提交、PR 与合并提交；
- main 回归结果；
- 当前未实现范围。

### 32.6 ADR

新增数据库迁移 ADR，至少说明：

- 为什么在 F-005 引入 Flyway；
- 为什么不继续手工维护完整 init.sql；
- 为什么保留 Hibernate validate；
- 为什么 baseline 只显式开启一次；
- 迁移文件为何不可回写；
- 为什么使用独立 MySQL 测试库而不是 H2。

### 32.7 Feature Plan

开发期间本文件保留在：

```text
docs/plans/active/
```

完成并验收后移动到：

```text
docs/plans/completed/
```

归档时补充：

- 实际文件；
- 实际测试结果；
- 迁移结果；
- 手工验证结果；
- 实现提交；
- PR；
- 合并提交；
- 完成日期；
- 已知限制。

### 32.8 历史 Completed Plan

F-001 至 F-004 的 Completed Plan 是阶段历史记录，不因为 F-005 引入 Flyway 或复习模块而回写其当时的“不包含”章节。

---

## 33. 实施顺序

### 阶段 0：真实 Git 基线与计划提交

1. 在真实本地仓库确认 `main`；
2. 确认 HEAD 为预期基线；
3. 确认与远端同步；
4. 确认 working tree clean；
5. 创建 `feature/F-005-rolling-review`；
6. 将本计划加入 active 目录；
7. 提交计划文档。

建议计划提交：

```text
docs: add F-005 rolling review plan
```

### 阶段 1：Flyway 与测试库基础设施

1. 增加 Flyway 依赖；
2. 建立 V1；
3. 建立 V2；
4. 替换数据库创建脚本；
5. 增加测试数据库创建脚本；
6. 增加主配置和测试配置；
7. 验证空测试库 V1→V2；
8. 验证 Hibernate validate。

### 阶段 2：时间配置、枚举和数据实体

1. 增加 ReviewProperties；
2. 增加 Clock 配置；
3. 增加三个枚举；
4. 实现 QuestionReviewState；
5. 实现 ReviewRecord；
6. 实现两个 Repository；
7. 验证 @MapsId、约束和级联。

### 阶段 3：纯调度规则

1. 实现 ReviewSchedulingPolicy；
2. 实现不可变计算结果；
3. 完成四类评价与边界日期单测；
4. 在进入 Service 前固定全部状态转换。

### 阶段 4：Question 集成

1. 新题创建同步初始化状态；
2. 详情响应增加复习摘要；
3. 列表批量装配状态；
4. 增加 reviewStatus 查询参数；
5. 增加四种筛选组合查询；
6. 修改保持复习数据；
7. 删除依赖数据库级联；
8. 更新原 Question 测试。

### 阶段 5：复习 Service 与 API

1. 实现动态队列；
2. 实现 dueCount；
3. 实现评价事务；
4. 实现重新加入事务；
5. 实现历史插入；
6. 实现复习 Controller 与 DTO；
7. 实现异常与统一处理；
8. 验证响应隐藏字段。

### 阶段 6：并发与完整集成测试

1. 完成 Repository 测试；
2. 完成 Controller 测试；
3. 完成完整生命周期测试；
4. 完成修改与删除测试；
5. 完成独立事务乐观锁测试；
6. 完成重复/并发提交结果测试；
7. 执行全部回归测试。

### 阶段 7：现有开发库迁移验收

1. 备份；
2. 记录迁移前数据；
3. 显式 baseline；
4. 执行 V2；
5. 核对 Flyway 历史；
6. 核对状态行数；
7. 抽查日期；
8. 关闭 baseline 开关。

### 阶段 8：手工 API 验证

按第 31 节逐项执行并记录结果。

### 阶段 9：文档与功能分支收尾

1. 更新 README；
2. 更新 PRODUCT；
3. 更新 API 设计；
4. 更新数据库设计；
5. 更新项目状态；
6. 新增 Flyway ADR；
7. 更新本 Feature Plan 的实施结果；
8. 执行 `git diff --check`；
9. 再执行完整测试；
10. 提交并推送功能分支。

建议实现提交：

```text
feat: implement F-005 rolling review
```

### 阶段 10：PR 与 main 收尾

1. 创建 Pull Request；
2. 核对 PR diff 只包含 F-005；
3. 使用项目现有 Merge Commit 方式合并；
4. 切回 main 并同步远端；
5. 在 main 上执行全量测试；
6. 将计划归档到 completed；
7. 更新项目状态中的最终提交信息；
8. 完成必要归档提交；
9. 推送 main；
10. 删除已合并的本地和远程功能分支；
11. 确认 main 工作区 clean。

---

## 34. 验收标准

### 34.1 功能验收

- 新题次日首次复习；
- 旧题正确回填；
- 到期和逾期题进入队列；
- 未来题和已掌握题不进入队列；
- 队列排序稳定；
- subject 筛选正确；
- dueCount 包含当前题；
- 四类评价间隔正确；
- 第二次连续熟练进入已掌握；
- 非熟练评价清零连续次数；
- 重新加入当天到期；
- 修改保留状态和历史；
- 删除级联清理；
- 重复和并发提交只有一次成功。

### 34.2 API 验收

- 三个复习接口路径、方法和状态码正确；
- 空队列返回固定 200 JSON；
- 待复习响应不泄露受限字段；
- 详情接口继续返回完整内容；
- 现有 Question 响应增加四个复习字段；
- reviewStatus 可与 subject 组合；
- 成功响应时间格式正确；
- 错误继续使用 ApiErrorResponse；
- 已确认错误码均可触发。

### 34.3 数据库验收

- Flyway 是唯一业务表结构来源；
- V1、V2 能在空测试库执行；
- 已有库能通过一次性 baseline 升级；
- 两张表字段与 Entity 一致；
- 检查约束生效；
- 外键和级联生效；
- 索引存在；
- 每道题恰好一行状态；
- 历史结果字段正确；
- Hibernate validate 通过。

### 34.4 代码验收

- Controller 不含业务计算；
- ReviewService 控制事务；
- ReviewSchedulingPolicy 不访问数据库；
- Entity 不直接作为 API DTO；
- Question 不建立复习历史反向集合；
- 列表状态批量查询，无 N+1；
- Clock 可替换；
- 不依赖服务器默认时区；
- @Version 冲突正确映射；
- 无未使用依赖和无关框架。

### 34.5 测试验收

- 原 65 个测试全部通过；
- F-005 新增测试全部通过；
- 没有 skipped 测试；
- 干净 Maven 全量测试 BUILD SUCCESS；
- 测试连接独立测试库；
- 迁移、约束和并发使用真实 MySQL；
- 人工迁移验收完成；
- 手工 API 验证完成；
- 最终测试数量写入文档。

### 34.6 文档验收

- README 已更新；
- PRODUCT 已更新；
- API 设计已更新；
- 数据库设计已更新；
- 项目状态已更新；
- Flyway ADR 已新增；
- Feature Plan 已记录实际结果并归档；
- 文档不把后续功能写成已实现。

### 34.7 Git 验收

- 功能分支来自已验证 main；
- diff 仅包含 F-005；
- `git diff --check` 通过；
- 功能分支已推送；
- PR 已合并；
- main 已同步并重新全量测试；
- 最终提交 ID 已记录；
- 功能分支已清理；
- main working tree clean。

---

## 35. 风险与处理

### 35.1 错误 baseline 数据库

风险：自动 baseline 可能把结构不一致或连接错误的非空数据库登记为 V1。

处理：默认关闭，仅在备份和结构核对后显式开启一次，迁移完成立即关闭。

### 35.2 MySQL DDL 与数据恢复

风险：DDL 失败不能等同于普通业务事务回滚。

处理：迁移前备份，失败后保存日志并判断恢复，不通过手工补表掩盖迁移问题。

### 35.3 旧时间无时区

风险：旧 `DATETIME` 无法证明原始时区。

处理：按已有本地时间取 DATE 加一天，记录假设；不在 F-005 猜测转换。

### 35.4 状态行缺失

风险：直接 Repository 插题、迁移失败或手工 SQL 可能产生无状态题。

处理：业务创建在同一事务初始化；V2 全量回填；响应装配发现缺失时失败，不静默补默认状态。

### 35.5 乐观锁异常发生在 flush 或 commit

风险：如果只构造成功响应而没有让事务完成，可能误判并发成功。

处理：确保状态更新和历史插入在事务内 flush，统一映射 Spring/JPA 乐观锁异常并通过真实事务测试。

### 35.6 列表 N+1

风险：为每道题单独查询状态会随分页大小线性增加 SQL 次数。

处理：按当前页 ID 批量查询状态并使用 Map 装配。

### 35.7 多条件分页复杂度

风险：subject 和 reviewStatus 形成四种组合。

处理：沿用当前显式 JPQL ID 分页方式，不临时引入新查询框架。

### 35.8 dueCount 与下一题一致性

风险：计数和第一题使用两个查询，并发修改可能改变瞬时结果。

处理：两者放在同一只读事务，当前单用户低并发场景不引入快照表或复杂锁；提交后客户端始终重新获取动态队列。

### 35.9 答案泄露

风险：复用 QuestionDetailResponse 会提前返回答案或知识点。

处理：使用专用 DueQuestionResponse，并对字段不存在做 Controller JSON 断言。

### 35.10 测试误连开发库

风险：测试启动 Flyway 或写测试数据到日常库。

处理：测试资源明确指向 `wrong_question_system_test`，在测试和文档中验证实际数据库名。

### 35.11 Flyway checksum

风险：执行后修改 V1/V2 导致其他环境校验失败。

处理：已执行迁移不回写，后续变化新增版本。

### 35.12 范围膨胀

风险：复习功能容易继续扩展到历史页面、统计、抽查和算法。

处理：严格按本计划实现；这些能力留到后续 Feature。

---

## 36. 已确认决策汇总

1. F-005 采用固定规则滚动复习核心闭环。
2. 评价为不会、模糊、基本掌握、熟练。
3. 对应间隔为 1、3、7、14 天。
4. 下一次日期从实际完成日计算。
5. 连续两次熟练进入 MASTERED。
6. 其他评价清零连续熟练次数。
7. 时区未来用户可配置，但 F-005 使用后端配置，默认 Asia/Shanghai。
8. 已有错题按原 `created_time` 的日期加一天回填首次复习日期。
9. 队列包含逾期题。
10. 队列最早到期优先，同日 ID 升序。
11. 队列动态逐题获取。
12. 查看答案复用错题详情接口。
13. 只允许评价已到期或逾期 active 题。
14. 修改错题保留进度和历史。
15. 删除错题级联删除状态和历史。
16. 已掌握题可手动重新加入，当天到期。
17. 重新加入保留 lastReviewedAt 并记录 REACTIVATION。
18. 新题首次复习为配置时区下的创建业务日期加一天。
19. 队列支持全部科目和可选科目筛选。
20. 返回 dueCount，包含当前题。
21. 历史只保存，不提供查询接口。
22. 历史不保存题目内容快照。
23. 重复和并发提交只允许首次成功。
24. 现有 Question 响应统一增加复习摘要。
25. Question 分页支持 reviewStatus 与 subject 组合。
26. 状态枚举为 ACTIVE / MASTERED。
27. 历史事件为 EVALUATION / REACTIVATION。
28. 使用独立状态表和历史表。
29. 引入 Flyway，并以 Flyway 为唯一结构来源。
30. 采用一次性显式 baseline 接管旧库。
31. 使用 JPA @Version 乐观锁。
32. 使用配置时区和注入 Clock。
33. 复习 API 使用独立 `/api/reviews` 三接口。
34. 空队列返回 200、dueCount 0、question null。
35. 四级评价使用已确认英文枚举。
36. 待复习题只返回题目、图片、科目和到期日。
37. 成功响应采用已确认的顶层字段结构。
38. 错误状态和错误码采用已确认契约。
39. Java 使用独立 review 模块和具体规则类。
40. 复习实体单向指向 Question。
41. 间隔和阈值是规则类常量，只有时区外部配置。
42. 集成测试使用独立本地 MySQL 测试库。
43. 迁移采用自动测试空库和人工验收旧库升级。
44. 乐观锁采用独立事务与重复提交两层真实验证。
45. 原有 65 个测试必须全部回归通过。

---

## 37. 完成定义

只有同时满足以下条件，F-005 才能标记为 Completed：

- 本计划的包含范围全部实现；
- 不包含范围没有被擅自加入；
- 业务规则与状态转换全部通过测试；
- Flyway V1、V2 和测试库配置完成；
- 现有开发库完成备份、baseline、V2 和数据核对；
- API 契约和错误码一致；
- 状态与历史事务保持原子性；
- 并发测试证明只发生一次成功推进；
- 原 65 个测试和全部新增测试通过；
- 手工 API 验证通过；
- 所有要求文档同步；
- Flyway ADR 完成；
- Feature Plan 补充实际结果并移入 completed；
- 功能分支完成提交、推送和 PR；
- PR 通过 Merge Commit 合并到 main；
- 合并后的 main 再次全量测试通过；
- main 与远端同步；
- 功能分支清理；
- 最终 main working tree clean；
- 最终提交、PR、合并提交和测试数量均已记录。

在以上条件全部满足前，`docs/project-status.md` 不得把 F-005 标记为 Completed。

---

## 38. 实施与验收记录

### 38.1 已写入功能分支的实现

2026-09-04 已在基于 `bba3122` 的功能分支和计划提交 `d5187f3` 上完成实现：

- Flyway 依赖、V1、V2；
- 开发库与测试库创建脚本；
- 独立测试数据源；
- 时区配置与可注入 Clock；
- ReviewStatus、ReviewRating、ReviewEventType；
- QuestionReviewState、ReviewRecord；
- `@MapsId` 共享主键与 `@Version` 乐观锁；
- ReviewSchedulingPolicy；
- Review Repository、Service、Controller 和 DTO；
- 三个复习 API；
- Question 创建、响应摘要和状态分页集成；
- 统一复习错误响应；
- 调度、时间、Service、Repository、Controller、生命周期和并发测试代码；
- README、PRODUCT、API、数据库、项目状态和 ADR 文档。

### 38.2 生成阶段的静态验证

生成环境已完成以下静态验证和修正：

- `pom.xml` 通过 XML 解析；
- 主配置和测试配置通过 YAML 解析；
- 测试配置显式固定为 `wrong_question_system_test`，并补齐用户名、
  密码、Hibernate Validate 和 JDBC UTC 配置；
- Flyway 迁移文件的语句边界和 V1/V2 职责已核对；
- 已检查 F-005 Java 文件的重复类型和可能未使用 import，并删除发现的
  未使用 import；
- 评价与重新加入操作改为从同一 `Instant` 推导业务日期，避免午夜边界
  出现日期与事件时刻不一致；
- 新增测试数据库名称断言，降低测试误连日常开发库的风险；
- 修正 PRODUCT 和数据库设计中与 F-005 当前设计冲突的历史表述。

生成环境不能完成可信 Java/MySQL 验证，原因是：

- 环境只有 JDK 17，项目要求 JDK 21；
- Maven Central 在生成环境中不可解析；
- 没有本地 MySQL 和 `DB_PASSWORD`。

已实际尝试：

```text
sh ./mvnw -DskipTests compile
```

Maven 在解析父 POM 时因 `repo.maven.apache.org` 无法解析而失败，没有进入
Java 源码编译阶段。该结果未被当作测试通过；后续结果均来自用户本地
Java 21 与真实 MySQL 环境。

### 38.3 本地自动化测试

用户本地环境：

- Java 21.0.12；
- Maven Wrapper 3.9.16；
- Spring Boot 4.1.1；
- MySQL 9.6；
- 独立测试库 `wrong_question_system_test`。

第一次完整测试实际运行 113 个测试，出现 3 个失败和 2 个错误。根据真实
输出完成以下修正：

- 删除级联测试在断言数据库结果前清空 JPA 持久化上下文，避免一级缓存
  返回已被数据库级联删除的旧实体；
- 时间相关测试只读取一次可变测试 Clock，避免同一断言中的时间漂移；
- MySQL CHECK 约束测试按 Spring 数据访问异常和 MySQL 错误码 `3819`
  验证，不依赖可能随 Hibernate 版本变化的具体异常子类。

随后定向运行 16 个测试，发现 Java `Instant` 纳秒精度与 MySQL
`DATETIME(6)` 微秒精度不一致。实现改为在持久化复习事件时间前统一截断到
微秒，测试也按同一持久化精度构造预期值。

最终实际执行：

```text
.\mvnw.cmd clean test
```

结果：

- 编译 47 个生产源码文件和 16 个测试源码文件；
- Tests run：113；
- Failures：0；
- Errors：0；
- Skipped：0；
- BUILD SUCCESS；
- 总耗时 32.078 秒；
- 原有 65 个测试和 F-005 新增 48 个测试全部通过；
- Flyway 空库迁移、Hibernate Validate、Repository 约束和两个独立事务
  并发测试均在真实 MySQL 上通过。

测试日志包含 Flyway 对 MySQL 9.6 高于其已验证版本 9.4 的兼容性提示，
以及 Mockito 动态加载 agent 的未来兼容性提示；两者均未造成测试失败。

### 38.4 已有开发库迁移验收

迁移前只读核对确认 `wrong_question_system` 只有 F-004 的三张业务表，
数据量为 `question=0`、`knowledge_point=4`、
`question_knowledge_point=0`，表结构、索引和外键与 V1 一致。

迁移前创建了可恢复的无 GTID 备份：

```text
D:\Projects\wrong-question-system-backups\wrong_question_system-before-F005-no-gtid-20260904-071220.sql
SHA-256: AC325FD38E9FA10669D507C8DE0EE99C282D1B0FD31E20F3B4BE759B743951D0
```

为验证旧题回填，迁移前创建一条受控夹具：题目 ID 为 `45`，创建时间为
`2026-08-31 10:20:30`，并保留一条知识点关联。随后仅本次启动显式设置
`FLYWAY_BASELINE_ON_MIGRATE=true`，实际结果为：

- Flyway 成功把已有三表结构登记为版本 1；
- V2 `add rolling review` 成功执行；
- Hibernate EntityManagerFactory 成功初始化，应用成功启动；
- Flyway 历史包含成功的 V1 BASELINE 和 V2 SQL；
- 夹具获得唯一 ACTIVE 状态，`next_review_date=2026-09-01`，与
  `DATE(created_time) + 1 day` 一致；
- 原题和知识点关联保持不变；
- 状态队列索引、历史时间索引、科目索引以及两张新表的 PK、FK、CHECK
  约束均存在；
- `FLYWAY_BASELINE_ON_MIGRATE` 已恢复为未设置状态。

### 38.5 手工 API 验收

通过真实 HTTP 请求执行 16 步手工验收，全部通过：

- 清理迁移夹具并验证数据库级联；
- 创建隔离知识树和新题，验证初始复习摘要；
- 验证未到期排除、到期/逾期包含、最早到期与同日 ID 顺序；
- 验证包含当前题的 `dueCount`、科目筛选和空筛选结果；
- 复用错题详情接口查看答案；
- 分别验证四级评价的 1、3、7、14 天间隔；
- 验证提交成功后数量减少、重复提交冲突和 ACTIVE 重新加入冲突；
- 验证连续两次熟练进入 MASTERED 以及分页状态筛选；
- 验证已掌握题重新加入当天到期并保留 `lastReviewedAt`；
- 验证修改错题保留状态与历史；
- 验证删除错题级联删除状态和全部历史。

脚本最终输出：

```text
F-005 MANUAL API VERIFICATION: PASSED
最终数量 question,knowledge_point,relation,state,history=0,4,0,0,0
```

迁移夹具和全部 API 临时数据均已清理。

### 38.6 尚待 Git 与合并验收

F-005 仍保持 In Progress，尚待：

1. 最终核对工作树范围并通过 `git diff --check`；
2. 提交和推送实现与文档；
3. 创建 PR，并通过 Merge Commit 合并到 `main`；
4. 在合并后的 `main` 再次执行全部 113 个测试；
5. 记录实现提交、PR、合并提交和 main 回归结果；
6. 将本计划移入 `docs/plans/completed/`；
7. 清理本地与远程功能分支；
8. 确认最终 `main` 工作树 clean 且与 `origin/main` 同步。
