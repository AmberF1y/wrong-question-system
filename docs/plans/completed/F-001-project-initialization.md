# F-001 项目初始化


## 状态

Completed


## 完成时间

2026-08-22


## 目标

完成错题整理 + 滚动复习系统项目初始化。

建立基础开发环境、项目结构、后端框架、数据库环境以及开发规范，为后续业务功能开发提供基础。


---

# 完成内容


## 1. 项目仓库初始化

完成 Git 仓库创建：

- 初始化项目仓库
- 建立基础目录结构
- 配置 Git 分支开发流程


当前项目结构：

```
wrong-question-system

├── backend
├── docs
├── sql
├── .gitignore
└── ...
```


---

## 2. 后端项目初始化


完成 Spring Boot 后端项目创建。


技术栈：

- Java 21
- Spring Boot
- Maven
- Spring MVC
- Spring Data JPA
- MySQL


后端项目位置：

```
backend/
```


完成：

- Maven 配置
- Spring Boot 启动配置
- 项目依赖管理


---

## 3. 数据库环境配置


完成 MySQL 环境确认。


数据库：

```
wrong_question_system
```


字符集：

```
utf8mb4
```


排序规则：

```
utf8mb4_unicode_ci
```


完成：

- MySQL 服务启动确认
- 数据库创建
- Spring Boot 数据库连接配置


数据库密码通过环境变量管理：

```
DB_PASSWORD
```


---

## 4. 基础健康检查功能


完成系统健康检查接口。


接口：

```
GET /api/health
```


返回：

```json
{
  "status": "ok"
}
```


用途：

- 验证 Spring Boot 服务是否正常启动
- 作为后续部署和服务监控基础


---

## 5. 测试体系初始化


完成 Spring Boot 测试环境配置。


新增测试：

```
HealthControllerTest
```


测试内容：

- 模拟 HTTP 请求
- 验证健康检查接口返回结果


测试结果：

```
BUILD SUCCESS
```


---

## 6. 开发工具迁移


完成开发环境调整。


使用：

- IntelliJ IDEA
- Navicat
- Git


完成：

- IDEA Maven项目加载
- Maven依赖下载配置
- 开发环境代理配置
- 数据库连接工具配置


---

## 7. Git规范配置


完成：

```
.gitignore
```


忽略：

- IDEA配置文件
- VS Code配置文件
- Maven构建产物


避免开发环境文件进入版本控制。


---

# 文档体系建立


建立项目文档结构：

```
docs/

├── PRODUCT.md
├── project-status.md
├── database-design.md
├── api-design.md
│
├── decisions
│   └── ADR-001-initial-tech-stack-and-architecture.md
│
└── plans
    ├── active
    └── completed
```


用于：

- 记录项目状态
- 保存设计决策
- 管理开发任务
- 支持后续 GPT/Codex 辅助开发


---

# 产出文件


## 后端代码

```
backend/
```


包含：

- Spring Boot 主程序
- HealthController
- HealthResponse DTO
- Controller测试


## 文档

```
docs/
```


包含：

- 项目需求文档
- 项目状态文档
- 数据库设计文档
- API设计文档


## 数据库脚本目录

```
sql/
```


用于保存数据库初始化脚本。


---

# 遇到的问题及解决


## 1. MockMvc测试无法注入


问题：

```
No qualifying bean of type MockMvc available
```


原因：

测试环境没有正确加载 MockMvc 配置。


解决：

调整测试注解配置，使 Spring Boot 自动提供 MockMvc。


---

## 2. Spring Boot 无法连接数据库


问题：

```
Unable to determine Dialect without JDBC metadata
```


原因：

环境变量 DB_PASSWORD 未被 IDEA 进程读取。


解决：

重新启动 IDEA，使环境变量生效。


---

## 3. Maven依赖加载问题


问题：

IDEA无法正常刷新 Maven 项目。


原因：

网络代理配置问题。


解决：

配置 IDEA Maven 代理并重新加载 Maven 项目。


---

# 验收结果


F-001 已完成。


当前系统具备：

- 可运行 Spring Boot 后端
- 可连接 MySQL数据库
- 基础接口测试
- Git版本管理
- 项目文档管理体系


---

# 后续任务


进入：

```
F-002 数据库设计
```


目标：

- 完成 MVP 数据库设计
- 创建 question 表
- 编写数据库初始化 SQL
- 建立 Java Entity 映射