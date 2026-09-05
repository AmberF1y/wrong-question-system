# API 设计

## 1. 文档状态

- 当前版本：F-008 已在功能分支完成实现与验证，等待 Pull Request
- 基础路径：`/api`
- 数据格式：JSON；图片读取接口返回原始二进制
- 当前范围：健康检查、知识点管理、错题管理、固定规则滚动复习与题目图片

本文件记录已经确认并进入实现的 API。OCR、Dashboard、自适应复习和多图片
接口仍不在当前范围内。

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

---

## 11. 创建错题

### POST `/api/questions`

请求不提交 `subject`、`imagePath`、ID 或时间字段：

```json
{
  "questionText": "TCP 为什么需要拥塞控制？",
  "wrongAnswer": "未作答",
  "correctAnswer": "避免发送方使网络长期过载",
  "analysis": "拥塞控制面向整个网络负载。",
  "errorReason": "混淆了流量控制和拥塞控制",
  "knowledgePointIds": [3, 4]
}
```

成功状态：`201 Created`

```json
{
  "id": 8,
  "questionText": "TCP 为什么需要拥塞控制？",
  "wrongAnswer": "未作答",
  "correctAnswer": "避免发送方使网络长期过载",
  "analysis": "拥塞控制面向整个网络负载。",
  "errorReason": "混淆了流量控制和拥塞控制",
  "subject": "408",
  "imagePath": null,
  "knowledgePoints": [
    {
      "id": 3,
      "name": "TCP",
      "parentId": 2
    },
    {
      "id": 4,
      "name": "拥塞控制",
      "parentId": 3
    }
  ],
  "createdTime": "2026-09-02T20:10:30",
  "updatedTime": "2026-09-02T20:10:30"
}
```

规则：

- 五个文本字段均必填，并在保存前去除首尾空白；
- 正文内部空格和换行保持不变；
- 至少提交一个知识点 ID；
- 知识点 ID 不能为 `null`、不能重复且必须存在；
- 允许同时选择父知识点和子知识点；
- 只保存直接选择的知识点，不自动保存祖先；
- 所有知识点必须属于同一根节点；
- `subject` 自动取共同根节点名称。

---

## 12. 查询错题详情

### GET `/api/questions/{id}`

成功状态：`200 OK`

响应字段与创建成功响应相同，包括五个文本字段、只读 `imagePath`、直接关联知识点和时间字段。

知识点按 ID 升序返回。错题不存在时返回：

```text
404 QUESTION_NOT_FOUND
```

---

## 13. 分页查询错题

### GET `/api/questions`

请求示例：

```http
GET /api/questions?page=0&size=20&subject=408
```

参数：

| 参数 | 必填 | 默认值 | 规则 |
| --- | --- | ---: | --- |
| `page` | 否 | 0 | 从 0 开始，不能小于 0 |
| `size` | 否 | 20 | 1～100 |
| `subject` | 否 | 无 | 去除首尾空白后精确匹配 |

成功状态：`200 OK`

```json
{
  "items": [
    {
      "id": 8,
      "questionText": "TCP 为什么需要拥塞控制？",
      "subject": "408",
      "knowledgePoints": [
        {
          "id": 4,
          "name": "拥塞控制",
          "parentId": 3
        }
      ],
      "createdTime": "2026-09-02T20:10:30",
      "updatedTime": "2026-09-02T20:10:30"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

规则：

- 固定按 `question.id DESC` 排序；
- 不允许调用方自定义排序；
- 列表摘要不返回答案、解析、错误原因和图片路径；
- 超出范围的页和不存在的科目均返回 `200 OK` 与空 `items`；
- 空白 `subject` 或非法分页参数返回 400；
- F-004 不提供知识点筛选。

---

## 14. 修改错题

### PUT `/api/questions/{id}`

PUT 提交全部可编辑字段，请求结构与创建错题相同。

成功状态：`200 OK`

规则：

- 五个文本字段全部替换；
- 知识点关联集合全部替换；
- 根据新知识点集合重新计算 `subject`；
- 允许用另一棵知识树中的合法集合切换科目；
- `id`、`createdTime` 和 `imagePath` 保持不变；
- `updatedTime` 反映本次修改；
- 任一校验失败时整个事务回滚。

---

## 15. 删除错题

### DELETE `/api/questions/{id}`

成功状态：`200 OK`

```json
{
  "message": "错题删除成功"
}
```

删除采用真实删除。数据库通过 `ON DELETE CASCADE` 清理对应的
`question_knowledge_point`、`question_review_state` 和 `review_record`，但不会
删除任何知识点。错题存在图片时，数据库事务提交后同时删除对应文件和空的
题目图片目录。

---

## 16. F-004 文本长度

| 字段 | 最大长度 |
| --- | ---: |
| `questionText` | 10000 |
| `wrongAnswer` | 5000 |
| `correctAnswer` | 5000 |
| `analysis` | 10000 |
| `errorReason` | 2000 |

没有实际作答时，`wrongAnswer` 使用文字“未作答”，不能使用空字符串。

---

## 17. F-004 错误码

| 场景 | HTTP 状态 | code |
| --- | ---: | --- |
| 字段或分页参数校验失败 | 400 | `VALIDATION_FAILED` |
| 知识点 ID 重复 | 400 | `QUESTION_DUPLICATE_KNOWLEDGE_POINT` |
| 多个知识点跨科目 | 400 | `QUESTION_KNOWLEDGE_POINTS_CROSS_SUBJECT` |
| JSON 无法解析 | 400 | `MALFORMED_REQUEST_BODY` |
| 错题不存在 | 404 | `QUESTION_NOT_FOUND` |
| 知识点不存在 | 404 | `KNOWLEDGE_POINT_NOT_FOUND` |
| 数据库完整性约束冲突 | 409 | `DATA_INTEGRITY_CONFLICT` |

---

## 18. F-004 不提供的接口

F-004 不提供：

- PATCH；
- 批量创建、修改或删除；
- 知识点筛选和复杂搜索；
- 图片上传；
- OCR；
- 复习、统计和前端页面。

---

## 19. F-005 错题响应扩展

创建、详情、修改响应以及分页中的每个错题摘要统一增加：

```json
{
  "reviewStatus": "ACTIVE",
  "nextReviewDate": "2026-09-04",
  "consecutiveProficientCount": 0,
  "lastReviewedAt": null
}
```

`reviewStatus` 只可能是 `ACTIVE` 或 `MASTERED`。`MASTERED` 的 `nextReviewDate` 为 `null`。

错题分页增加可选 `reviewStatus` 参数，可与 `subject` 组合：

```http
GET /api/questions?page=0&size=20&subject=408&reviewStatus=MASTERED
```

仍保持 `question.id DESC` 排序。非法状态枚举返回 `400 VALIDATION_FAILED`。

---

## 20. 获取下一道待复习题

### GET `/api/reviews/due/next`

可选按科目精确筛选：

```http
GET /api/reviews/due/next?subject=408
```

有待复习题：

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

没有待复习题：

```json
{
  "dueCount": 0,
  "question": null
}
```

规则：

- 只选择 `ACTIVE` 且 `nextReviewDate <= 今天` 的题；
- `dueCount` 包含本次返回的当前题；
- 最早到期优先，同日按题目 ID 升序；
- `subject` 去除首尾空白后精确匹配，空白返回 400；
- 每次动态查询当前第一题，不保存会话或题单快照；
- 响应不暴露知识点、错误答案、正确答案、解析和错误原因。

---

## 21. 提交复习评价

### POST `/api/reviews/{questionId}/evaluations`

```json
{
  "rating": "BASICALLY_MASTERED"
}
```

允许的评价：

```text
NOT_KNOWN
FUZZY
BASICALLY_MASTERED
PROFICIENT
```

成功状态：`200 OK`

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

只有当前已到期或逾期的 `ACTIVE` 题允许评价。响应不自动携带下一题，客户端应再次调用待复习接口。

---

## 22. 重新加入复习

### POST `/api/reviews/{questionId}/reactivate`

请求不需要 JSON body。仅 `MASTERED` 题允许操作。

成功后：

- 状态变为 `ACTIVE`；
- 下一次复习日期为当天；
- 连续熟练次数归零；
- `lastReviewedAt` 保持不变；
- 写入 `REACTIVATION` 历史。

成功响应与评价响应结构相同，其中 `eventType` 为 `REACTIVATION`、`rating` 为 `null`。

---

## 23. F-005 复习错误码

| 场景 | HTTP 状态 | code |
| --- | ---: | --- |
| rating 为空、subject 空白、查询枚举非法 | 400 | `VALIDATION_FAILED` |
| JSON 损坏或 rating 枚举不存在 | 400 | `MALFORMED_REQUEST_BODY` |
| 错题不存在 | 404 | `QUESTION_NOT_FOUND` |
| 活动题尚未到期 | 409 | `REVIEW_NOT_DUE` |
| 已掌握题提交普通评价 | 409 | `REVIEW_ALREADY_MASTERED` |
| 活动题执行重新加入 | 409 | `REVIEW_NOT_MASTERED` |
| 并发请求持有过期版本 | 409 | `REVIEW_CONCURRENT_MODIFICATION` |

F-005 不提供复习历史查询、批量评价、撤销评价或专门的查看答案接口。

---

## 24. 上传或替换题目图片

### PUT `/api/questions/{questionId}/image`

请求使用：

```http
Content-Type: multipart/form-data
```

文件字段名固定为 `file`。成功状态：`200 OK`。

```json
{
  "questionId": 42,
  "imagePath": "questions/42/3ba68e3d-62a6-4fa0-bb6f-3e737bd87c11.png",
  "contentType": "image/png",
  "size": 102400
}
```

规则：

- 同一接口处理首次上传和替换；
- 支持 PNG、JPEG、WebP、GIF；
- 单文件最大 `20 * 1024 * 1024` 字节；
- 空文件、SVG 和无法识别的内容被拒绝；
- 服务端根据文件签名识别实际格式，不信任文件名、扩展名或客户端 MIME；
- 文件名由服务端 UUID 生成，客户端不能提交 `imagePath`；
- 替换成功后数据库指向新文件，旧文件在事务提交后删除；
- 数据库事务回滚时删除本次新文件。

---

## 25. 读取题目图片

### GET `/api/questions/{questionId}/image`

成功状态：`200 OK`，响应体为原始图片字节。

响应头：

```http
Content-Type: image/png | image/jpeg | image/webp | image/gif
Content-Disposition: inline
X-Content-Type-Options: nosniff
Cache-Control: no-store
```

规则：

- 先验证错题存在，再读取数据库保存的相对路径；
- 错题不存在返回 `404 QUESTION_NOT_FOUND`；
- 错题没有图片或对应文件不存在返回 `404 QUESTION_IMAGE_NOT_FOUND`；
- 响应不包含磁盘绝对路径；
- 前端详情和每日复习仅在 `imagePath` 非空时请求该接口；
- 图片失败只影响图片区域，文字内容仍保持可用。

---

## 26. 单独移除题目图片

### DELETE `/api/questions/{questionId}/image`

成功状态：`200 OK`。

```json
{
  "message": "题目图片移除成功"
}
```

数据库中的 `image_path` 置为 `NULL`，旧文件在事务提交后删除。文字、知识点和
复习状态不变。错题没有图片时返回 `409 QUESTION_IMAGE_NOT_ATTACHED`。

---

## 27. F-008 图片错误码

| 场景 | HTTP 状态 | code |
| --- | ---: | --- |
| 文件为空或未提交 | 400 | `QUESTION_IMAGE_EMPTY` |
| 文件签名无法识别或格式不支持 | 400 | `QUESTION_IMAGE_UNSUPPORTED_FORMAT` |
| 文件超过 20 MiB | 413 | `QUESTION_IMAGE_TOO_LARGE` |
| 错题不存在 | 404 | `QUESTION_NOT_FOUND` |
| 错题无图片或文件不存在 | 404 | `QUESTION_IMAGE_NOT_FOUND` |
| 重复移除空图片 | 409 | `QUESTION_IMAGE_NOT_ATTACHED` |
| 文件系统读写失败 | 500 | `QUESTION_IMAGE_STORAGE_FAILED` |

存储失败响应使用通用信息，不返回服务端绝对路径。

---

## 28. F-008 客户端请求顺序

创建带图片错题：

```text
POST /api/questions
→ 获得 questionId
→ PUT /api/questions/{questionId}/image
```

如果第二步失败，第一步创建的错题仍然存在，客户端不会重发 POST，而是进入
编辑页允许只重试图片。

修改时，文字与知识点仍使用 `PUT /api/questions/{id}`，图片上传/替换和移除
分别使用独立图片接口。只修改文字不会触碰图片；只修改图片时不重复提交未
变化的文字请求。
