# Data Model: Tool 体系（第 20 节）

**Branch**: `020-lesson20-tool` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## 实体与值对象

### OryxTool（统一工具抽象）— 已有，oryxos-core，不改

所有工具的统一接口形态，屏蔽工具来源。四种来源都以它注册：

| 字段/方法 | 类型 | 说明 |
|---|---|---|
| `getName()` | String | 工具名，LLM 靠它点名调用 |
| `getDescription()` | String | 用途描述，给 LLM 看 |
| `getInputSchema()` | String | JSON Schema（字符串），缺它 Function Calling 翻译卡死 |
| `execute(String jsonInput)` | ToolResult | 执行，输入 JSON 字符串，返回统一结果 |

### ToolResult（工具执行结果）— 已有，oryxos-core，不改

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | boolean | 是否成功 |
| `content` | String | 成功时的结果内容 |
| `error` | String | 失败时的错误信息（可空） |
| `retryable` | boolean | 失败时是否可重试 |

静态工厂：`ok(content)`、`fail(error, retryable)`。

### ToolRegistry（工具注册表接口）— 已有，oryxos-core，不改

| 方法 | 签名 | 说明 |
|---|---|---|
| `get` | `Optional<OryxTool> get(String name)` | 按名称查找 |
| `listAll` | `Collection<OryxTool> listAll()` | 列出全部已注册工具 |

`ToolExecutor`（17 节）执行时用 `get(name)`；`PromptBuilder.resolveTools`（17 节）按 `Profile.tools()` 逐个 `get` 过滤。**按 Profile 过滤的"不多不少"语义**由 `DefaultToolRegistry` 提供 `toolsFor(List<String>)`（见下）。

### DefaultToolRegistry（本节点新实现）— oryxos-tool/registry

`ToolRegistry` 接口的具体实现：

| 方法 | 签名 | 说明 |
|---|---|---|
| `register` | `void register(OryxTool tool)` | 注册工具（按 name 索引）；重名覆盖并 WARN |
| `get` | `Optional<OryxTool> get(String name)` | 按名称查找（实现接口） |
| `listAll` | `Collection<OryxTool> listAll()` | 全部工具（实现接口） |
| `toolsFor` | `List<OryxTool> toolsFor(List<String> toolNames)` | 按 Profile.tools 过滤子集——**恰好等于**声明列表（声明里未注册的工具跳过并 WARN，但子集仍只含声明项，不多一个） |

约束：
- `toolsFor` 返回的子集仅包含声明列表中的工具，且每个声明项最多一个工具；未注册的声明项跳过（WARN）不计入"多"。
- 线程安全：注册发生在启动期，读取在运行时；用 `ConcurrentHashMap` 或启动后不可变（`Map.copyOf` 冻结）。

### McpServerConfig（MCP server 配置）— 本节点新，oryxos-tool/mcp

`mcp_servers.yaml` 单项的配置记录：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | String | server 唯一名 |
| `transport` | String | `stdio` \| `sse` |
| `command` | String | 启动命令（stdio） |
| `args` | List\<String\> | 启动参数（可空） |
| `env` | Map\<String,String\> | 环境变量（可空，支持 `${ENV}` 占位） |

### McpServerConfigLoader（本节点新）

SnakeYAML 解析 `.oryxos/mcp_servers.yaml` → `List<McpServerConfig>`。顶层键 `mcpServers:`。文件缺失/为空 → 空列表（合法状态，不报错）。

### McpClientService（MCP 客户端服务）— 本节点新

| 方法 | 签名 | 说明 |
|---|---|---|
| `connectAll` | `void connectAll()` | 启动时连接所有配置 server；逐个 try/catch，失联只 WARN、跳过该 server 工具、整体不抛异常 |
| `disconnectAll` | `void disconnectAll()` | 关闭所有连接（优雅清理，`@PreDestroy`） |

内部维护 `name → McpSyncClient` 与 `name → List<McpToolAdapter>`。

### McpToolAdapter（MCP 工具适配器）— 本节点新

把 `McpSchema.Tool` 适配成 `OryxTool`：

| OryxTool 方法 | 实现 |
|---|---|
| `getName` | `tool.name()` |
| `getDescription` | `tool.description()` |
| `getInputSchema` | `tool.inputSchema()` 经 Jackson 序列化为 String |
| `execute` | 解析 JSON 输入 → `CallToolRequest(name, arguments)` → `client.callTool(req)` → 结果包 `ToolResult` |

`CallToolResult.isError()==true` 或异常 → `ToolResult.fail(..., true)`（可重试）；否则取 `content()` 的 `TextContent.text()` 拼进 `ToolResult.ok(...)`。

## 状态与生命周期

- **DefaultToolRegistry**：启动期注册（内置工具装配 + `McpClientService.connectAll`），运行期只读。
- **McpClientService**：`@PostConstruct connectAll()` → 连接/注册；`@PreDestroy disconnectAll()` → 优雅关闭。
- **MCP 失联**：任一 server 连接失败 → WARN + 跳过 → 不影响其他 server 与整体启动。

## 不落库的数据（无新表）

- MCP 配置在文件系统 `.oryxos/mcp_servers.yaml`（用户可维护，git 可跟踪）。
- 工具执行审计复用 17 节 `tool_invocations` 表（`ToolExecutor` 写，本节点不重复实现）。

## 验证规则（来自 spec FR）

- 契约三件套（name/description/inputSchema）非空：`OryxToolContractTest` 参数化遍历。
- 按 Profile 过滤子集精确匹配：`ToolRegistryTest`。
- 越界拦截：`FileToolsTest`/`ShellToolsTest`/`HttpToolsTest` 用 mock Sandbox 抛异常断言。
- MCP 失联隔离：`McpClientServiceTest` mock client，坏 server 抛 ConnectException。
- MCP 转发：`McpToolAdapterTest` mock client，断言参数原样、结果包 `ToolResult`。
