# F-009 数学公式渲染实施计划

## 1. 状态

| 项目 | 内容 |
| --- | --- |
| Feature | F-009 |
| 名称 | 数学公式渲染 |
| 状态 | Completed |
| 基线 | `main@62cd59fc2a46811b3c3c05a9c569873785e4e5f6` |
| 功能分支 | `feature/F-009-math-formula-rendering` |
| 目标环境 | Windows 桌面浏览器、Vue 3 前端 |

F-009 已完成实现、自动化验证和真实浏览器人工验收。本计划归档于
`docs/plans/completed/`。

---

## 2. 目标与范围

用户可以在以下五个既有纯文本字段中混合输入中文、普通文本和 LaTeX 数学公式：

- 题目；
- 我的错误答案；
- 正确答案；
- 解析；
- 错误原因。

公式在创建/编辑表单中即时预览，并在错题列表、错题详情和每日复习流程中
统一渲染。数据库继续保存用户输入的原始文本，不保存 KaTeX HTML。

F-009 不包含：

- OCR、自动解题或 AI 生成公式；
- Markdown 编辑器或富文本编辑器；
- 后端 API、DTO、Entity、Flyway 或数据库结构修改；
- 用户输入 HTML、脚本、样式、外部图片或危险链接；
- 公式搜索、公式编辑器按钮面板或所见即所得排版；
- `v1.0.0` 标签、Release、提交或推送操作。

---

## 3. 语法约定

| 用途 | 语法 | 示例 |
| --- | --- | --- |
| 行内公式 | `$...$` | `当 $x\to0$ 时` |
| 独立公式 | `$$...$$` | `$$\frac{a}{b}$$` |
| 美元符号 | `\$` | `价格为 \$5` |
| 普通文本 | 不加分隔符 | `原有换行继续保留` |

解析规则：

- `$$...$$` 优先作为独立公式块识别，`$...$` 作为行内公式识别；
- 被反斜杠转义的 `\$` 只显示为美元符号，不开启或关闭公式；
- 普通文本和公式可以出现多次并相互混排；
- 普通文本中的换行原样保留；
- 未配对的 `$`、未配对的 `$$`、空缺结束符和无法解析的 LaTeX 回显用户原文；
- 单个公式失败只影响该片段，不影响同一字段的其他文本和整个页面。

---

## 4. 必须支持的数学内容

KaTeX 渲染至少覆盖：

- 上标、下标、分式、根式；
- 极限、积分、二重积分、求和与乘积；
- 无穷、偏导数、一阶和高阶导数；
- 向量、常见希腊字母；
- 不等式与集合关系；
- `bmatrix` 矩阵、`vmatrix` 行列式、`cases` 分段函数；
- `\cdots` 等省略号。

以下八个泰勒展开式是固定自动化验收样例：

```text
$$e^x=1+x+\frac{x^2}{2}+\frac{x^3}{6}+\cdots$$
$$\sin x=x-\frac{x^3}{6}+\frac{x^5}{120}+\cdots$$
$$\cos x=1-\frac{x^2}{2}+\frac{x^4}{24}+\cdots$$
$$\ln(1+x)=x-\frac{x^2}{2}+\frac{x^3}{3}-\frac{x^4}{4}+\cdots$$
$$\frac{1}{1-x}=1+x+x^2+x^3+\cdots$$
$$(1+x)^\alpha=1+\alpha x+\frac{\alpha(\alpha-1)}{2}x^2+\cdots$$
$$\arctan x=x-\frac{x^3}{3}+\frac{x^5}{5}+\cdots$$
$$\arcsin x=x+\frac{x^3}{6}+\frac{3x^5}{40}+\cdots$$
```

---

## 5. 前端设计

### 5.1 依赖与样式

- `katex` 作为正式前端依赖写入 `package.json` 和 `package-lock.json`；
- `katex/dist/katex.min.css` 只在 `src/main.ts` 全局引入一次；
- 不引入 MathJax、Markdown 或富文本依赖。

### 5.2 共享组件

`MathText.vue` 负责：

1. 扫描用户原始字符串并识别转义美元符号；
2. 将普通文本和公式分成独立片段；
3. 对每个完整公式单独调用 `katex.renderToString`；
4. 失败时捕获异常并回显该公式的完整原始文本；
5. 对独立公式提供容器内横向滚动，避免撑破卡片或页面。

KaTeX 固定配置：

```ts
{
  displayMode,
  output: 'htmlAndMathml',
  throwOnError: true,
  trust: false,
}
```

### 5.3 页面接入

- `QuestionForm.vue`：折叠式输入说明和五字段即时预览；
- `QuestionListView.vue`：题目摘要；
- `QuestionDetailView.vue`：题目、错误答案、正确答案、解析、错误原因；
- `ReviewQuestionCard.vue`：每日复习题目；
- `ReviewAnswerPanel.vue`：每日复习的四个答案与复盘字段。

创建和修改页面都复用 `QuestionForm.vue`，因此说明与预览行为保持一致。

---

## 6. 安全边界

- 不对整段用户输入使用 `v-html`；
- 普通文本只通过 Vue 文本插值输出，HTML 特殊字符由 Vue 转义；
- 只有 `katex.renderToString` 的成功结果允许进入局部 `v-html`；
- `trust: false` 不允许 KaTeX 信任链接、HTML 扩展或外部资源命令；
- 非法或不受信任的公式命令在 `throwOnError: true` 下进入原文回退；
- 用户输入的 `<script>`、`<style>`、事件属性、图片和链接不得成为 DOM 节点；
- 生成的 HTML 仅用于当前前端展示，不进入 API 请求或数据库；
- 不修改后端验证长度、字段必填规则和现有 trim 行为。

---

## 7. 自动化测试方案

`MathText.spec.ts` 覆盖：

- 无公式普通文本；
- 行内公式和独立公式；
- 中文、普通文本、多个公式混排；
- 普通文本换行；
- `\$` 转义；
- 未配对 `$` 与未配对 `$$`；
- 错误 LaTeX 的可见原文回退；
- script、style、事件属性、图片与危险链接输入不成为 DOM；
- 常见考研数学语法；
- 八个固定泰勒展开式逐一成功渲染；
- HTML 与 MathML 两种输出均存在。

页面和表单测试覆盖：

- 表单说明默认折叠、预览随输入更新、提交仍为原始 LaTeX；
- 详情页五个字段都由共享组件渲染；
- 每日复习在查看答案前渲染题目，查看后渲染四个复盘字段；
- 既有图片、复习状态、错误恢复与 CRUD 测试不删除、不跳过、不弱化。

最终执行：

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm.cmd run test -- --run
npm.cmd run type-check
npm.cmd run build

cd ..
git diff --check
git status --short
git diff --stat
```

---

## 8. 验收标准

- 五个文本字段均可混排普通文本和公式；
- 创建、修改、列表、详情和每日复习使用一致渲染逻辑；
- 原有纯文本显示与换行不回退；
- 行内、独立、多个公式和八个泰勒展开式正确渲染；
- 转义、未配对和错误公式均显示可理解的原始文本；
- 用户 HTML、脚本、样式、事件属性和危险链接不执行；
- 长独立公式只在内容区域横向滚动；
- API 和数据库继续保存原始 LaTeX，无结构变更；
- 前端全量测试、类型检查、生产构建和后端回归通过；
- 浏览器人工验收完成，F-009 可标记为 Completed。

---

## 9. 当前进度

已完成代码实现、文档同步和自动化验证：

- 前端专项测试：4 个测试文件、63 个测试全部通过；
- 前端全量测试：17 个测试文件、113 个测试全部通过；
- 八个固定泰勒展开式逐一通过；
- `npm.cmd run type-check` 通过；
- `npm.cmd run build` 通过，Vite 转换 1730 个模块；
- 后端 `.\mvnw.cmd test`：146 个测试，0 failure、0 error、0 skipped，
  `BUILD SUCCESS`；
- `git diff --check` 通过。

已知非阻断提示：

- Vite 继续报告主 chunk 超过 500 kB；
- Flyway 提示 MySQL 9.6 高于其已验证的 MySQL 9.4；
- Mockito/Byte Buddy 提示未来 JDK 将限制动态 agent 加载；
- Git 在 Windows 上提示部分 LF 工作区文件下次可能转换为 CRLF。

用户已于 2026-09-09 确认完成真实浏览器人工验收。因此 F-009 标记为
Completed，计划归档至 completed 目录。
