# 契约：`OryxTool` / `ToolResult` / `ToolRegistry`（能力四，本周最小集）

**关联实体**: [OryxTool / ToolResult](../data-model.md#oryxtool--toolresult)、[Sandbox](../data-model.md#sandbox)

**来源**: 技术方案 §6.1 / §6.6 / §6.7，宪法原则八

---

## `OryxTool`（既有接口，扩充）

```java
interface OryxTool {
    String getName();                 // 工具名，注册与路由 key
    String getDescription();          // 供 LLM 理解用途
    JsonSchema getInputSchema();      // JSON Schema，由 Spring AI @Tool 生成
    ToolResult execute(JsonNode input);
}
```

## `ToolResult`

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 是否成功 |
| `content` | String | 结果内容（进上下文的文本/JSON） |
| `errorMessage` | String \| null | 失败信息 |
| `retryable` | boolean | 是否可重试（如超时可重试，白名单拒绝不可重试） |

## `ToolRegistry`

```java
class ToolRegistry {
    OryxTool get(String name);
    List<OryxTool> all();
    List<OryxTool> forProfile(Profile p);   // 按 Profile.tools 过滤子集
}
```

启动时收集所有 Tool（本周：`http_get`）包装为 `OryxTool` 实例。

## `http_get`（本周唯一内置 Tool，归属 `HttpTools`）

- **name**: `http_get`
- **description**: 发起 HTTP GET 请求获取 URL 内容（本周用于天气查询）
- **input schema**: `{ url: string, timeoutMs?: number }`
- **沙箱**: `Sandbox.enforce(new SandboxAction(HTTP_REQUEST, url))`，域名白名单校验（`http.allowed_domains`）
- **失败语义**: 超时 / 网络错误 / 白名单拒绝 → `success=false`，`retryable` 视情况（超时 true，白名单拒绝 false）

## 审计

`ToolExecutor` 每次执行前后记录 `ToolInvocationRecorder`（宪法原则五）：tool_name、input_json、result_json、success、error_message、duration_ms。本周内存实现，第四周 JPA。

## 关键约束

1. **Tool 三合一单模块**（宪法原则八）：内置 Tool + Sandbox + ToolRegistry 均在 `oryxos-tool`，不拆模块。
2. **`Sandbox` 接口中立**（技术方案 §6.7）：不携带"白名单/容器"等实现细节，本周 `WhitelistSandbox` 仅实现 HTTP 域名校验，`FILE_READ/WRITE`/`SHELL_COMMAND` 接口预留第二周。
3. **Tool 调用由 `ToolExecutor` 调度**，Spring AI 不自动执行（宪法原则二）。
