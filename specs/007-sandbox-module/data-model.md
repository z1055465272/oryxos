# Data Model: Sandbox 安全校验模块（第 24 节）

**Branch**: `024-lesson24-sandbox` | **Date**: 2026-08-12 | **Spec**: [spec.md](spec.md)

本模块无持久化数据（无表、无文件写入）。"数据模型"即运行时校验的动作描述与配置绑定。

## 实体：SandboxAction（校验动作，record）

- `type: ActionType`：动作类型（四值，见下）。
- `target: String`：动作目标——文件路径 / shell 命令 / HTTP URL，具体语义由 `type` 决定，实现类自己解释。
- 纯字符串 target 承载意图，不含"白名单/容器/镜像"等实现词。

## 实体：ActionType（动作类型，枚举）

| 值 | 含义 | WhitelistSandbox 路由 |
|----|------|----------------------|
| `FILE_READ` | 文件读 | `checkFilePath` |
| `FILE_WRITE` | 文件写 | `checkFilePath`（与 FILE_READ 同路由，为未来按读写分权限预留） |
| `SHELL` | Shell 命令 | `checkShellCommand` |
| `HTTP_REQUEST` | HTTP 请求 | `checkHttpUrl` |

## 实体：三块白名单配置（@ConfigurationProperties record）

| record | prefix | 配置键 | 语义 |
|--------|--------|--------|------|
| `FileSandboxProperties` | `file` | `file.allowed_paths`（List\<String\>） | 允许访问的路径根集合；为空 = 不允许任何路径 |
| `ShellSandboxProperties` | `shell` | `shell.allowed_commands`（List\<String\>） | 允许执行的命令首 token 集合；为空 = 不允许任何命令 |
| `HttpSandboxProperties` | `http` | `http.allowed_domains`（List\<String\>） | 允许访问的域名/通配符模式；为空 = 不允许任何域名 |

**配置边界语义（FR-009）**：配置项为空 = "什么都不允许"，不是"不校验"。

## 校验规则（WhitelistSandbox 内部）

| 规则 | 算法 |
|------|------|
| `checkFilePath` | `Path.of(raw).normalize().toAbsolutePath()` 后对任一白名单根做 `startsWith`；白名单根在构造时 `Path::of` + `normalize`。防 `../` 穿越（normalize 展开后落在根外则被拦）。**宪法 VI 加固**：目标存在时先 `toRealPath()` 解析真实路径再 `startsWith`；目标不存在时沿父目录链向上找最近存在祖先 `toRealPath()` 后再 `startsWith`——防符号链接逃逸 |
| `checkShellCommand` | `command.trim().split("\\s+")[0]` 取首 token，`allowedCommands.contains` 精确比对 |
| `checkHttpUrl` | `URI.create(url).getHost()` 取 host；模式 `*.` 前缀走 `host.endsWith(pattern.substring(1))`（带点号边界，`evil-example.com` 不命中 `example.com`），否则 `host.equals(pattern)` |
| 失败 | 抛 `SandboxViolationException(message)`，message 为可读原因（如 "命令不在白名单内: rm"） |

## 状态/边界

| 场景 | 行为 |
|------|------|
| 配置项为空 | 所有动作被拦（任何路径/命令/域名都不允许） |
| 相对路径含 `..` | normalize 后落在白名单根外的被拦 |
| 命令带前导空格 | `trim()` 后再取首 token，正常解析 |
| 域名 `*.example.com` 通配 | 命中 `api.example.com`、`sub.api.example.com`；不命中 `evil-example.com`、`example.com`（无点号前缀的裸域用 `equals` 单独匹配） |
| 校验失败 | 抛 `SandboxViolationException`，由 `ToolExecutor` 既有失败路径记 `tool_invocations`（`success=false`） |
