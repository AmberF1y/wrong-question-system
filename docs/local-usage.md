# Windows 本地长期使用指南

## 1. 适用范围

本文用于 `v1.0.0` 的单用户 Windows 桌面端本地运行。应用使用本机 MySQL
保存结构化数据，使用仓库外目录保存题目图片。

默认约定：

```text
仓库：D:\Projects\wrong-question-system
图片：D:\WrongQuestionData\question-images
备份：D:\WrongQuestionBackups
数据库：wrong_question_system
时区：Asia/Shanghai
后端：http://127.0.0.1:8080
前端：http://127.0.0.1:5173
```

## 2. 首次使用前检查

需要安装并可从命令行调用：

- Java 21；
- Node.js 与 npm；
- MySQL Server；
- MySQL 客户端工具，包括 `mysqldump.exe`；
- Windows PowerShell 5.1。

数据库 `wrong_question_system` 必须已经创建。空库由 Flyway 自动建表；已有旧库
的首次 Flyway 接管必须按 `README.md` 的一次性 baseline 流程执行，不能由启动
脚本自动处理。

## 3. 日常启动

确认 MySQL 已启动，然后打开 PowerShell：

```powershell
Set-Location "D:\Projects\wrong-question-system"
$env:DB_PASSWORD = "<你的本地 MySQL 密码>"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-local.ps1"
```

启动脚本会：

1. 校验仓库、Java、Node、npm、Maven Wrapper、数据库密码和端口；
2. 创建仓库外正式图片目录；
3. 设置后端需要的图片目录和业务时区环境变量；
4. 分别打开后端和前端 PowerShell 窗口；
5. 等待后端健康检查和前端页面可访问；
6. 输出访问地址、图片目录和两个子进程 ID。

首次安装前端依赖时，脚本会在 `frontend/node_modules` 不存在的情况下执行
`npm.cmd ci`。如果明确不允许自动安装，可以使用 `-SkipNpmInstall`；此时缺少
依赖会直接报错。

覆盖默认路径或时区示例：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\start-local.ps1" `
    -ImageDirectory "E:\WrongQuestionData\question-images" `
    -ReviewZoneId "Asia/Shanghai"
```

`DB_PASSWORD` 只保存在当前 PowerShell 进程环境中。关闭该窗口后变量自然消失；
不要把真实密码写进脚本、Git 文件、命令参数或日志。

## 4. 日常停止

在启动脚本打开的后端窗口和前端窗口中分别按 `Ctrl+C`，等待两个进程退出。
备份前必须停止后端。第一版没有后台服务管理器，也不建议用按名称批量结束
Java、Node 或 PowerShell 进程。

## 5. 第一次真实使用

发布验证期间需要创建至少一道实际要保留的学习错题：

- 使用真实科目和知识点；
- 填写完整题目、错误答案、正确答案、解析和错误原因；
- 按需要上传一张真实题目图片；
- 在详情页确认内容；
- 在到期后从“每日复习”完成一次真实复习。

这条数据属于正式学习数据，不作为验收夹具删除，也不复制进 Git 或发布证据
目录。

## 6. 日常备份

先停止后端，再从仓库根目录执行：

```powershell
Set-Location "D:\Projects\wrong-question-system"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\backup-local.ps1"
```

`mysqldump` 会在控制台交互式询问 MySQL 密码。脚本默认读取：

- 数据库 `wrong_question_system`；
- 图片目录 `D:\WrongQuestionData\question-images`；
- 当前 Git 提交。

并在 `D:\WrongQuestionBackups` 下创建独立时间戳目录。成功目录包含：

```text
database.sql
question-images\...
backup-summary.txt
manifest.tsv
```

如果中途失败，目录名保留 `.incomplete-` 前缀，不得把它当成可恢复备份。

覆盖备份参数示例：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\backup-local.ps1" `
    -ImageDirectory "E:\WrongQuestionData\question-images" `
    -BackupRoot "E:\WrongQuestionBackups" `
    -DatabaseUser "root"
```

## 7. 备份清单复核

将 `$backupPath` 改为一次成功备份目录，然后执行：

```powershell
$backupPath = "D:\WrongQuestionBackups\wrong-question-system-<时间戳>"
$manifestPath = Join-Path $backupPath "manifest.tsv"
$rows = Import-Csv -LiteralPath $manifestPath -Delimiter "`t"

$failures = @(
    foreach ($row in $rows) {
        $path = Join-Path $backupPath $row.Path
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            "缺少文件：$($row.Path)"
            continue
        }

        $actualHash = (
            Get-FileHash -LiteralPath $path -Algorithm SHA256
        ).Hash.ToLowerInvariant()

        if ($actualHash -ne $row.Sha256) {
            "哈希不一致：$($row.Path)"
        }
    }
)

if ($failures.Count -eq 0) {
    "ManifestVerified=True"
} else {
    $failures
    throw "备份清单复核失败"
}
```

## 8. 人工恢复原则

恢复会覆盖正式数据，必须人工确认，不由仓库脚本自动执行。恢复前：

1. 停止后端和前端；
2. 复核 `manifest.tsv`；
3. 再为当前正式数据库和图片做一份安全备份；
4. 确认选择的是正确的历史备份目录；
5. 确认目标数据库为空，或已经明确批准覆盖现有数据。

数据库导入示例仅应在上述条件满足后执行：

```powershell
$backupPath = "D:\WrongQuestionBackups\wrong-question-system-<时间戳>"
$sqlPath = Join-Path $backupPath "database.sql"

cmd.exe /d /c `
    "mysql.exe --host=127.0.0.1 --port=3306 --user=root --password --default-character-set=utf8mb4 wrong_question_system < `"$sqlPath`""
```

图片恢复时，先把当前正式图片目录改名保存，不要直接合并覆盖；再把备份中的
`question-images` 整体复制为新的正式图片目录。启动应用后抽查图片路径与页面
显示。任何路径或数据库状态不明确时，应停止恢复并先核对。

## 9. 常见问题

### 端口已占用

启动脚本固定使用后端 `8080` 和前端 `5173`。先确认占用者并正常停止对应服务，
不要让 Vite 自动换端口，否则代理和文档地址会失去一致性。

### 后端健康检查超时

查看新打开的后端窗口。常见原因包括 MySQL 未启动、`DB_PASSWORD` 不正确、
数据库不存在或 Flyway 需要人工接管旧库。

### 前端页面可打开但显示后端未连接

先访问 `http://127.0.0.1:8080/api/health`。如果后端正常，再检查前端窗口中的
Vite 代理错误。

### 图片加载失败

确认启动时使用的是正式图片目录，并核对数据库 `image_path` 对应的相对文件
是否存在。不要为了消除错误手工清空数据库中的路径；应先找回文件或从已核验
备份恢复。

### 备份脚本拒绝运行

先确认后端已经停止且 `8080` 未监听，再检查 `mysqldump.exe` 是否在 `PATH`
中。备份根目录不能位于仓库或图片目录内部。
