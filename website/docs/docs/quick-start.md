---
title: Quick Start
---

# Quick Start

## Prerequisites

- JDK 21 or newer
- Maven 3.9+ — or just use the repository's Maven Wrapper `./mvnw` (no Maven install needed)
- An LLM API key (e.g. [DeepSeek](https://platform.deepseek.com/) or [Kimi](https://platform.moonshot.cn/))

## Build & run

```bash
# 1. Build the fat JAR
./mvnw clean package

# 2. Initialize the workspace (creates .oryxos/ in the current directory)
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar init

# 3. Configure the LLM API key via environment variable (never in plaintext config)
export DEEPSEEK_API_KEY=sk-xxx

# 4. Edit the default Profile — provider and model
#    .oryxos/profiles/default.yaml

# 5. Interactive conversation (the main development/debug path)
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar chat

# 6. Or start the HTTP API service for business systems to integrate
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar serve  # port 8080
```

## Call over REST

```bash
# Create a session
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"profile":"default","user_id":"u1"}'

# Send a message (keeps session context)
curl -X POST http://localhost:8080/api/v1/sessions/{id}/messages \
  -H "Content-Type: application/json" \
  -d '{"message":"查一下北京天气并告诉我穿什么"}'

# Stateless single agent call (good for short tasks)
curl -X POST http://localhost:8080/api/v1/agents/default/invoke \
  -H "Content-Type: application/json" \
  -d '{"message":"查一下北京天气并告诉我穿什么"}'
```

Full API docs are available at `http://localhost:8080/swagger-ui` after startup (OpenAPI 3.0).

## Workspace layout

`oryxos init` generates the `.oryxos/` workspace:

```text
.oryxos/
├── profiles/          # Profile YAML (one per agent)
├── sessions/          # session data
├── skills/            # SKILL.md files
├── memory/MEMORY.md   # long-term memory (agent-written via save_memory)
├── logs/              # structured logs
├── tools/             # custom tool configuration
├── mcp_servers.yaml   # MCP server configuration
├── oryxos.db          # SQLite (sessions + audit tables)
├── AGENTS.md          # bootstrap: project-level agent behavior
├── SOUL.md            # bootstrap: agent personality
└── USER.md            # bootstrap: user preferences
```

## CLI commands

Three run modes share the same Profile configuration and session storage:

| Command | Description |
| --- | --- |
| `oryxos chat` | Interactive multi-turn conversation (optional `--profile`) |
| `oryxos serve` | HTTP API service (default port 8080) |
| `oryxos gateway` | Resident daemon across channels (CLI + HTTP in the core phase) |

Core 12 commands: `init`, `status`, `chat`, `serve`, `gateway`, `profile list/create/show/delete`, `provider list`, `tool list`, `session list`.
