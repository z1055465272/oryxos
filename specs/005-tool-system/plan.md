# Implementation Plan: Tool 体系 — Agent 能动手干活的手

**Branch**: `020-lesson20-tool` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-tool-system/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

第 20 节交付 Tool 体系，让 Agent 从"只会想和说"到"能动手干活"。核心是四件事：(1) 统一工具抽象 `OryxTool`（已在 `oryxos-core`，16 节建全）与统一注册表 `ToolRegistry` 的**具体实现**（落 `oryxos-tool`）；(2) 内置文件/Shell/HTTP 工具（`FileTools`/`ShellTools`/`HttpTools`），每个工具执行第一步过 `Sandbox.enforce`（占位实现，24 节替换）；(3) MCP Client（`McpClientService` + `McpToolAdapter`，SDK 2.0.0），读 `.oryxos/mcp_servers.yaml` 连接、`tools/list` 包装注册、失联只 WARN 不炸；(4) 19 节 `NotifyTools` 完成 `@Tool` 注册接线。技术方案 §6.1~6.6 为设计依据，工具模块三合一（宪法 VIII）。

## Technical Context

**Language/Version**: JDK 21 + Spring Boot 3.5.16 + Spring AI 1.0.9（BOM 锁定，本地依赖已核实：`spring-ai-model`、`org.springframework.ai.support.ToolCallbacks`、`org.springframework.ai.tool.method.MethodToolCallback` 存在）+ Maven 多模块

**Primary Dependencies**:
- `io.modelcontextprotocol.sdk:mcp:2.0.0`（已在根 pom `dependencyManagement` 锁定，本地 `~/.m2` 已下载，子构件 `mcp-core-2.0.0.jar` 含全部 client 类）
- `org.springframework.ai:spring-ai-model`（`@Tool` 注解、`ToolCallbacks.from(...)` 生成 `ToolDefinition` 的 schema）
- `spring-web`（RestClient，NotifyTools 已用）、SnakeYAML（解析 `mcp_servers.yaml`，版本由 Boot parent 管理）

**Storage**: 不新增表。工具执行审计走既有 `tool_invocations` 表（17 节 `ToolExecutor` 已写，本节约束：不重复实现审计）。MCP server 配置落 `.oryxos/mcp_servers.yaml`（文件系统，不做 DB/CRUD）。

**Testing**: JUnit 5 单测（全部不碰网络，MCP 用 mock）+ `spring-boot-starter-test` + MockWebServer（`http_get`/`http_post` 正常路径，已有依赖 `mockwebserver3 5.0.0-alpha.14`）。无集成冒烟（本节约束：不碰真网络、不连真 MCP server）。

**Target Platform**: 本地 CLI 环境（Windows 10，JDK 21 at `D:\envconfig\JDK\jdk-21.0.11`）+ Linux 部署目标

**Project Type**: Maven 多模块 Java 21 单体（`oryxos-tool` 三合一模块）

**Performance Goals**: 不设量化目标（课件未定义）；内置工具同步阻塞、与虚拟线程天然配合（宪法 VII）

**Constraints**: 所有涉外 IO（文件/Shell/HTTP）执行第一步过 `Sandbox.enforce`（占位放行，24 节替换 WhitelistSandbox）；外部 MCP 失联不能拖垮启动；工具执行结果包成 `ToolResult`；`getInputSchema` 不得返回空（缺它 Function Calling 翻译卡死）；测试方法名英文（`@DisplayName` 保留课件原文）

**Scale/Scope**: 内置工具 8 个（File 3 + Shell 1 + Http 2 + Notify 1 + MCP 动态）+ MCP 工具按 `tools/list` 动态注册。本次交付的 8 个内置工具覆盖"读写文件、跑命令、调 API、推通知"最短链路（`save_memory`/`recall_memory` 归 22 节 Memory 模块）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查结果 | 说明 |
|---|---|---|
| I. 自实现 ReAct Loop | ✅ 不涉及 | 本节约束在 `oryxos-tool`，不触碰 `ReActLoop` |
| II. Spring AI 只用两件事 | ✅ 通过 | 只用 `@Tool` schema 生成；`ToolExecutor` 是唯一执行路径（17 节已建），本节约束不新增执行路径、不引入自动执行 |
| III. Provider 显式映射 | ✅ 不涉及 | 无 Provider 变更 |
| IV. 一个目录 = 一个 Agent；Skill 不进 ToolRegistry | ✅ 通过 | 只注册 Tool；`mcp_servers.yaml` 是配置、不是 Agent 目录 |
| V. 审计表 Day One 写入 | ✅ 通过 | 不新增审计逻辑，复用 17 节 `ToolExecutor` 写 `tool_invocations` |
| VI. 沙箱白名单 | ✅ 占位接入 | 工具执行首行调 `sandbox.enforce(...)`（接口已建），`NoOpSandbox` 占位放行，24 节替换 `WhitelistSandbox`。文件目标真实路径校验（`toRealPath`）24 节落地 |
| VII. 同步执行模型 | ✅ 通过 | 内置工具/MCP 调用全部同步阻塞；MCP SDK 用 `McpClient.sync(...)` 同步客户端，不用异步 |
| VIII. Tool 模块三合一 | ✅ 通过 | 内置 Tool + MCP Client + Registry + Sandbox 接口全部在 `oryxos-tool`，不拆多模块 |
| 技术栈约束 | ✅ 通过 | JDK 21 + Spring Boot 3.x；MCP SDK 2.0.0 已核实；凭证走环境变量占位（`mcp_servers.yaml` 的 env 支持 `${ENV}`） |

## Project Structure

### Documentation (this feature)

```text
specs/005-tool-system/
├── plan.md              # This file
├── research.md          # Phase 0 output (MCP SDK API 核实)
├── data-model.md        # Phase 1 output (实体/值对象/注册表契约)
├── quickstart.md        # Phase 1 output (验证指南)
├── contracts/           # Phase 1 output (ToolRegistry 契约、mcp_servers.yaml schema)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── OryxTool.java            # 已有（16 节）：统一工具抽象，含 getInputSchema
├── ToolResult.java          # 已有（16 节）：success/content/error/retryable
└── ToolRegistry.java        # 已有（16 节）：契约接口（get/listAll），20 节在 oryxos-tool 实现

oryxos-tool/src/main/java/com/oryxos/tool/
├── builtin/
│   ├── FileTools.java       # 新增：read_file/write_file/list_dir（@Tool，先过 sandbox）
│   ├── ShellTools.java      # 新增：shell（@Tool，命令白名单 + 超时）
│   ├── HttpTools.java       # 新增：http_get/http_post（@Tool，域名白名单）
│   └── NotifyTools.java     # 已有（19 节）：notify（@Tool 已完成接线）
├── registry/
│   └── DefaultToolRegistry.java   # 新增：ToolRegistry 具体实现（Map 索引 + 按 Profile.tools 过滤）
├── mcp/
│   ├── McpServerConfig.java # 新增：mcp_servers.yaml 的配置记录（name/transport/command/env）
│   ├── McpServerConfigLoader.java  # 新增：SnakeYAML 解析 .oryxos/mcp_servers.yaml
│   ├── McpClientService.java # 新增：连接所有配置 server → tools/list → 包装注册，失联只 WARN
│   └── McpToolAdapter.java  # 新增：把 McpSchema.Tool 适配成 OryxTool，execute 经 JSON-RPC 转发
└── sandbox/                 # 已有（接口 + NoOpSandbox 占位）

oryxos-cli/src/main/java/com/oryxos/cli/
└── CliSpringBootstrap.java  # 修改：stub ToolRegistry 替换为 DefaultToolRegistry + 内置工具装配 + McpClientService
```

**Structure Decision**: 遵循 TechnicalSolution §10 模块落位表——`OryxTool`/`ToolResult`/`ToolRegistry` 接口在 `oryxos-core`（16 节已建），Registry 具体实现与内置工具/MCP 在 `oryxos-tool`（三合一，宪法 VIII）。装配层（`CliSpringBootstrap`）在 `oryxos-cli` 用 `@Bean` 把工具组件接进 Spring 上下文。依赖方向：`oryxos-cli → oryxos-tool → oryxos-core`，无循环依赖。

## Complexity Tracking

> 无宪法违规，本表留空。

## Research Notes (Phase 0 摘要)

### 决策一：MCP SDK 用同步客户端，不用异步

**Decision**: `McpClient.sync(transport)` → `McpSyncClient`（`listTools()`/`callTool(CallToolRequest)` 均同步）。
**Rationale**: 宪法 VII 同步执行模型；SDK 2.0.0 的 `McpClient.sync()` 返回 `McpSyncClient`，方法全同步。
**Alternatives**: `McpAsyncClient`（CompletableFuture 式，违反宪法 VII，弃）。

### 决策二：工具 schema 来源——内置工具用 `@Tool` 注解 + `ToolCallbacks.from`，MCP 工具直接映射 `McpSchema.Tool.inputSchema`

**Decision**: 内置工具方法标 `@Tool(name/description)`，装配时用 `ToolCallbacks.from(toolsBean)` 生成 `ToolDefinition`（name/description/inputSchema 三件套）；MCP 工具把 `McpSchema.Tool` 的 `name()/description()/inputSchema()` 直接映射成 `OryxTool`。
**Rationale**: `ToolCallbacks.from` 自动从注解+参数生成 JSON Schema，与 19 节 `NotifyTools` 的 `@Tool` 写法一致，且满足 FR-011（契约三件套齐全）。MCP 的 `inputSchema()` 本身就是 `Map<String,Object>`，序列化成 String 即可。
**Alternatives**: 手写 `ToolDefinition.builder()` 逐条拼 schema（繁琐易漏，弃）。

### 决策三：`OryxTool.getInputSchema()` 返回 String（已定字面量），MCP 的 Map schema 用 Jackson 序列化

**Decision**: 保持 `OryxTool.getInputSchema()` 返回 `String`（16 节已定，软门禁不改）；`McpToolAdapter` 把 `tool.inputSchema()`（`Map<String,Object>`）用 `ObjectMapper.writeValueAsString` 序列化为 String。
**Rationale**: 现有 `ToolSchemaAdapter.toFunctionTool` 已按 String 消费（`function.setJsonSchema(tool.getInputSchema())`），改类型会破坏 16 节契约。Spring AI 的 `ToolDefinition.inputSchema()` 也返回 String，一致。
**Alternatives**: 改 `OryxTool` 返回类型（违反软门禁 2，弃）。

### 决策四：MCP server 连接失败的隔离范围

**Decision**: `McpClientService.connectAll()` 对每个 server 单独 try/catch——连接失败或 `listTools()` 失败都只 `log.warn` 并跳过该 server 的工具，其余 server 照常注册；整体不抛异常。
**Rationale**: FR-008/课件"外部依赖失联不拖垮自身启动"。逐个 server 隔离，不因一个坏配置放弃全部。
**Alternatives**: 整体 try/catch（一个坏 server 丢全部，弃）；失败即启动失败（违反 FR-008，弃）。

### 决策五：`McpToolAdapter.execute` 的调用参数形态

**Decision**: `McpToolAdapter.execute(String jsonInput)` 内部把 JSON 字符串解析成 `Map<String,Object>`（Jackson），构造 `CallToolRequest.builder().name(toolName).arguments(map).build()` 转发；`CallToolResult.content()` 的 `TextContent` 文本拼进 `ToolResult.content`，`isError()==true` 时返回失败。
**Rationale**: 课件 `McpToolAdapter` 规格："execute 转发参数原样、结果包成 ToolResult"。SDK 的 `CallToolRequest(String name, Map arguments)` 与 `CallToolResult.content()` 已核实。
**Alternatives**: 直接传 JSON 字符串给 `arguments`（SDK 2.0.0 的 `CallToolRequest(String, Map)` 要求 Map，弃）。

### 决策六：`mcp_servers.yaml` 配置形态与解析

**Decision**: YAML 顶层 `mcpServers:` 列表，每项 `name`/`transport`（`stdio`|`sse`）/`command`/`args`/`env`；`McpServerConfigLoader` 用 SnakeYAML 解析为 `List<McpServerConfig>`，env 值支持 `${ENV}` 占位（复用 `ConfigLoader` 的解析思路）。文件缺失/为空时不报错（无 MCP server 配置是合法状态）。
**Rationale**: 课件/技术方案 §6.4 规定声明 name/transport/command/env；`.oryxos/mcp_servers.yaml` 是工作区文件系统的 MCP 配置。
**Alternatives**: 手写模板字符串解析（易错，弃）；依赖 Spring Boot `@ConfigurationProperties`（需新增自动配置类，过度，弃）。

### 决策七：内置工具正常路径测试用 MockWebServer，越界路径用 NoOpSandbox 异常测试

**Decision**: `HttpToolsTest` 的"正常能跑通"用 MockWebServer（本地假 HTTP，不算外网依赖）；"越界会被拦"两个用例——`FileToolsTest`/`ShellToolsTest` 用真实文件系统+临时目录断言白名单内路径可读/内命令可跑，越界用 mock `Sandbox` 抛异常断言被拦；`HttpToolsTest` 越界用 mock `Sandbox` 抛异常。
**Rationale**: 课件正文 `http_get` 用例模板断言"白名单外域名 execute 抛 RuntimeException"。当前 `NoOpSandbox` 默认放行，要让越界用例真实生效，测试里注入一个"命中即抛"的 `Sandbox` mock（行为契约：工具执行首行调 `enforce`，24 节换真实现后测试语义不变）。
**Alternatives**: 断言 `NoOpSandbox` 不抛（无法验证"被拦"行为，弃）。
