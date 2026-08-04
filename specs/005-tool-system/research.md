# Research: Tool 体系（第 20 节）

**Branch**: `020-lesson20-tool` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## 1. MCP SDK 2.0.0 客户端 API 核实

**任务来源**: 课件 `McpClientService`/`McpToolAdapter` 规格；技术方案 §6.4。

### 核实结果（本地 `~/.m2` javap 确认）

- `io.modelcontextprotocol.sdk:mcp:2.0.0` 是聚合 artifact，真正类在 `mcp-core-2.0.0.jar`。
- `io.modelcontextprotocol.client.McpClient`：静态 `sync(McpClientTransport)` 返回 `McpClient.SyncSpec`；`.build()` 返回 `McpSyncClient`。
- `io.modelcontextprotocol.client.McpSyncClient`：
  - `ListToolsResult listTools()`（同步）
  - `CallToolResult callTool(CallToolRequest)`（同步）
  - `void close()` / `boolean closeGracefully()`（implements AutoCloseable）
- `io.modelcontextprotocol.spec.McpSchema.ListToolsResult`：`List<Tool> tools()`、`nextCursor()`。
- `io.modelcontextprotocol.spec.McpSchema.Tool`（record）：`name()`、`description()`、`inputSchema()`（`Map<String,Object>`）、`title()`。
- `io.modelcontextprotocol.spec.McpSchema.CallToolRequest`：构造 `CallToolRequest(String name, Map<String,Object> arguments)`。
- `io.modelcontextprotocol.spec.McpSchema.CallToolResult`：`List<Content> content()`、`Boolean isError()`、`Object structuredContent()`。
- `io.modelcontextprotocol.spec.McpSchema.TextContent`（Content 子类）：`text()`。
- transport：`StdioClientTransport`（构造 `(ServerParameters, McpJsonMapper)`）、`HttpClientSseClientTransport`（builder）；`ServerParameters.builder(command).args(...).env(...).build()`。
- `McpClient.SyncSpec`：`requestTimeout(Duration)`、`clientInfo(Implementation)`、`capabilities(ClientCapabilities)`、`build()`。

**决策**: 用 `McpClient.sync(...)` 同步客户端。`McpServerConfigLoader` 按 `transport` 值路由——`stdio` → `StdioClientTransport`，`sse` → `HttpClientSseClientTransport`（SSE 分支核心阶段保留但不强制测试，见数据模型）。

## 2. Spring AI `@Tool` schema 生成 API 核实

**任务来源**: 课件"内置 Tool 用 `@Tool` 注解自动扫描注册"；FR-011。

### 核实结果

- `org.springframework.ai.support.ToolCallbacks`：`static ToolCallback[] from(Object... beans)`——扫描 bean 的 `@Tool` 注解方法，生成 `MethodToolCallback`。
- `org.springframework.ai.tool.method.MethodToolCallback`：`getToolDefinition()` 返回 `ToolDefinition`（`name()/description()/inputSchema()` 三件套）。
- `org.springframework.ai.tool.annotation.Tool`：`name()`、`description()`、`returnDirect()`。

**决策**: 内置工具方法标 `@Tool(name=..., description=...)`，装配时 `ToolCallbacks.from(fileTools, shellTools, httpTools, notifyTools)` 得 `ToolCallback[]`，逐个包装成 `OryxTool`（用 `ToolDefinition` 三件套 + `ToolCallback.call(String)` 委托执行）注册进 `DefaultToolRegistry`。

## 3. OryxTool/ToolResult/ToolRegistry 接口现状（前序交付，不改）

- `OryxTool`（oryxos-core）：`getName()/getDescription()/getInputSchema()/execute(String)`——`getInputSchema()` 返回 **String**（16 节已定，不改）。
- `ToolResult`（oryxos-core）：record `(success, content, error, retryable)` + 静态 `ok(content)`/`fail(error, retryable)`。
- `ToolRegistry`（oryxos-core）：接口 `Optional<OryxTool> get(String)` / `Collection<OryxTool> listAll()`。`ToolExecutor`（17 节）与 `PromptBuilder.resolveTools`（17 节）都依赖此接口按名查工具、按 `Profile.tools` 过滤。20 节在 `oryxos-tool` 交付 `DefaultToolRegistry` 具体实现。

## 4. 越界测试策略（"白名单会拦"如何落地）

- 现状 `NoOpSandbox` 默认放行。课件正文的越界用例（`http_get` 白名单外域名 → execute 抛 `RuntimeException`）要真实生效，测试注入一个"命中即抛"的 `Sandbox` mock。
- 行为契约不变：工具执行**首行**调 `sandbox.enforce(action)`，24 节换 `WhitelistSandbox` 后测试语义不变（mock 换成真实现即可，断言逻辑不动）。
- 文件工具的正常路径用 `@TempDir` 真实临时目录（白名单内）；Shell 工具用真实 `ProcessBuilder`（白名单内命令）；HTTP 用 MockWebServer。

## 5. 依赖方向核对（防循环依赖）

- `oryxos-cli → oryxos-tool → oryxos-core`（`oryxos-cli` 已依赖 `oryxos-tool`，见其 pom）。
- `oryxos-provider → oryxos-core`（`ToolSchemaAdapter` 消费 `OryxTool`，Provider 不依赖 tool 模块）。
- `oryxos-tool → oryxos-core`（`NotifyTools` 已用 `ProfileContext`/`ToolResult`）。
- 无循环。`DefaultToolRegistry` 在 `oryxos-tool`，`ToolExecutor`（core）依赖 `ToolRegistry` **接口**——依赖倒置成立。
