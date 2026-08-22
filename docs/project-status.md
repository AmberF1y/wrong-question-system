# 错题整理 + 滚动复习系统

## 项目目标

开发一个用于个人学习的错题管理和滚动复习系统。

核心功能：

- 错题录入
- OCR识别题目
- 错题整理
- 知识点关联
- 滚动复习
- 掌握程度管理


## 当前阶段

F-001 项目初始化


## 技术栈

后端：

- Java 21
- Spring Boot 4
- Spring MVC
- Spring Data JPA
- MySQL 9.6
- Maven


开发工具：

- IntelliJ IDEA
- Navicat
- Git


## 已完成

### 项目初始化

- 创建 Spring Boot 项目
- 配置 Maven
- 配置 MySQL 数据库
- 配置环境变量 DB_PASSWORD

### 基础接口

完成健康检查接口：

GET /api/health


返回：

{
  "status": "ok"
}


### 测试

完成：

HealthControllerTest


## 当前数据库

数据库名：

wrong_question_system


字符集：

utf8mb4


## 下一阶段

F-002 数据库设计

计划：

- 设计错题核心实体
- 创建数据表
- 建立 JPA Entity
- 实现基础 CRUD