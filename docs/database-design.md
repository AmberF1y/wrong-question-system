# 数据库设计

## 当前版本

v1.0 MVP


## 数据库

wrong_question_system


## 数据表


# question

用于保存用户错题信息。


|字段           |类型        |说明    |
|-              |-          |-       |
|id             |BIGINT     |主键    |
|title          |TEXT       |题目    |
|wrong_answer   |TEXT       |错误答案|
|correct_answer |TEXT       |正确答案|
|analysis       |TEXT       |解析    |
|error_reason   |TEXT       |错误原因|
|subject        |VARCHAR(50)|科目    |
|knowledge_point|TEXT       |知识点  |
|created_time   |DATETIME   |创建时间|
|updated_time   |DATETIME   |更新时间|


## 设计说明

第一版本采用单表设计。

原因：

- 优先完成核心错题管理闭环
- 降低开发复杂度
- 后续根据需求拆分知识点、复习记录等模块


未来可能扩展：

- knowledge_point 知识点表
- review_record 复习记录表
- OCR图片表