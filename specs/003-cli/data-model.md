# Data Model: CLI 命令行入口 + 会话持久化地基

**Feature**: CLI 命令行入口 + 会话持久化地基 | **Date**: 2026-08-03

## 实体清单

### 1. Session（core 值对象 — oryxos-core，第 17 节交付，本节不改字段）

引擎内存版会话，ReActLoop 直接累积消息。本节为其增加持久化能力（经 SessionEntity），字段保持不动。

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 会话标识（SessionManager 内部由 channel+user+profile 拼接，本节才正式唯一生成） |
| `profileName` | String | 关联 Agent/Profile |
| `channel` | String | 接入渠道（cli/web/scheduler） |
| `userId` | String | 用户标识 |
| `messages` | List<Message> | 对话历史（sealed：UserMessage/AssistantMessage/ToolResultMessage） |
| `status` | Status | ACTIVE / ARCHIVED |

### 2. SessionEntity（JPA 实体 — oryxos-storage，本节新增）

`com.oryxos.storage.SessionEntity`，映射 `sessions` 表。字段照 TechnicalSolution §9.2 逐列。

| 字段 | 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|------|
| `sessionId` | `session_id` | VARCHAR PK | NOT NULL | 主键，SessionManager 按 channel+user+profile 唯一拼接 |
| `profileName` | `profile_name` | VARCHAR | NOT NULL | 关联 Profile |
| `channel` | `channel` | VARCHAR | NOT NULL | 接入渠道 |
| `userId` | `user_id` | VARCHAR | NOT NULL | 用户标识 |
| `messagesJson` | `messages_json` | TEXT | NOT NULL | JSON 序列化的对话历史（空历史存 `[]`） |
| `status` | `status` | VARCHAR | NOT NULL | `active` / `archived` |
| `createdAt` | `created_at` | TIMESTAMP | NOT NULL | 创建时间 |
| `lastActiveAt` | `last_active_at` | TIMESTAMP | NOT NULL | 最后活跃时间 |
| `archivedAt` | `archived_at` | TIMESTAMP | NULLABLE | 归档时间（可空） |

**序列化**：`messages_json` 用 Jackson `ObjectMapper` 编码/解码 `Session.messages()`。三型消息带判别字段：
- `UserMessage` → `{"type":"user","content":...}`
- `AssistantMessage` → `{"type":"assistant","content":...,"toolCalls":[...]}`
- `ToolResultMessage` → `{"type":"tool_result","toolCallId":...,"toolName":...,"content":...}`

编解码方法收在 `SessionEntity` 静态方法（`encodeMessages`/`decodeMessages`），测试断言往返一致。

**DDL**（V2__create_sessions_table.sql，`CREATE TABLE IF NOT EXISTS` 幂等）：

```sql
CREATE TABLE IF NOT EXISTS sessions (
    session_id      VARCHAR(255) PRIMARY KEY,
    profile_name    VARCHAR(255) NOT NULL,
    channel         VARCHAR(100) NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    messages_json   TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at     TIMESTAMP
);
```

### 3. SessionManager（接口契约 — oryxos-core，本节扩展为三方法）

| 方法 | 签名 | 行为 |
|------|------|------|
| `getOrCreate` | `Session getOrCreate(String channel, String user, String profileName)` | 按三元组幂等获取或创建；session_id 唯一拼接只在此处 |
| `get` | `Optional<Session> get(String sessionId)` | 按会话标识查询 |
| `save` | `void save(Session session)` | 持久化累积完的历史 |

## 状态流转

```
getOrCreate(三元组) ──新──▶ Session(status=ACTIVE, messages=[])
        │
        ├─已存在──▶ 返回既有 Session（幂等，历史保留）
        │
save(session) ──▶ 更新 messages_json + last_active_at
markArchived() ──▶ status=ARCHIVED + archived_at（核心阶段保留能力，CLI 不触发）
```

**幂等关键**：同一三元组 → 同一 session_id → 同一行 → 返回同一 Session（多轮对话靠它串起来）；三元组任一不同 → 不同 session_id → 不同会话。
