# Implementation Plan: 第一周 — Provider 抽象 + ReAct 循环

**Branch**: `001-week1-llm-react-loop` | **Date**: 2026-08-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-week1-llm-react-loop/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

第一周交付 OryxOS 运行时内核的前两块地基：**对接 LLM（能力一）+ ReAct 循环（能力二）**，验收 Demo 一"`oryxos chat` 查天气穿衣"。用户发起"查一下北京天气并告诉我穿什么"，Agent 通过自实现 ReAct loop 调用 HTTP 天气 Tool、读取结果、给出穿衣建议，完整对话日志按序累积。

技术方案（docs/TechnicalSolution.md §13）明确本周范围：Maven 多模块骨架（9 个）、`oryxos init`、Profile YAML 解析、`ProviderService`（先跑通 DeepSeek 或 Kimi，含 provider name 显式映射）、`ReActLoop` + `PromptBuilder` + `ToolExecutor`、一个内置 HTTP Tool（`http_get`，天气查询）、`CliChannel`、Session 内存版。Tool 体系补全（文件/Shell/MCP、Sandbox 全档）与 Memory / Web Service / SQLite 持久化属后续周次。

## Technical Context

**Language/Version**: Java 21（MUST，virtual thread 处理并发）

**Primary Dependencies**: Spring Boot 3.x（3.5.16，父 POM 已定）、Spring AI 1.0.9（仅协议转换 + `@Tool` schema 生成，禁用自动 tool 执行）、Spring AI Alibaba BOM 1.1.2.3、spring-ai-openai starter（DeepSeek/Kimi 均 OpenAI 兼容）、Picocli 4.7.7（CLI）、SnakeYAML（Profile YAML）、SQLite + Spring Data JPA（本周仅骨架，不落库）

**Storage**: 本周 Session 内存版；`.oryxos/` 工作区含 `agents/`（最小 AGENT.md）、`logs/`。SQLite（含审计表）第四周引入

**Testing**: JUnit 5（spring-boot-starter-test）；单元测试覆盖 `ReActLoop`/`PromptBuilder`/`SandboxChecker`，Provider 调用用 mock 测试路由，Demo 一用手工验收

**Target Platform**: Windows / Linux 服务器（企业自托管），单二进制 JAR

**Project Type**: library + cli（Maven 多模块，单体可执行 JAR，`oryxos` 命令入口）

**Performance Goals**: CLI 交互场景，单次对话端到端 ≤2 分钟（含 LLM 往返）；虚拟线程支撑后续高并发，本周无并发指标要求

**Constraints**: 自实现 ReAct loop（不依赖 Spring AI Agent 抽象）；Spring AI 只用协议转换 + schema 生成；Provider 显式 name→ChatModel 映射；HTTP 域名白名单沙箱；同步阻塞 + 虚拟线程；工具/LLM 审计写入接口预留（SQLite 落库第四周）

**Scale/Scope**: 本周 4 个活跃模块（`oryxos-core`/`oryxos-provider`/`oryxos-tool`/`oryxos-channel-cli`/`oryxos-cli`/`oryxos-boot`），其余 3 模块（`oryxos-memory`/`oryxos-web`/`oryxos-storage`）仅保留骨架；单 Agent 对话，Session 内存版

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

依据 `.specify/memory/constitution.md`（v1.1.0）逐条核对本周实现：

| 原则 | 本周落实 | 状态 |
|------|---------|------|
| I. 自实现 ReAct Loop | `ReActLoop` 手工实现，不调 Spring AI Agent 抽象 / `ChatClient.prompt().call()` 自动执行 | ✅ |
| II. Spring AI 只用两件事 | 只用协议转换 + `@Tool` schema 生成；`ProviderService` 只做 `chatModel.call(...)` 并自行检查 tool call | ✅ |
| III. Provider 显式映射 | `ProviderService` 维护 `Map<String, ChatModel>` 显式映射，禁用 Bean 类型扫描 | ✅ |
| IV. 一目录一 Agent | 本周**最小 AGENT.md**：仅 frontmatter 派生 Profile（用户已澄清），正文注入后续周次；不引入 Skill 软连接 | ✅（已澄清） |
| V. 审计表 Day One | 见下方"原则五说明"——本周不落 SQLite，但审计写入经 core 接口预留 | ⚠️ 受控延迟 |
| VI. 沙箱白名单 | `WhitelistSandbox` HTTP 域名白名单（`http.allowed_domains`），`toRealPath` 类真实路径校验 | ✅ |
| VII. 同步执行 | 全程同步阻塞 + Java 21 虚拟线程，不引 Reactor/WebFlux | ✅ |
| VIII. Tool 模块三合一 | HTTP Tool + Sandbox + ToolRegistry 合并于 `oryxos-tool` 单模块 | ✅ |

**原则五说明（受控延迟，已记录）**：技术方案 §13 明确 SQLite 持久化（含 `tool_invocations`/`llm_calls` 写入）在第四周引入，本周 Session 为内存版、无 SQLite。为守住"可审计是核心差异化、数据地基 day one 立起来"的意图，本周 `ToolExecutor` 与 Provider 调用**通过 core 定义的审计写入接口**（`ToolInvocationRecorder` / `LlmCallRecorder`）落内存实现，第四周 storage 模块落地时换成 JPA 实现即可，不返工。这是与宪法意图一致的受控延迟，非静默跳过。

## Project Structure

### Documentation (this feature)

```text
specs/001-week1-llm-react-loop/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── cli.md           # oryxos init / chat 命令契约
│   ├── provider.md      # ProviderService 接口契约
│   ├── oryx-tool.md     # OryxTool / ToolResult 接口契约（含审计记录契约）
│   └── react-loop.md    # ReActLoop / AgentService 编排契约（含审计记录契约）
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── OryxTool.java          # Tool 统一抽象（已有，扩充）
├── ToolResult.java        # 执行结果（已有，扩充）
├── Session.java           # 会话（内存版）
├── Message.java           # 消息（USER / TOOL_CALL / TOOL_RESULT / ASSISTANT）
├── Profile.java           # 运行配置（name/description/provider/tools/settings）
├── ProfileRegistry.java   # Profile 内存索引
├── SessionManager.java    # 会话内存管理
├── react/ReActLoop.java   # 自实现 ReAct 主循环
├── react/PromptBuilder.java
├── react/ToolExecutor.java
├── service/AgentService.java
└── audit/
    ├── ToolInvocationRecorder.java   # 审计写入接口（内存实现，第四周换 JPA）
    └── LlmCallRecorder.java

oryxos-provider/src/main/java/com/oryxos/provider/
├── ProviderService.java       # 显式 name→ChatModel 映射（已有，实现）
├── ProviderConfig.java        # 从 application.yaml 读 provider 配置
└── FunctionCallingAdapter.java# OryxTool → Spring AI 工具格式（只用 schema 生成）

oryxos-tool/src/main/java/com/oryxos/tool/
├── ToolRegistry.java
├── sandbox/Sandbox.java              # 接口（中立，不携带实现细节）
├── sandbox/WhitelistSandbox.java     # HTTP 域名白名单实现
├── sandbox/SandboxViolationException.java
└── HttpTools.java                    # http_get（本周只此一个，天气）

oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/
└── CliChannel.java          # oryxos chat：读 stdin 写 stdout，/quit 退出

oryxos-cli/src/main/java/com/oryxos/cli/
├── OryxOsCli.java           # Picocli 主入口（已有，扩充）
├── InitCommand.java         # oryxos init：建 .oryxos/ 工作区
├── ChatCommand.java         # oryxos chat
└── ConfigLoader.java        # 环境变量 ${ENV_VAR} 解析 + 必填校验

oryxos-boot/src/main/java/com/oryxos/
├── OryxOSApplication.java   # Spring Boot 主类 + 自动配置（已有）
└── config/ProviderBeanConfig.java   # 基于 ProviderConfig 创建 ChatModel Beans（T015）

oryxos-memory / oryxos-web / oryxos-storage   # 本周仅保留空模块骨架
```

**Structure Decision**: 沿用仓库既有 Maven 9 模块结构（`pom.xml` 已配好模块与依赖管理）。本周只在其中 6 个活跃模块新增实现，`oryxos-memory`/`oryxos-web`/`oryxos-storage` 保留空包结构，保证多模块骨架完整、后续周次直接填充。核心抽象（`OryxTool`/`ToolResult`）与既有代码兼容，不推翻已有接口。

## Complexity Tracking

> 原则五审计写入的受控延迟记录于此。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 审计写入（原则五）本周不落 SQLite | 技术方案 §13 将 SQLite 持久化明确排在第四周；本周 Session 为内存版，无存储层 | 本周强行引入 SQLite 会提前把 storage 模块拉入第一周，违反既定节奏；改用 core 审计接口 + 内存实现，第四周无缝换 JPA |
