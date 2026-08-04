# Data Model: Notify 模块

**Feature**: Notify 模块 — Agent 主动推送出口 | **Date**: 2026-08-04

## Entity Relationship

```text
Profile ──has many──> NotifyChannelConfig
                            │
                            │ type (e.g. "webhook")
                            │ url  (e.g. "https://hooks.example.com/xxx")
                            │
                            ▼
NotifyChannelAdapter ──uses──> NotifyTarget { channelType, config {url: ...} }
       ▲
       │ implements
       │
WebhookNotifyAdapter ──POST JSON──> external webhook URL
```

## Entities

### NotifyChannelConfig (oryxos-core)

Profile 级别的通知渠道配置，嵌入在 `Profile.notifyChannels` 列表中。

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | String | Yes | 渠道类型标识（如 "webhook"），同时作为 `notify()` 调用的 `channel` 参数匹配键 |
| `url` | String | Yes | 渠道连接地址，支持 `${ENV_VAR}` 占位符，在 ConfigLoader 阶段解析 |

**Validation rules**:
- `type` 不可为 null 或空字符串
- `url` 不可为 null 或空字符串（空 URL 应在 NotifyTools 调用时明确报错）
- 空列表合法（表示该 Agent 无通知能力，notify 调用时报错）

### NotifyTarget (oryxos-tool/notify)

通知适配器的"目标"值对象，由 NotifyTools 从 NotifyChannelConfig 转换而来。

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `channelType` | String | Yes | 渠道类型（从 NotifyChannelConfig.type 透传） |
| `config` | Map<String, String> | Yes | 渠道配置键值对（如 {"url": "https://..."}），由适配器按 channelType 解释 |

### NotifyChannelAdapter (oryxos-tool/notify)

通知发送的抽象接口，无状态。

| Method | Signature | Description |
|--------|-----------|-------------|
| `send` | `void send(NotifyTarget target, String content)` | 将 content 发送到 target 指定的通知目标 |

### WebhookNotifyAdapter (oryxos-tool/notify)

`NotifyChannelAdapter` 的 HTTP webhook 实现，Spring `@Component`。

| Dependency | Purpose |
|------------|---------|
| `RestClient` | 执行 HTTP POST |

行为：
- 从 `target.config().get("url")` 取 URL
- POST body: `{"content": "<content>"}`，Content-Type: `application/json`
- 成功（2xx/3xx）：void 返回
- 失败（4xx/5xx）：异常向上传播（RestClient 默认行为）

### Sandbox (oryxos-tool/sandbox)

占位接口，23/24 节由真实沙箱实现替换。

| Method | Signature |
|--------|-----------|
| `enforce` | `void enforce(SandboxAction action)` |

### SandboxAction (oryxos-tool/sandbox)

描述一次待校验的操作。

| Field | Type |
|-------|------|
| `type` | `ActionType` |
| `target` | `String`（文件路径 / 命令 / URL） |

### ActionType (oryxos-tool/sandbox)

| Value | 对应操作 |
|-------|----------|
| `FILE_READ` | 文件读 |
| `FILE_WRITE` | 文件写 |
| `SHELL` | Shell 命令 |
| `HTTP_REQUEST` | HTTP 请求（notify + http_get/post 共用） |

### NotifyTools (oryxos-tool/builtin)

Agent 可见的统一通知入口，Spring `@Component`。

| Dependency | Purpose |
|------------|---------|
| `Sandbox` | 安全校验（先于发送） |
| `NotifyChannelAdapter` | 实际发送 |
| `ProfileContext` | 读当前 Profile 的 notify_channels |

方法：`ToolResult notify(String content, @Nullable String channel)`

执行流：
1. `Profile current = ProfileContext.current()`
2. 如果 `current.notifyChannels()` 为空 → `ToolResult.fail("未配置通知渠道", false)`
3. 如果 `channel` 参数非空 → 按 type 匹配第一个 NotifyChannelConfig；否则用第 0 个
4. 匹配不到 → `ToolResult.fail("未找到渠道: " + channel, false)`
5. `sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url))`
6. `adapter.send(new NotifyTarget(type, Map.of("url", url)), content)`
7. 返回 `ToolResult.ok("已推送")`
