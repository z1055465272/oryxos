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

**DDL** (已在 V1__init_audit_tables.sql):

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

### 2. Session（核心记录 — oryxos-core，本节内存版）

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | channel + user + profile 联合生成 |
| `profileName` | String | 关联 Profile |
| `messages` | List\<Message\> | 对话历史（含用户消息、LLM 响应、tool 结果） |
| `status` | SessionStatus | ACTIVE / ARCHIVED |

**Message 子类型**:
- `UserMessage(String content)` — 用户输入
- `LlmResponseMessage(String content, List\<ToolCallRequest\> toolCalls)` — LLM 响应
- `ToolResultMessage(String toolName, String content)` — 工具执行结果

> 第 18 节将此 record 升级为 JPA 实体，messages 序列化为 JSON 存入 `messages_json` 列。

### 3. Prompt（已修改 — oryxos-core）

扩展后的 Prompt 承载多轮上下文：

| 字段 | 类型 | 说明 |
|------|------|------|
| `systemMessage` | String | system prompt（ContextLoader 提供） |
| `messages` | List\<Message\> | 会话历史消息（最近 N 轮） |
| `availableTools` | List\<OryxTool\> | 当前可用工具列表 |
| `userMessage` | String | 当前轮用户消息（首轮）或上轮上下文 |

> 为保持向后兼容，`userMessage` 保留——单轮简单场景仍可直接使用。

## 接口定义（本节新建）

### ToolRegistry（oryxos-core）

```java
public interface ToolRegistry {
    Optional<OryxTool> get(String name);
    Collection<OryxTool> listAll();
}
```

### SessionManager（oryxos-core）

```java
public interface SessionManager {
    void save(Session session);
    Optional<Session> findById(String sessionId);
}
```
