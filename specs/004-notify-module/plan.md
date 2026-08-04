# Implementation Plan: Notify 模块 — Agent 主动推送出口

**Branch**: `019-lesson19-notify` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-notify-module/spec.md`

## Summary

实现 OryxOS 通知推送模块——Agent 完成工作后主动把结果推送到外部渠道（核心阶段仅通用 webhook）。核心交付：`NotifyChannelAdapter` 接口 + `NotifyTarget` 值对象 + `WebhookNotifyAdapter` 唯一实现 + `NotifyTools` 内置 Tool + Sandbox 占位类型。同时改造 Profile 的 `notify_channels` 字段从 `List<String>` 升级为 `List<NotifyChannelConfig>`。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.x, Spring Web (RestClient), Spring AI Alibaba (`@Tool` 注解), MockWebServer (test)

**Storage**: 无新增表。NotifyChannelConfig 嵌入 Profile YAML，不持久化数据库。

**Testing**: JUnit 5 + Mockito + MockWebServer；P3C/SpotBugs/FindSecBugs/PMD 静态门禁

**Target Platform**: Linux/Windows server, JDK 21

**Project Type**: library (Spring Boot 多模块单体)

**Performance Goals**: webhook 发送 ≤ 30s 超时（与 Spring RestClient 默认一致）

**Constraints**: 同步阻塞（VII）；不入新表；Profile JSON 必过环境变量占位符校验；Java 18+ 语法禁（switch default -> 等）

**Scale/Scope**: 核心阶段单 Adapter 实现；Sandbox 占位待 23/24 节；@Tool 完整接线待 20 节

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 自实现 ReAct Loop | N/A | NotifyTools 不涉及 ReAct 循环，由 ToolExecutor 调用 |
| II. Spring AI 只用两件事 | ✅ 通过 | `@Tool` 注解仅用于 schema 生成，`NotifyTools.notify()` 由 ToolExecutor 手动调用 |
| III. Provider 显式映射 | N/A | 不涉及 Provider 路由 |
| IV. 一个目录=一个 Agent | ✅ 通过 | notify_channels 配置嵌入 AGENT.md frontmatter |
| V. 审计表 Day One | ✅ 通过 | notify 调用通过 ToolExecutor 现有路径写入 tool_invocations |
| VI. 沙箱白名单 | ✅ 通过 | Sandbox.enforce 调用点已预留（占位实现默认放行），23/24 节填具体校验 |
| VII. 同步执行 | ✅ 通过 | RestClient 同步 POST，无 Reactive/CompletableFuture |
| VIII. Tool 模块三合一 | ✅ 通过 | 全部放在 oryxos-tool 模块 |

**Gate Result**: 全部通过，无违规。

## Project Structure

### Documentation (this feature)

```text
specs/004-notify-module/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1
└── tasks.md             # Phase 2 (speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── Profile.java              # [MODIFY] notifyChannels: List<String> → List<NotifyChannelConfig>
├── ProfileLoader.java        # [MODIFY] parseNotifyChannels() 新方法，解析 YAML 结构化列表
├── NotifyChannelConfig.java  # [NEW] record(type, url)，支持环境变量占位符
└── ProfileContext.java       # [MODIFY] 新增 resolveNotifyChannel(String) 实例方法

oryxos-tool/src/main/java/com/oryxos/tool/
├── notify/
│   ├── NotifyChannelAdapter.java  # [NEW] 接口：send(NotifyTarget, String)
│   ├── NotifyTarget.java          # [NEW] record(channelType, Map<String,String> config)
│   └── WebhookNotifyAdapter.java  # [NEW] @Component 实现，RestClient POST JSON
├── builtin/
│   └── NotifyTools.java           # [NEW] @Tool notify(content, channel)，三步串联
└── sandbox/
    ├── Sandbox.java               # [NEW] 占位接口：enforce(SandboxAction)
    ├── SandboxAction.java         # [NEW] record(type: ActionType, target: String)
    └── ActionType.java            # [NEW] enum { FILE_READ, FILE_WRITE, SHELL, HTTP_REQUEST }

oryxos-tool/src/test/java/com/oryxos/tool/
├── notify/
│   └── WebhookNotifyAdapterTest.java   # [NEW] 第一批：MockWebServer 验证发送/5xx/URL 来源
├── builtin/
│   └── NotifyToolsTest.java            # [NEW] 第二批：mock Sandbox+Adapter，顺序/默认/报错
├── sandbox/
│   └── SandboxPlaceholderTest.java     # [NEW] 占位允许通过 smoke
└── core/
    └── NotifyChannelConfigTest.java    # [NEW] 环境变量占位符解析测试
```

**Structure Decision**: 全部 notify 相关类放 oryxos-tool（按 CLAUDE.md §6.8 和模块落位表）。`NotifyChannelConfig` 放 oryxos-core/Profile 同包，因为是 Profile 字段的一部分。Sandbox 占位放 oryxos-tool/sandbox 子包，为 23/24 节预留位置。

## Complexity Tracking

无违规需 justify。
