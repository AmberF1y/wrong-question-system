# F-002 数据库设计


## 状态

Active


## 开始时间

2026-08-22


## 目标

完成错题整理 + 滚动复习系统 MVP 版本数据库设计。

本阶段优先实现最小可用版本（MVP），保证系统能够支持基础错题管理流程。

核心目标：

- 完成数据库结构设计
- 创建第一版数据库表
- 为后续后端开发提供数据模型基础


---

# 背景

项目第一阶段（F-001）已经完成：

- Spring Boot 项目初始化
- MySQL环境配置
- 基础接口测试
- 项目文档体系建立


进入业务开发阶段后，需要首先确定数据库结构。


当前开发策略：

> 先实现最小可用版本，再根据实际使用需求逐步扩展。


---

# MVP 功能范围

第一版数据库需要支持：

- 错题录入
- 错题查看
- 错题修改
- 错题删除


暂不实现：

- 自动复习算法
- OCR识别
- AI解析
- 复杂知识点统计


---

# 数据库设计方案


## 数据库

名称：

```
wrong_question_system
```


## 第一版数据表


### question

用于保存用户错题信息。


字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| title | TEXT | 题目 |
| wrong_answer | TEXT | 错误答案 |
| correct_answer | TEXT | 正确答案 |
| analysis | TEXT | 解析 |
| error_reason | TEXT | 错误原因 |
| subject | VARCHAR(50) | 科目 |
| knowledge_point | TEXT | 知识点 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |


---

# 当前任务


## 数据库设计

- [x] 完成数据库设计方案
- [ ] 编写建表 SQL
- [ ] 使用 Navicat 创建 question 表
- [ ] 验证数据库表结构


## 后端数据层准备

- [ ] 创建 Question Entity
- [ ] 配置 JPA 注解映射
- [ ] 创建 QuestionRepository


## 文档更新

- [x] 更新 database-design.md
- [ ] 更新 project-status.md


---

# 设计原则


## 1. 优先完成核心闭环

当前版本不追求复杂设计。

采用单表设计：

```
question
```


原因：

- 降低开发复杂度
- 快速实现可用系统
- 根据真实需求迭代


## 2. 保留扩展空间

未来根据需求增加：


### knowledge_point

用于：

- 知识点树管理
- 多知识点关联
- 薄弱知识点统计


### review_record

用于：

- 记录复习历史
- 保存掌握程度变化
- 实现滚动复习算法


### question_image

用于：

- 保存题目图片
- OCR识别


---

# 完成标准


F-002 完成需要满足：


## 数据库

- [ ] wrong_question_system 中存在 question 表
- [ ] 字段设计符合 database-design.md


## 后端

- [ ] Spring Boot 可以连接数据库
- [ ] Question Entity 可以正确映射数据库表
- [ ] Repository 可以访问 question 数据


## 项目管理

- [ ] 更新 project-status.md
- [ ] 将 F-002 移动至 completed
- [ ] 创建下一阶段开发计划