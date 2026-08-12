# Implementation Plan: Sandbox 安全校验模块（第 24 节）

**Branch**: `024-lesson24-sandbox` | **Date**: 2026-08-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-sandbox-module/spec.md`

## Summary

交付 Sandbox 安全校验能力：把第 23 节评审定下的"接口先行"白名单沙箱落成代码。`Sandbox` 接口只有一个方法 `enforce(SandboxAction)`（动作类型 + 动作目标），不携带任何一档实现的词；核心阶段唯一实现 `WhitelistSandbox` 读 `file.allowed_paths` / `shell.allowed_commands` / `http.allowed_domains` 三块配置，按 `ActionType` 路由到三个私有校验方法（路径标准化防穿越、命令首 token 白名单、域名带点号边界的通配符匹配）。校验失败抛 `SandboxViolationException`，被 `ToolExecutor`（17 节交付）既有失败路径接住，按普通工具失败写 `tool_invocations`（`success=false`、`error_message=原因`），不新增审计逻辑。接线点：`FileTools`/`ShellTools`/`HttpTools`/`NotifyTools` 的 `execute` 首行调 `sandbox.enforce(...)`。

现状与课件差异（已在 H0 核实并记录）：`com.oryxos.tool.sandbox` 包已存在占位类型（`Sandbox`/`SandboxAction`/`ActionType`/`NoOpSandbox`，20/23 节已建），四个 Tool 已在构造器注入 `Sandbox` 并调用 `enforce`——本节实际做的是：新增 `SandboxViolationException`、三个 `@ConfigurationProperties` record、`WhitelistSandbox`，删除 `NoOpSandbox` 占位换成 `WhitelistSandbox`，补齐配置键与 harness 测试。`ActionType` 保留既有四值设计（`FILE_READ/FILE_WRITE/SHELL/HTTP_REQUEST`），与 TechnicalSolution §6.7 及既有代码/测试一致；课件正文三值写法视为示意图。

## Technical Context

**Language/Version**: Java 21（virtual thread 并发，本项目固定）

**Primary Dependencies**: Spring Boot 3.x（`@ConfigurationProperties` 配置绑定、`@Component` 组件扫描）；校验逻辑全用 JDK 内置（`java.nio.file.Path`、`java.net.URI`），无新增第三方依赖。

**Storage**: 无持久化新增。三块白名单配置在 `oryxos-boot/src/main/resources/application.yaml`（`file.allowed_paths` / `shell.allowed_commands` / `http.allowed_domains`），无新表。

**Testing**: JUnit 5 + Mockito（`spring-boot-starter-test`）。`WhitelistSandboxTest` 直接 `new WhitelistSandbox(fileProps, shellProps, httpProps)` 测三类校验 + 穿越/通配符边界；四个 Tool 拦截回归用 mock `Sandbox` 现有测试（`FileToolsTest`/`ShellToolsTest`/`HttpToolsTest` 已有"白名单外被拦 + IO 未发生"用例，`NotifyToolsTest` 已有 enforce 顺序断言），并新增验证"真实 IO 未发生"的接线回归。

**Target Platform**: 本地 CLI / 服务器（`oryxos-chat` / `serve` / `gateway` 共用 `CliSpringBootstrap` 组合根）

**Project Type**: Java 多模块 Maven 单体

**Performance Goals**: 每次工具执行首行过一次路径/命令/域名比对，常数级开销可接受

**Constraints**: 全部实现落在 `oryxos-tool` 模块（`com.oryxos.tool.sandbox`），不往 `oryxos-core` 塞新概念；`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）

**Scale/Scope**: 单档白名单实现（劝阻级），单机；容器/microVM 留扩展阶段

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 |
|------|------|
| I. 自实现 ReAct Loop | 不触碰 `ReActLoop`；只在 `ToolExecutor` 下方的 `Sandbox` 实现与 Tool 首行接线。通过 |
| II. Spring AI 只用两件事 | 不涉及（Sandbox 不用 `@Tool`、不引入自动工具执行）。通过 |
| III. Provider 显式映射 | 不涉及。通过 |
| IV. 目录=Agent / Skill 加载 | 不涉及。通过 |
| V. 审计 Day One | `SandboxViolationException` 从 Tool 抛出 → `ToolExecutor.execute` 既有 catch 落 `tool_invocations`（`success=false`），复用失败路径，不新增独立审计。通过 |
| VI. Sandbox | 本节即宪法 VI 的实现本身：接口 `Sandbox` 表达意图不表达实现、`WhitelistSandbox` 按 Path/Pattern 白名单校验、校验失败抛异常；`checkFilePath` 在课件 `normalize()` 之上叠 `toRealPath()` 真实路径校验（存在目标解析真实路径再 `startsWith`，不存在目标向上找最近存在祖先解析）——宪法 VI MUST 条款（文件目标存在时必须 `toRealPath()` 确认真实路径仍位于白名单根）。通过 |
| VII. 同步执行 | 全部同步阻塞，无 Reactor/CompletableFuture。通过 |
| VIII. Tool 模块三合一 | Sandbox 包放 `oryxos-tool`（与内置 Tool 同模块），不拆模块。通过 |

**声明两个本节的强制设计决策**（宪法 v1.1.0"新建/改名必须在 plan 里声明理由"）：

1. **`ActionType` 保留既有四值（FILE_READ/FILE_WRITE/SHELL/HTTP_REQUEST），不改成课件三值（FILE_ACCESS/SHELL_EXEC/HTTP_REQUEST）**。理由：既有 `com.oryxos.tool.sandbox.ActionType`、四个 Tool 的 `enforce` 调用、`FileToolsTest`/`ShellToolsTest`/`HttpToolsTest`/`NotifyToolsTest` 全部按四值编写，且 TechnicalSolution §6.7 明确"文件读写分开便于未来按读/写分权限"是刻意设计。改三值会破坏全部既有测试与前序交付物，属于软门禁 2/4 触发场景。课件正文三值写法视为示意，本决策在 Constitution Check 声明理由（技术方案优先级高于课件正文）。
2. **三个 `@ConfigurationProperties` record 放 `com.oryxos.tool.sandbox`，用 `@EnableConfigurationProperties` 在 `CliSpringBootstrap` 注册**。理由：配置绑定需要把 record 注册进 Spring 上下文。`CliSpringBootstrap` 已有 `@EnableConfigurationProperties(OryxOsProperties.class)`（第 16 节），在本节追加三个 record 到同一注解即完成注册；`@Component` 的 `WhitelistSandbox` 由既有 `@ComponentScan("com.oryxos.tool")` 扫入。不新增自动配置类。
3. **`checkFilePath` 在课件 `normalize()` 之上叠 `toRealPath()` 真实路径校验**（宪法 VI MUST / CLAUDE.md 原则六）。理由：课件只写 `Path.normalize().toAbsolutePath()` + `startsWith`，能防 `..` 穿越但挡不住符号链接（白名单内软链指向白名单外真实文件）。宪法 VI 明确"文件目标存在时必须用 `toRealPath()` 校验真实路径仍位于白名单根；新建路径校验最近存在父目录的真实路径"。实现：目标存在 → `toRealPath()` 后再 `startsWith`；目标不存在 → 沿父目录链向上找最近存在祖先 `toRealPath()` 后再 `startsWith`（防新建路径经软链目录逃逸）。不破坏课件任何 harness 测试（`WhitelistSandboxTest` 的穿越/通配符用例仍全绿），只是把 `normalize()` 基线加强到宪法要求。

**复杂性说明**：无跨模块新依赖。`oryxos-tool` 内部完成全部改动（sandbox 包 + 四 Tool 首行接线 + 测试），组合根 `CliSpringBootstrap` 仅追加 `@EnableConfigurationProperties` 注册三块配置。默认白名单值按 2026-08-12 澄清取宽松默认，不打断现有 chat/demo/notify 流程。

## Project Structure

### Documentation (this feature)

```text
specs/007-sandbox-module/
├── plan.md              # This file
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
├── contracts/           # Phase 1 输出
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成）
```

### Source Code (repository root)

```text
oryxos-tool/src/main/java/com/oryxos/tool/sandbox/
├── Sandbox.java                    # 已有（20/23节占位）：接口，只读契约
├── SandboxAction.java              # 已有：record(type, target)
├── ActionType.java                 # 已有：四值枚举
├── SandboxViolationException.java  # 新增：RuntimeException，携带可读失败原因
├── WhitelistSandbox.java           # 新增：核心阶段唯一实现，读三块配置
├── FileSandboxProperties.java      # 新增：@ConfigurationProperties(prefix="file")
├── ShellSandboxProperties.java     # 新增：@ConfigurationProperties(prefix="shell")
├── HttpSandboxProperties.java      # 新增：@ConfigurationProperties(prefix="http")
└── NoOpSandbox.java                # 删除：占位实现被 WhitelistSandbox 替换

oryxos-tool/src/main/java/com/oryxos/tool/builtin/
├── FileTools.java                  # 已有（接线已就位，无需改）
├── ShellTools.java                 # 已有（接线已就位，无需改）
├── HttpTools.java                  # 已有（接线已就位，无需改）
└── NotifyTools.java                # 已有（接线已就位，无需改）

oryxos-tool/src/test/java/com/oryxos/tool/sandbox/
├── WhitelistSandboxTest.java       # 新增：三类校验"允许+拒绝"成对 + 穿越 + 通配符边界 + 空配置=全拒绝
└── SandboxPlaceholderTest.java     # 删除：NoOpSandbox 占位测试随实现替换退役

oryxos-tool/src/test/java/com/oryxos/tool/builtin/
├── FileToolsTest.java              # 已有：mock Sandbox 拦截用例（保留）
├── ShellToolsTest.java             # 已有（保留）
├── HttpToolsTest.java              # 已有（保留）
└── NotifyToolsTest.java            # 已有（保留）

oryxos-boot/src/main/resources/
└── application.yaml                # 修改：新增 file/shell/http 三块白名单配置（宽松默认）
```

> 注：课件 3.3 列出的"ShellTools 改法"（`@Tool` 方法内先 enforce 再 `doExecute`）在当前代码库已是既有形态——四个 Tool 早已在构造器注入 `Sandbox` 并在每个 `@Tool` 方法首行调用 `sandbox.enforce(...)`。故接线改动为零，本节以测试加固（`WhitelistSandboxTest` + 接线回归验证真实 IO 未发生）与实现替换为主。

**Structure Decision**: Sandbox 包（接口 + 值对象 + 实现 + 配置绑定）全部放 `oryxos-tool` 模块，与 `FileTools`/`ShellTools`/`HttpTools` 同模块相邻（课件明确"它们本来就是一伙的，不需要往 oryxos-core 塞新概念"）。组合根 `CliSpringBootstrap` 只追加配置注册。

## Complexity Tracking

> 无宪法违规需要 justify。上述两个决策均在 Constitution Check 中声明理由，不属于违规。`NoOpSandbox` 占位（非前序交付物，是 20/23 节临时占位）将被删除替换为 `WhitelistSandbox`，`SandboxPlaceholderTest` 随占位退役，不破坏任何前序交付契约。

## Phase 0 / Phase 1 artifacts

见 `research.md`、`data-model.md`、`contracts/`、`quickstart.md`。
