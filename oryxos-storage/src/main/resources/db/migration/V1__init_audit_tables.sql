-- OryxOS 审计表建表脚本（预留位）
--
-- 两张审计表 tool_invocations / llm_calls 核心阶段 day one 写入落库（constitution 原则五）。
-- SQLite 上 Hibernate 的 ddl-auto=update 对 ALTER TABLE 支持很弱，表结构变更不依赖自动迁移，
-- 以本脚本为唯一真相源手动维护。
--
-- 激活方式：US-5 持久化落地时，把本脚本交由 Flyway（V1__init_audit.sql）管理，
-- 或在 application.yaml 把 ddl-auto 调整为 update 前先手动执行本脚本。

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

CREATE TABLE IF NOT EXISTS llm_calls (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id        VARCHAR(255) NOT NULL,
    provider          VARCHAR(100) NOT NULL,
    model             VARCHAR(100) NOT NULL,
    prompt_tokens     INTEGER      DEFAULT 0,
    completion_tokens INTEGER      DEFAULT 0,
    total_tokens      INTEGER      DEFAULT 0,
    duration_ms       BIGINT       DEFAULT 0,
    success           BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message     TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_invocations_session ON tool_invocations (session_id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_session ON llm_calls (session_id);
