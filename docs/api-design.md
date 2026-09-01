# API 设计

## 1. 文档状态

- 当前版本：F-003
- 基础路径：`/api`
- 数据格式：JSON
- 当前范围：健康检查与知识点管理

本文件记录已经确认并进入实现的 API，不记录未开始的错题、复习、图片或 OCR 接口。

---

## 2. 通用约定

### 2.1 Content-Type

带 JSON 请求体的接口使用：

```http
Content-Type: application/json
```

### 2.2 成功响应

成功响应直接返回对应 DTO，不增加通用 `data` 包装层。

### 2.3 错误响应

通用错误结构：

```json
{
  "timestamp": "2026-08-31T17:20:00Z",
  "status": 409,
  "code": "KNOWLEDGE_POINT_NAME_CONFLICT",
  "message": "同一父节点下已存在同名知识点",
  "path": "/api/knowledge-points"
}
```

Validation 失败时额外返回：

```json
{
  "timestamp": "2026-08-31T17:20:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "请求参数校验失败",
  "path": "/api/knowledge-points",
  "fieldErrors": {
    "name": "知识点名称不能为空"
  }
}
```

---

## 3. 健康检查

### GET `/api/health`

成功状态：`200 OK`

```json
{
  "status": "ok"
}
```

---

## 4. 查询完整知识树

### GET `/api/knowledge-points/tree`

成功状态：`200 OK`

```json
[
  {
    "id": 1,
    "name": "408",
    "parentId": null,
    "children": [
      {
        "id": 2,
        "name": "计算机网络",
        "parentId": 1,
        "children": []
      }
    ]
  }
]
```

规则：

- 一次返回完整知识树，不分页。
- 根节点和同级节点均按 ID 升序排列。
- 叶子节点返回 `children: []`。

---

## 5. 创建知识点

### POST `/api/knowledge-points`

请求体：

```json
{
  "name": "TCP",
  "parentId": 2
}
```

`parentId: null` 表示创建根节点；非空表示创建子节点。

成功状态：`201 Created`

```json
{
  "id": 3,
  "name": "TCP",
  "parentId": 2
}
```

---

## 6. 修改知识点

### PUT `/api/knowledge-points/{id}`

请求体提交完整可编辑状态：

```json
{
  "name": "TCP/IP",
  "parentId": 2
}
```

调用方应始终提交 `parentId` 字段：

- 根节点保持 `null`。
- 普通节点提交当前父节点或同一知识树中的新父节点 ID。

成功状态：`200 OK`

```json
{
  "id": 3,
  "name": "TCP/IP",
  "parentId": 2
}
```

移动限制：

- 只允许普通节点在同一根节点下移动。
- 禁止自引用和循环引用。
- 禁止跨根节点移动。
- 根节点不能变成子节点。
- 普通节点不能升级为根节点。

根节点改名时，相关错题的 `subject` 在同一事务中同步修改。

---

## 7. 删除知识点

### DELETE `/api/knowledge-points/{id}`

只有无子节点且无错题引用的知识点才能删除。

成功状态：`200 OK`

```json
{
  "message": "知识点删除成功"
}
```

接口不会递归删除子树，也不会自动解除错题关联。

---

## 8. 名称规则

- 创建和修改时去除首尾空格。
- 去除后不能为空。
- 不合并内部空格。
- 根节点最多 50 个字符。
- 普通节点最多 100 个字符。
- 根节点之间不能重名。
- 同一父节点下不能重名。
- 不同父节点下允许同名。
- 当前 MySQL 排序规则为 `utf8mb4_unicode_ci`，英文大小写不区分。

---

## 9. 状态码与错误码

| 场景 | HTTP 状态 | code |
| --- | ---: | --- |
| 字段校验失败 | 400 | `VALIDATION_FAILED` |
| JSON 无法解析 | 400 | `MALFORMED_REQUEST_BODY` |
| 知识点或父节点不存在 | 404 | `KNOWLEDGE_POINT_NOT_FOUND` |
| 根节点或同级名称冲突 | 409 | `KNOWLEDGE_POINT_NAME_CONFLICT` |
| 自引用 | 409 | `KNOWLEDGE_POINT_SELF_PARENT` |
| 循环引用 | 409 | `KNOWLEDGE_POINT_CYCLE` |
| 跨根节点移动 | 409 | `KNOWLEDGE_POINT_CROSS_TREE_MOVE_FORBIDDEN` |
| 非法改变根节点身份 | 409 | `KNOWLEDGE_POINT_ROOT_CHANGE_FORBIDDEN` |
| 存在子节点 | 409 | `KNOWLEDGE_POINT_HAS_CHILDREN` |
| 被错题引用 | 409 | `KNOWLEDGE_POINT_IN_USE` |
| 数据库完整性约束冲突 | 409 | `DATA_INTEGRITY_CONFLICT` |

---

## 10. F-003 不提供的接口

F-003 不提供：

- `GET /api/knowledge-points/{id}`；
- 错题 CRUD；
- 分页和搜索；
- 图片上传；
- OCR；
- 复习与统计接口。
