# Research: Sandbox 安全校验模块（第 24 节）

**Branch**: `024-lesson24-sandbox` | **Date**: 2026-08-12 | **Spec**: [spec.md](spec.md)

## 待解未知项与决策

本节的 Technical Context 无 NEEDS CLARIFICATION 标记（全部技术决策在 plan.md 的 Constitution Check 与 Summary 中落定）。以下记录 Phase 0 对两个关键设计问题相对代码库的核实结论。

## Decision 1: `ActionType` 保留四值，不改课件三值

**Decision**: 保留既有 `com.oryxos.tool.sandbox.ActionType` 四值枚举（`FILE_READ` / `FILE_WRITE` / `SHELL` / `HTTP_REQUEST`），不改动。

**Rationale**: 课件正文列出三值 `FILE_ACCESS / SHELL_EXEC / HTTP_REQUEST`，但 TechnicalSolution §6.7 明确给出四值（"ActionType 取四值（文件读 / 文件写 / Shell 命令 / HTTP 请求）——文件读写分开便于未来按读/写分权限；WhitelistSandbox 的 enforce 把 FILE_READ、FILE_WRITE 两 case 同路由到 checkFilePath"）。H0 核实：代码库 `ActionType.java` 已是四值；四个 Tool 的 `enforce` 调用与既有测试（`FileToolsTest` 用 `FILE_READ`/`FILE_WRITE`、`ShellToolsTest` 用 `SHELL`、`HttpToolsTest`/`NotifyToolsTest` 用 `HTTP_REQUEST`）全部按四值编写。改三值会破坏全部既有测试与前序交付物（软门禁 2/4 触发场景）。

**Alternatives considered**:
- 按课件三值改 → 破坏既有 `ActionType`、四个 Tool 调用、`SandboxPlaceholderTest`、四个既有 Tool 测试，且偏离 TechnicalSolution §6.7 刻意设计。否决。
- 三值 + 兼容别名 → 引入无谓复杂度，且既有调用用的是四值名，别名没有消费者。否决。

**Evidence**: `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/ActionType.java`（四值）；`docs/TechnicalSolution.md` §6.7（"ActionType 取四值"）；四个 Tool 源文件与对应测试的 `ActionType.X` 引用。

## Decision 2: 三个 `@ConfigurationProperties` record 的注册方式与默认配置值

**Decision**: 三个 record（`FileSandboxProperties` / `ShellSandboxProperties` / `HttpSandboxProperties`，prefix 分别为 `file` / `shell` / `http`）放 `com.oryxos.tool.sandbox`，在组合根 `CliSpringBootstrap` 的既有 `@EnableConfigurationProperties` 注解上追加注册。`application.yaml` 配宽松默认白名单。

**Rationale**: `CliSpringBootstrap` 已有 `@EnableConfigurationProperties(OryxOsProperties.class)`（第 16 节），同一注解追加三个 record 即可完成配置绑定，无需新增自动配置类。`WhitelistSandbox` 是 `@Component`，由既有 `@ComponentScan("com.oryxos.tool")` 扫入，其构造函数参数（三个 record）由配置绑定提供。默认白名单取宽松值（2026-08-12 澄清）：`file.allowed_paths` 覆盖工作区/当前目录、`shell.allowed_commands` 覆盖常用命令（如 `ls`/`cat`/`java`/`git`/`curl`/`echo` 等）、`http.allowed_domains` 覆盖现有依赖域名（如 `deepseek.com` 等），保证沙箱生效但默认不打断现有 chat/demo/notify 流程。

**Alternatives considered**:
- 在 sandbox 包内加 `@Configuration` + `@EnableConfigurationProperties` 自动配置类 → 增加一个本不需要的类，且违背"不新增自动配置类"的克制原则。否决。
- 默认白名单为空（全拒绝） → 最安全但现有工具全部被拦，打断开发体验；用户已明确选宽松默认。否决。
- 默认白名单只给最小示例集 → 部分演示仍可能失效。否决。

**Evidence**: `oryxos-cli/src/main/java/com/oryxos/cli/CliSpringBootstrap.java:71`（`@EnableConfigurationProperties(OryxOsProperties.class)`）；`@ComponentScan(basePackages = {"com.oryxos.tool", ...})`（第 70 行）。

## Decision 3: 接线改动为零，以测试加固与实现替换为主

**Decision**: 四个 Tool 的 `enforce` 接线不新增代码（已就位），本节新增实现 + 配置 + harness 测试。

**Rationale**: H0 核实四个 Tool 已在构造器注入 `Sandbox` 并在每个 `@Tool` 方法首行调用 `sandbox.enforce(...)`（`FileTools` 的 `readFile`/`writeFile`/`listDir`、`ShellTools.shell`、`HttpTools.httpGet`/`httpPost`、`NotifyTools.notify`）。课件 3.3 的"改法"在代码库已是既有形态。真正的行为变化来自 `NoOpSandbox`（默认放行）→ `WhitelistSandbox`（真实校验）的实现替换。

**Alternatives considered**:
- 重写四个 Tool 的接线 → 冗余且违反"不重复造轮子"。否决。

**Evidence**: 四个 Tool 源文件（构造器 + 每个 `@Tool` 方法首行 `sandbox.enforce`）。
