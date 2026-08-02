---
title: Roadmap
---

# Roadmap

OryxOS is delivered in two stages: the **core phase** makes the agent-OS runtime kernel solid in Java (capability-aligned with open-source agent-OS base layers); the truly differentiating **governance layer** is built above the core kernel in the extension phase, with community help. The core phase is the foundation; enterprise governance is the endgame.

## Core phase (4 weeks, ~3h per week)

| Week | Core capability | Demo by the weekend |
| --- | --- | --- |
| Week 1 | LLM integration + ReAct loop | The agent holds multi-turn conversations and calls an HTTP tool to complete a simple task |
| Week 2 | Memory + Tool system | The agent remembers preferences, reads/writes files, calls external MCP tools |
| Week 3 | Web Service | External systems call OryxOS through 10 REST endpoints |
| Week 4 | Multi-agent demo + engineering wrap-up | Multiple agents coexist, full CLI, sessions survive restart, the homepage is reachable |

## Extension phase (community)

- **Channels & models**: multi-channel (WeCom/Feishu/DingTalk/Slack/email), provider fallback & reliability, adaptive routing
- **Memory & capabilities**: automatic memory extraction, semantic retrieval (vector DB), episodic memory, Memory Wiki, complete skill system
- **Tools & security**: expose MCP servers, tool policy, tool LRU loading, full sandbox isolation (Docker/K8s pod)
- **Governance & operations**: web dashboard, SSO & multi-tenancy (SAML/OIDC), full audit & traceability, clustered deployment & high availability
- **Enterprise integration**: ERP/CRM/CMDB/monitoring/intranet-knowledge-base connectors

## Community building

Skills marketplace, multi-language SDKs (Java/Python/TypeScript/Go), visual Profile editor, native file generation, multi-region deployment, Kubernetes operator, mobile management console, voice channel, RISC-V and edge deployment.
