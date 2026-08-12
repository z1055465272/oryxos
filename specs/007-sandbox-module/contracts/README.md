# Contracts: Sandbox 安全校验模块（第 24 节）

**Branch**: `024-lesson24-sandbox` | **Date**: 2026-08-12 | **Spec**: [spec.md](../spec.md)

## Contract 1: Sandbox 接口（不变的墙）

```text
// com.oryxos.tool.sandbox（既有，只读契约）
enum ActionType { FILE_READ, FILE_WRITE, SHELL, HTTP_REQUEST }   // 既有四值，不改

record SandboxAction(ActionType type, String target) {}          // 既有，不改

interface Sandbox {
    void enforce(SandboxAction action);                          // 唯一入口，不表达任何一档实现
}
```

**行为契约**：`enforce` 校验不通过抛 `SandboxViolationException`（message 为可读原因）；通过则无副作用返回。签名不出现"白名单/容器/microVM/镜像"等实现词——用最重的 microVM 实现反向套此签名应能干净套入（接口中立性自查）。

## Contract 2: SandboxViolationException（失败信号）

```text
class SandboxViolationException extends RuntimeException {
    SandboxViolationException(String message);  // message = 可读失败原因，如 "命令不在白名单内: rm"
}
```

**行为契约**：普通 `RuntimeException`，从 Tool 的 `@Tool` 方法抛出，被 `ToolExecutor.execute` 既有 catch 接住，按普通工具失败记 `tool_invocations`（`success=false`、`error_message=message`）。不新增独立审计路径。

## Contract 3: WhitelistSandbox（核心阶段唯一实现）

```text
@Component
class WhitelistSandbox implements Sandbox {
    WhitelistSandbox(FileSandboxProperties fileProps,
                     ShellSandboxProperties shellProps,
                     HttpSandboxProperties httpProps);
}
```

**行为契约**：
- 按 `action.type()` 路由：`FILE_READ`/`FILE_WRITE` → `checkFilePath`；`SHELL` → `checkShellCommand`；`HTTP_REQUEST` → `checkHttpUrl`。
- 三个校验方法均为 `private`，不暴露在接口契约上（接口不被第一档实现带偏）。
- `checkFilePath`：normalize + toAbsolutePath 后 startsWith 白名单根，防 `../` 穿越；并叠 `toRealPath()` 真实路径校验（目标存在解析真实路径、不存在向上找最近存在祖先解析）——防符号链接逃逸（宪法 VI）。
- `checkShellCommand`：命令首 token 白名单精确比对。
- `checkHttpUrl`：host 通配符匹配，`*.` 前缀带点号边界（`evil-example.com` 不命中 `*.example.com`）。
- 配置为空 = 什么都不允许。

## Contract 4: 三个 @ConfigurationProperties record

```text
@ConfigurationProperties(prefix = "file")  record FileSandboxProperties(List<String> allowedPaths)
@ConfigurationProperties(prefix = "shell") record ShellSandboxProperties(List<String> allowedCommands)
@ConfigurationProperties(prefix = "http")  record HttpSandboxProperties(List<String> allowedDomains)
```

**行为契约**：在组合根 `CliSpringBootstrap` 的 `@EnableConfigurationProperties` 上追加注册；`application.yaml` 配置 `file.allowed_paths` / `shell.allowed_commands` / `http.allowed_domains`。默认值为宽松白名单（2026-08-12 澄清），沙箱生效但不打断现有流程。

## Contract 5: 四 Tool 接线点（既有，不改）

```text
// 每个 @Tool 方法首行（既有形态，零新增代码）
sandbox.enforce(new SandboxAction(ActionType.X, target));
```

`FileTools`（`readFile`/`writeFile`/`listDir` 用 `FILE_READ`/`FILE_WRITE`）、`ShellTools`（`shell` 用 `SHELL`）、`HttpTools`（`httpGet`/`httpPost` 用 `HTTP_REQUEST`）、`NotifyTools`（`notify` 用 `HTTP_REQUEST`）。校验失败直接抛异常，后面真正的 IO 代码一行不改。
