# 错题整理与滚动复习前端

基于 Vue 3、TypeScript、Vite 和 Element Plus 的桌面优先前端。

## 本地运行

先启动位于 `backend` 目录的 Spring Boot 后端，再运行：

```powershell
cd D:\Projects\wrong-question-system\frontend
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`，并将 `/api` 请求代理到
`http://localhost:8080`。

## 验证

```powershell
npm run type-check
npm run test:unit -- --run
npm run build
```
