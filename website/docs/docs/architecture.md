---
title: Architecture
---

# Architecture

OryxOS is a Spring Boot monolith with two entry points — the **CLI channel** and the **Web service**. Messages from both converge into the same ReAct engine, which orchestrates three capabilities (Provider / Memory / Tool) on top of the storage layer.

![OryxOS architecture flow](/flow.svg)

## Maven modules (9)

| Module | Responsibility |
| --- | --- |
| `oryxos-core` | Core engine: `ReActLoop`, `PromptBuilder`, `ToolExecutor`, `ContextLoader`, `Session`, `Profile`, `OryxTool` abstraction. Every module depends on it. |
| `oryxos-provider` | Capability ①: `ProviderService`, function-calling adaptation, explicit provider-name mapping. |
| `oryxos-memory` | Capability ③: `MemoryService` (three-tier unified facade), `LongTermMemory`, `MemoryTools`. |
| `oryxos-tool` | Capability ④: built-in tools (File/Shell/Http), MCP client, `ToolRegistry`, `SandboxChecker` (one module, three-in-one). |
| `oryxos-web` | Capability ⑤: `WebServer`, 6 `ApiController`s, `GlobalExceptionHandler`, OpenAPI. |
| `oryxos-channel-cli` | Support: CLI channel. |
| `oryxos-storage` | Support: SQLite (`sessions`, `tool_invocations`, `llm_calls` tables). |
| `oryxos-cli` | Support: Picocli command-line entry (12 subcommands). |
| `oryxos-boot` | Support: Spring Boot startup module, packaged as a fat JAR. |

The relationship: Provider / Memory / Tool feed the ReAct engine; the engine's capabilities are exposed outward through the CLI and the Web service. Modules are decoupled through interfaces — in the extension phase, new channels, providers and tools are added at the edges without touching the core engine.

## Key technical decisions

- **Synchronous blocking + Java 21 virtual threads**, not reactive. Spring MVC + virtual threads at the HTTP layer; thousands of concurrent requests on a single machine.
- **Sandbox via Path/Pattern whitelists** (file paths, shell commands, HTTP domains), validated at the application layer. No SecurityManager (deprecated since JDK 17, unavailable in JDK 21). Full container-grade sandboxing (bwrap/Docker/K8s pod) is an extension-phase item.
- **Three-tier memory as one facade**: `MemoryService` exposes a single interface to the ReAct loop and delegates internally to `SessionManager` (session memory) and `LongTermMemory` (long-term memory). Core phase ships session + long-term (`MEMORY.md` file + the `save_memory`/`recall_memory` built-in tools); episodic memory and vector retrieval come in the extension phase. `recallByKeyword` reserves headroom for vector retrieval upgrades.
- **MEMORY.md vs USER.md**: `USER.md` is a bootstrap file (written by the user, read-only for OryxOS — the user's "initial settings"); `MEMORY.md` is long-term memory (written by the agent via `save_memory` — the agent's "growth record"). Both enter the system prompt, but with different sources and lifecycles.
- **Persistence**: Profile / bootstrap / memory / SKILL.md / MCP configuration live on the filesystem (user-editable, git-tracked); sessions / audit / metadata go to SQLite (Spring Data JPA). Note that SQLite's `hibernate.ddl-auto=update` is weak at schema evolution — table changes require manually maintained DDL scripts or Flyway/Liquibase.

## Sensitive configuration

LLM API keys, provider credentials and MCP server credentials are injected through environment variables or independent local config files — **never written in plaintext into Profile YAML** (Profiles use `${ENV_VAR}` placeholders resolved at load time), with required-field and format validation at load time. Full encryption, key rotation and enterprise KMS/Vault integration are extension-phase items.
