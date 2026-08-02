---
title: What is OryxOS
---

# What is OryxOS

OryxOS is a **Java-native Agent OS** for the enterprise — a base system that runs and manages AI agents. It is installed on the enterprise's own infrastructure, provides a unified runtime for business agents (ops assistant, customer service, HR assistant, sales assistant, knowledge management…), and sits on top of the enterprise's model, channel, tool, memory, identity and audit infrastructure.

Business teams **configure** agents — prompt, model, tool list, channel binding — and the agents run. To give an agent new abilities, teams write tools that plug into existing enterprise systems. **Agents are configured, not coded.**

## Why OryxOS

The industry already agrees that enterprises need AI agents. The hard part is not "making an agent" — it is "running it controllably inside the enterprise." Most agent pilots never reach production: integration, data access, delivery cost, change management, security governance — none of these are "the model isn't strong enough," all of them are "the base isn't stable or controllable enough."

For **strictly regulated enterprises** (banks, government, telecom, energy, healthcare), the requirement is hard and non-negotiable:

- Core data cannot leave the enterprise
- Systems must be fully auditable
- New components must pass existing security and compliance processes
- The technology stack must align with the existing estate

They will not run core business agents on SaaS, they will not run them on products bound to a public cloud, and they are unlikely to put a project with a CVE history and loose default permissions into production. **They need a privately deployable, fully auditable agent base that fits their existing IT governance and technology stack.** OryxOS fills this position.

## Design goals

Four words: **unified, private, easy to integrate, observable**.

- **Unified** — multiple agents inside one enterprise share a single base; adding a new agent does not reinvent the wheel.
- **Private** — data and deployment stay entirely in the enterprise's hands; models can be external APIs or local Ollama / vLLM.
- **Easy to integrate** — a standard Spring Boot project structure connects directly to existing ERP/CRM/CMDB/SSO/monitoring; tools can be written in any language via MCP.
- **Observable** — standard Prometheus metrics, structured logs, health checks — fit into the enterprise's existing monitoring stack.

## Positioning

| | Artifact | Who uses it | Layer | Relation to OryxOS |
| --- | --- | --- | --- | --- |
| **Framework** (LangChain / Spring AI) | code | developers | lowest-level component | reused by OryxOS (for LLM calls) |
| **Orchestration platform** (Dify / Coze) | a workflow | business users dragging | application layer | can run on top of OryxOS |
| **Vendor middleware / SaaS** (Glean / Bedrock AgentCore) | full app | enterprise buyers | SaaS, cloud-bound | different quadrant, not privately deployable |
| **OryxOS** | configured resident agents | business teams + tool writers | runtime layer | the runtime itself, self-hosted |

One line: *frameworks give you code and you build your own runtime; orchestration platforms give you workflows running on top of a runtime; OryxOS gives you the runtime itself — a base on which agents run resident, governable and auditable.*

## Design documents

- [Industry research](https://github.com/oryxos/oryxos/tree/main/docs/IndustryResearch.md) — what an Agent OS is, what the industry has done, where Java is missing
- [Requirements](https://github.com/oryxos/oryxos/tree/main/docs/RequirementDoc.md) — five core capabilities and support modules
- [Technical solution](https://github.com/oryxos/oryxos/tree/main/docs/TechnicalSolution.md) — key decisions, module architecture, persistence
- [Implementation guide](https://github.com/oryxos/oryxos/tree/main/docs/AiProgrammingGuilde.md) — Spec-Kit workflow and the five user stories
