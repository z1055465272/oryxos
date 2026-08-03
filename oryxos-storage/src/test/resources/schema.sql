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
