# Contracts: Tool 体系（第 20 节）

**Branch**: `020-lesson20-tool` | **Date**: 2026-08-04 | **Spec**: [spec.md](../spec.md)

## Contract 1: ToolRegistry 契约（接口在 core，实现在 tool）

```text
// oryxos-core（16 节已建，本节不改）
interface ToolRegistry {
    Optional<OryxTool> get(String name);
    Collection<OryxTool> listAll();
}

// oryxos-tool 新增实现（20 节）
class DefaultToolRegistry implements ToolRegistry {
    void register(OryxTool tool);                          // 按 name 索引，重名覆盖并 WARN
    Optional<OryxTool> get(String name);
    Collection<OryxTool> listAll();
    List<OryxTool> toolsFor(List<String> toolNames);       // 按 Profile.tools 过滤，子集精确匹配
}
```

**行为契约**：
- `toolsFor` 返回子集**恰好等于**声明列表（每个声明项最多一个工具，未注册项跳过 WARN）；不多一个（未过滤干净）、不少一个（过滤过头）都是错。
- 消费方：`ToolExecutor`（17 节）按 `get(name)` 执行；`PromptBuilder.resolveTools`（17 节）按 `Profile.tools()` 过滤注入 prompt。

## Contract 2: OryxTool 契约（不变，16 节已定）

```text
interface OryxTool {
    String getName();
    String getDescription();
    String getInputSchema();   // 返回 String JSON Schema，非空（契约测试守护）
    ToolResult execute(String jsonInput);
}
```

**行为契约**：
- 每个已注册工具的 `getName()/getDescription()/getInputSchema()` 都非空——任何工具漏实现 `getInputSchema()`，`OryxToolContractTest` 参数化测试立刻红。
- `execute` 返回 `ToolResult`，不抛业务异常（Sandbox 越界抛异常除外，那是"被拦"的正常信号）。

## Contract 3: 内置工具执行链（涉外 IO 首行过 Sandbox）

```text
FileTools.read_file/write_file/list_dir  →  sandbox.enforce(FILE_READ/FILE_WRITE) → 真实 IO
ShellTools.shell                         →  sandbox.enforce(SHELL)               → 真实子进程
HttpTools.http_get/http_post             →  sandbox.enforce(HTTP_REQUEST)        → 真实 HTTP
```

**行为契约**：
- 每个涉外工具 `execute` 方法**第一件事**调 `sandbox.enforce(action)`；`NoOpSandbox` 占位放行（24 节替换 `WhitelistSandbox`）。
- 校验不过（当前测试中为 mock Sandbox 抛异常）→ 工具抛异常，真实 IO 不执行。
- 越界测试语义与实现解耦：测试注入"命中即抛"的 mock Sandbox，断言工具抛 `RuntimeException`；24 节换真实现后断言不变。

## Contract 4: mcp_servers.yaml 配置格式

```yaml
mcpServers:
  - name: <server 名>          # 必填，全局唯一
    transport: stdio|sse       # 必填，当前支持 stdio / sse
    command: <启动命令>         # stdio 必填
    args: [<参数>]              # 可空
    env:
      KEY: ${ENV_VAR}          # 可空，支持 ${ENV} 占位
```

**行为契约**：
- 文件缺失/为空 → 空列表，合法状态（无 MCP server 配置）。
- 单条配置缺失必填字段 → 该条 WARN 跳过，其余照常加载。

## Contract 5: MCP Client 连接与隔离

```text
McpClientService.connectAll():
  for cfg in loadConfigs():
    try:
      client = McpClient.sync(transportFor(cfg)).build()   // stdio → StdioClientTransport
      for tool in client.listTools().tools():
        registry.register(new McpToolAdapter(client, tool))
    catch (Exception e):
      log.warn("MCP server {} 连接失败，跳过它的工具", cfg.name(), e)   // 只 WARN，不炸启动
```

**行为契约**：
- 任一 server 失联 → 只 WARN、跳过该 server 工具、其余 server 照常注册、整体不抛异常。
- `McpToolAdapter.execute`：参数原样转发（JSON → `Map` → `CallToolRequest`），结果包 `ToolResult`；`isError()` 或异常 → `ToolResult.fail(..., true)`。
