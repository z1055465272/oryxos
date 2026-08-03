-- OryxOS sessions 表建表脚本（第 18 节）
--
-- 会话持久化地基：session_id 由 SessionManager 按 channel+user+profile 唯一拼接，
-- 对话历史整体 JSON 序列化存 messages_json 一列（核心阶段不按条拆表）。
-- 字段照 TechnicalSolution §9.2。
-- SQLite 上 Hibernate 的 ddl-auto=update 对 ALTER TABLE 支持很弱，表结构变更以本脚本为真相源手动维护。

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

CREATE INDEX IF NOT EXISTS idx_sessions_channel_user ON sessions (channel, user_id);
