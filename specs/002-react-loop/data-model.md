# Data Model: ReAct 循环引擎

**Feature**: ReAct 循环引擎 | **Date**: 2026-08-03

## 实体清单

### 1. ToolInvocation（审计实体 — oryxos-storage）

每次工具调用的审计记录，与 LlmCall 同口径。成功/失败都写入。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT PK | AUTOINCREMENT | 主键 |
| `sessionId` | VARCHAR(255) | NOT NULL | 关联 Session |
| `toolName` | VARCHAR(128) | NOT NULL | Tool 名称 |
| `inputJson` | TEXT | NOT NULL | 调用参数 JSON |
| `resultJson` | TEXT | NULLABLE | 执行结果 JSON |
| `success` | BOOLEAN | NOT NULL | 是否成功 |
| `errorMessage` | TEXT | NULLABLE | 错误信息（成功时为 null） |
| `durationMs` | BIGINT | NOT NULL | 执行耗时 ms |
| `createdAt` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 调用时间 |

**索引**: `idx_tool_invocations_session ON tool_invocations (session_id)`

**DDL** (已在 V1__init_audit_tables.sql，本节确认列完整，测试 schema.sql 同步补表):

```sql
CREATE TABLE IF NOT EXISTS tool_invocations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id    VARCHAR(255)  NOT NULL,
    tool_name     VARCHAR(128)  NOT NULL,
    input_json    TEXT          NOT NULL,
    result_json   TEXT,
    success       BOOLEAN       NOT NULL,
    error_message TEXT,
    duration_ms   INTEGER       NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 2. ToolInvocationRecord（契约值对象 — oryxos-core）

`ToolInvocationStore.save` 的入参（跨模块契约值对象，放 core，storage 适配为 JPA 实体）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 关联 Session |
| `toolName` | String | Tool 名称 |
| `inputJson` | String | 调用参数 JSON |
| `resultJson` | String? | 执行结果 JSON |
| `success` | boolean | 是否成功 |
| `errorMessage` | String? | 错误信息 |
| `durationMs` | long | 执行耗时 |
| `createdAt` | LocalDateTime | 调用时间 |

### 3. Session（核心记录 — oryxos-core，本节内存版）

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 会话标识（channel+user+profile 联合生成的公式由第 18 节 SessionManager 落地，本节由构造方提供） |
| `profileName` | String | 关联 Profile |
| `channel` | String | 接入渠道 |
| `userId` | String | 用户标识 |
| `messages` | List\<Message\> | 对话历史（累积：用户消息、LLM 响应、工具结果） |
| `status` | SessionStatus | ACTIVE / ARCHIVED |

**Message sealed 层次**（core 内联于 Session 或独立文件）:
- `Message.UserMessage(String content)` — 用户输入
- `Message.AssistantMessage(String content, List<ToolCall> toolCalls)` — LLM 响应（含可能的功能调用请求）
- `Message.ToolResultMessage(String toolCallId, String toolName, String content)` — 工具执行结果（引用 tool_call_id 供协议层配对）

**ToolCall**（独立值对象，Session 与 Response 共用）: `ToolCall(String id, String name, String arguments)` — 工具调用请求（arguments 为 JSON 字符串）。

> 第 18 节将 Session 升级为 JPA 实体，messages 序列化为 JSON 存入 `messages_json` 列。

### 4. Response（LLM 响应值对象 — oryxos-core）

`ProviderService.chat` 的返回类型（自有类型，不暴露 Spring AI）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | String | 模型文本回复 |
| `toolCalls` | List\<ToolCall\> | 模型请求的功能调用（无则为空） |

辅助方法: `hasToolCalls()` 判断是否需要执行工具。

### 5. Prompt（已修改 — oryxos-core）

扩展后的 Prompt 承载多轮上下文（向后兼容，旧构造器保留）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `systemMessage` | String? | system prompt（角色设定 + Bootstrap + Skill 正文 + 当前日期时间） |
| `messages` | List\<Session.Message\> | 会话历史消息（最近 N 轮截断后） |
| `availableTools` | List\<OryxTool\> | 当前可用工具列表 |
| `userMessage` | String | 当前轮用户消息（messages 为空时的单轮回退路径） |

## 接口定义（本节新建/迁移）

### ProviderService（oryxos-core，自 provider 迁移）

```java
public interface ProviderService {
    Response chat(String sessionId, Profile profile, Prompt prompt);
}
```

> `resolve(String)` 由第 16 节接口迁出，保留在 `DefaultProviderService` 具体类（当前无调用方）。

### ToolRegistry（oryxos-core）

```java
public interface ToolRegistry {
    Optional<OryxTool> get(String name);
    Collection<OryxTool> listAll();
}
```

### ToolInvocationStore（oryxos-core）

```java
public interface ToolInvocationStore {
    void save(ToolInvocationRecord record);
}
```

### SessionManager（oryxos-core）

```java
public interface SessionManager {
    void save(Session session);
}
```
