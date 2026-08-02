---
title: Features
---

# Features

The core phase prioritizes five core capabilities. On top of these five, a large number of real enterprise scenarios can be built.

| Capability | Description |
| --- | --- |
| **① LLM integration** | Connects mainstream models (DeepSeek, Tongyi, Kimi, Zhipu, Hunyuan, Doubao, Anthropic, OpenAI…) through a Provider abstraction. Agents are unaware of which model they call; switching at runtime carries no lock-in. Built on Spring AI — no reinventing the wheel. |
| **② ReAct loop** | The agent's brain. The LLM decides whether to call a tool and which one, reads the result, then decides the next step until it produces a final answer. The core loop is self-implemented (a few dozen lines of Java), not the Spring AI agent abstraction — fully controllable. |
| **③ Three-tier memory** | Session memory (current conversation) + long-term memory (`MEMORY.md` file, keeps user preferences and project context across conversations) + episodic memory (task process state, extension phase). An agent that remembers you across conversations is what separates an Agent OS from a chatbot. |
| **④ Plugin Tool system** | Five built-in tools (File / Shell / HTTP / save_memory / recall_memory) + a three-tier plugin approach (see below). This is how agents actually get enterprise work done. |
| **⑤ Web Service** | A complete REST API facade — business systems call an agent over HTTP. Ten endpoints in the core phase. This is the only channel through which enterprises embed AI into existing business systems. |

## Plugin tools, three tiers

Extend OryxOS by picking the lowest tier that fits:

| Tier | Barrier | How | Best for |
| --- | --- | --- | --- |
| **Zero-code** (recommended) | one markdown file | write a `SKILL.md` describing intent + reuse community MCP servers (GitHub, Slack, Notion…) — the LLM understands the task and composes calls itself | getting new scenarios live fast |
| **Light-code** | write an MCP server in any language | expose tools over the standard MCP protocol; OryxOS acts as an MCP client | connecting existing enterprise systems (ERP/CRM/CMDB) |
| **Heavy-code** | a Java Spring bean | write a Java method with `@Tool`, auto-registered at startup | deep integration with internal Java services, reusing existing Spring beans |

## Built on a set of decisions

The constitution is non-negotiable for OryxOS development:

1. **JDK 21 + Spring Boot 3.x monolith**, Maven multi-module (9 modules), single-binary deployment.
2. **Five core capabilities first**; enterprise governance (multi-tenancy / SSO / full audit / tool policy) comes in the extension phase.
3. **Self-implemented ReAct loop** — no Spring AI agent abstraction.
4. **Spring AI, half-used**: Provider abstraction, protocol conversion and `@Tool` JSON-schema generation only; **automatic tool execution is disabled** — `ReActLoop` + `ToolExecutor` do the scheduling, otherwise tools would be called twice.
5. **Three-tier plugin tools**, favoring zero-code `SKILL.md` + MCP.
6. **SQLite + MEMORY.md files** in the core phase; vector retrieval in the extension phase. Audit tables `tool_invocations` and `llm_calls` are written to the database from day one.
7. **A demonstrable demo after every user story** — working beats perfect.
