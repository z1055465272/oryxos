# 数据模型：第一周 — Provider 抽象 + ReAct 循环

**日期**: 2026-08-02

**输入**: `spec.md`、`plan.md`、`research.md`。数据模型与第四周 SQLite 最终形态语义对齐，避免迁移返工。

---

## 实体总览

| 实体 | 本周形态 | 存储 | 说明 |
|------|---------|------|------|
| Agent | 最小 AGENT.md → Profile | 文件系统 `.oryxos/agents/<name>/AGENT.md` | 仅 frontmatter 派生 Profile（已澄清 Q1） |
| Profile | Java 对象 | 内存（`ProfileRegistry`） | Agent 的运行配置，派生自 AGENT.md frontmatter |
| Session | Java 对象 | 内存（`SessionManager`） | 一次对话生命周期容器 |
| Message | Java 对象 | 内存（Session.messages） | 会话内一条记录，四类型 |
| Provider | 配置 + ChatModel 映射 | `application.yaml` + 内存映射 | 一家 LLM 服务商 |
| Tool | `OryxTool` 实例 | 内存（`ToolRegistry`） | 本周：`http_get` 一个 |
| Tool Invocation / LLM Call 审计 | 记录器接口 + 内存实现 | 内存（第四周 SQLite） | 审计写入接口预留 |

---

## Profile

由 `AgentLoader.deriveProfile(agentDir)` 从 AGENT.md frontmatter 派生（本周实现**最小子集**）。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | 是 | Profile 名，路由与索引 key |
| `description` | String | 否 | 一句话描述 |
| `provider.name` | String | 是 | 服务商名，作为 `ProviderService` 显式映射的路由 key |
| `provider.model` | String | 是 | 模型名 |
| `provider.temperature` | Double | 否 | 默认 0.7 |
| `tools` | List\<String\> | 否 | 可用 Tool 子集；缺省则注册表全部可用 |
| `settings.max_iterations` | Integer | 否 | ReAct 轮数上限，默认 10（宪法/技术方案 §4.3） |
| `settings.max_history_turns` | Integer | 否 | 对话历史保留轮数，默认 20（技术方案 §4.3） |

**校验规则**（启动时 `AgentLoader`）：
- `provider.name` 必须存在于 `ProviderService` 映射表，否则记录错误日志、该 Profile 不阻断启动
- 引用未注册的 Tool 名同样降级为错误日志

**状态**：静态（Profile 无运行时状态，只读配置）

---

## Session

本周内存版，字段与第四周 SQLite `sessions` 表语义对齐。

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 主键，`channel + user + profile` 联合生成 |
| `profileName` | String | 关联 Profile |
| `channel` | String | 接入渠道（本周 `cli`） |
| `userId` | String | 用户标识（CLI 默认固定值） |
| `messages` | List\<Message\> | 对话历史（内存版；第四周 `messages_json`） |
| `status` | String | `active` / `archived` |
| `createdAt` / `lastActiveAt` / `archivedAt` | Instant | 时间戳 |

**生命周期**：`created → active → archived`（本周不做归档 UI，字段预留）。

**关键约束**：
- 每次处理后 `lastActiveAt` 更新
- 对话历史注入 prompt 时按 `max_history_turns` 截断保留最近轮次（`PromptBuilder` 处理）

---

## Message

| 字段 | 类型 | 说明 |
|------|------|------|
| `role` | String | `user` / `assistant` / `tool` |
| `type` | String | `USER_TEXT` / `ASSISTANT_TEXT` / `TOOL_CALL` / `TOOL_RESULT` |
| `content` | String | 文本内容 |
| `toolName` | String | 仅 TOOL_CALL / TOOL_RESULT 时非空 |
| `toolInput` / `toolResult` | String(JSON) | 工具入参 / 结果 |
| `createdAt` | Instant | 时间戳 |

**消息累积顺序**（ReAct 循环保证）：`user → assistant(tool_call) → tool(result) → assistant(text)...`

---

## Provider

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | 唯一 provider name（`deepseek` / `kimi`） |
| `model` | String | 默认模型名 |
| `apiKey` | String | 从环境变量 `${DEEPSEEK_API_KEY}` 解析，**不明文入库** |
| `baseUrl` | String | 可选，覆盖默认端点 |

**显式映射**：`ProviderService` 持有 `Map<String, ChatModel>`（宪法原则三）。`ChatModel get(String name)` 供 `ReActLoop` 使用。

---

## OryxTool / ToolResult（既有接口，扩充）

```
OryxTool
├── getName(): String
├── getDescription(): String
├── getInputSchema(): JsonSchema
└── execute(JsonNode input): ToolResult

ToolResult
├── success: boolean
├── content: String
├── errorMessage: String | null
└── retryable: boolean
```

本周注册实例：`HttpTools#http_get`（天气查询，`Sandbox.enforce(HTTP_REQUEST, url)` 后执行）。

---

## Sandbox

```
Sandbox
└── enforce(SandboxAction action)   // 接口，中立

SandboxAction
├── type: FILE_READ | FILE_WRITE | SHELL_COMMAND | HTTP_REQUEST
└── target: String

WhitelistSandbox（本周实现）
└── 仅 HTTP_REQUEST：解析 host → 匹配 http.allowed_domains 通配符
```

**校验失败**抛 `SandboxViolationException`，`ToolExecutor` 捕获后走审计失败路径（`success=false` + `error_message`）。

---

## 审计记录器（本周接口 + 内存实现）

```
ToolInvocationRecorder
└── record(ToolInvocation inv)        // tool_name / input_json / result_json / success / error_message / duration_ms / created_at

LlmCallRecorder
└── record(LlmCall call)              // provider / model / prompt_tokens / completion_tokens / total_tokens / duration_ms / created_at
```

**本周**：内存 `ConcurrentLinkedQueue` 实现，供 Demo 验收核对。**第四周**：`oryxos-storage` 提供 JPA 实现落 SQLite，core 接口不变。
