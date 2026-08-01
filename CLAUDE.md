# CLAUDE.md

本文件为 Claude Code 在 OryxOS 仓库工作时提供项目专属指引。修改任何核心能力前，先读 docs/ 下对应文档章节。

## 项目概述

OryxOS 是基于 Java 实现的、面向企业（尤其严监管企业）的私有可审计 Agent OS 统一底座。装在企业自己的 K8s/服务器上，作为底座跑各种业务 Agent（运维、客服、HR、销售、知识管理等），共享渠道接入、模型路由、工具调用、记忆系统、沙箱执行能力。数据完全留在企业基础设施，不锁云生态。

技术栈一句话：**JDK 21 + Spring Boot 3.x + Spring AI Alibaba + 自实现 ReAct loop + SQLite + Picocli**。

核心阶段交付 Agent OS 的运行时内核（能力上对齐业界开源 Agent OS 基础层）；让 OryxOS 成为真正企业级 Agent OS 的治理层（多租户/SSO/完整审计/Tool Policy）放扩展阶段。背景见 [docs/IndustryResearch.md](docs/IndustryResearch.md)。

## 当前状态

Greenfield 项目，已完成 Maven 多模块骨架（9 个模块），`./mvnw clean package` 构建通过、fat JAR 可启动，docs/ 下四份设计文档齐备，尚未初始化 git。按需求文档 4 周（每周 3 小时，合计 12 小时）节奏推进主体开发，5 个 user story 对应五大核心能力。开发方法论见 [docs/AiProgrammingGuilde.md](docs/AiProgrammingGuilde.md)。

## 非协商原则（Constitution）

以下原则不可由 AI 自行修改，写代码时必须遵守：

1. **JDK 21 + Spring Boot 3.x 单体应用**，Maven 多模块（9 个），单二进制部署。
2. **五大核心能力优先**（对接 LLM / ReAct / Memory / Tool / Web Service），支撑模块次之；核心阶段交付运行时内核，企业级治理层放扩展阶段。
3. **自实现 ReAct loop**，不用 Spring AI 的 Agent 抽象。
4. **Spring AI 只用一半**：只用它的 Provider 抽象、协议转换、`@Tool` 的 JSON Schema 生成；**禁用 Spring AI 的自动 tool 执行**，tool 调度完全由 `ReActLoop` + `ToolExecutor` 控制——否则 tool 会被调两次。
5. **Plugin Tool 三档接入**，主推 SKILL.md + MCP 零代码方式。
6. **核心阶段 SQLite + MEMORY.md 文件存储**，向量检索放扩展；审计相关的 `tool_invocations` 和 `llm_calls` 两张表核心阶段就写入落库（可审计地基 day one 立起来）。
7. **每个 user story 完成后有可演示 demo**，优先级是跑通而非完美。

## 最容易写错的点（每次实现/审查重点核对）

这几处是 OryxOS 最容易被 AI 写错的地方：

- **Spring AI 自动 tool 执行没禁用** → tool 被调两次。调用 Spring AI 时只用协议转换和 schema 生成，tool 的实际调度由 `ToolExecutor` 控制。
- **Provider 用类型扫描** → 多 Provider 并存时 Bean 类型相同会有歧义。必须维护 provider name → `ChatModel` 的**显式映射**，不靠扫描容器里所有 `ChatModel`。
- **Tool 被拆成多个模块** → 应合并为单个 `oryxos-tool` 模块（内置 Tool / MCP Client / `ToolRegistry` / `SandboxChecker` 三合一）。
- **SkillLoader 当成 Tool** → SKILL.md 是注入 system prompt 的指令模板，归 `oryxos-core` 的 `ContextLoader`，不归 Tool 模块。
- **审计表没落库** → `tool_invocations`、`llm_calls` 核心阶段就写入（不一定做查询接口），不能只靠日志后期反解析。

## 架构（9 个 Maven 模块）

| 模块 | 职责 |
| --- | --- |
| `oryxos-core` | 核心引擎：`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`ContextLoader`、`Session`、`Profile`、`OryxTool` 抽象。所有模块依赖它 |
| `oryxos-provider` | 能力一：`ProviderService`、Function Calling 适配、provider name 映射 |
| `oryxos-memory` | 能力三：`MemoryService`（三层统一门面）、`LongTermMemory`、`MemoryTools` |
| `oryxos-tool` | 能力四：内置 Tool（File/Shell/Http）、MCP Client、`ToolRegistry`、`SandboxChecker`（三合一） |
| `oryxos-web` | 能力五：`WebServer`、6 个 `ApiController`、`GlobalExceptionHandler`、OpenAPI |
| `oryxos-channel-cli` | 支撑：CLI Channel |
| `oryxos-storage` | 支撑：SQLite（`sessions`、`tool_invocations`、`llm_calls` 三表） |
| `oryxos-cli` | 支撑：Picocli 命令行入口（12 子命令） |
| `oryxos-boot` | 支撑：Spring Boot 启动模块，打成 fat JAR |

五大能力关系：Provider/Memory/Tool 三个能力供养 ReAct 循环这个引擎，引擎跑出的能力通过 CLI 和 Web Service 两个入口对外提供。模块间通过接口解耦，扩展阶段加新 Channel/Provider/Tool 只在边缘扩展，不动核心引擎。

## 关键技术决策

- **同步阻塞 + Java 21 virtual thread**，不用响应式。HTTP 层用 Spring MVC + virtual thread，单机撑几千并发。
- **Sandbox 用 Path/Pattern 白名单**（文件路径、Shell 命令、HTTP 域名），在应用层校验。不用 SecurityManager（JDK 17 起废弃、JDK 21 已不可用）。完整容器级沙箱（bwrap/Docker/K8s pod）放扩展。
- **Memory 三层统一门面**：`MemoryService` 对 ReAct 循环只暴露一个接口，内部把会话记忆委托给 `SessionManager`、长期记忆委托给 `LongTermMemory`。核心阶段做会话 + 长期两层（`MEMORY.md` 文件 + `save_memory`/`recall_memory` 两内置 Tool），情景记忆和向量检索放扩展。`recallByKeyword` 接口预留向量检索升级空间（可升级为带 mode 参数的 `recall`）。
- **MEMORY.md vs USER.md**：`USER.md` 是 Bootstrap 文件（用户手写、OryxOS 只读不写，是用户"初始设定"）；`MEMORY.md` 是长期记忆（Agent 通过 `save_memory` 写入、OryxOS 读写，是 Agent"成长记录"）。两者都进 system prompt，但来源和生命周期不同。
- **持久化**：Profile/Bootstrap/Memory/SKILL.md/MCP 配置放文件系统（用户可编辑、git 跟踪）；Session/审计/元数据落 SQLite（Spring Data JPA）。注意 SQLite 的 `hibernate.ddl-auto=update` 对表结构演进支持弱，表结构变更需手动维护建表脚本或引入 Flyway/Liquibase，不要依赖 update 自动迁移。

## 工作区结构

`oryxos init` 生成 `.oryxos/` 工作目录：

```text
.oryxos/
├── profiles/          # Profile YAML（每个 Agent 一个）
├── sessions/          # 会话数据
├── skills/            # SKILL.md 文件
├── memory/MEMORY.md   # 长期记忆
├── logs/              # 结构化日志
├── tools/             # 自定义 Tool 配置
├── mcp_servers.yaml   # MCP server 配置
├── oryxos.db          # SQLite
├── AGENTS.md          # Bootstrap：项目级 agent 行为说明
├── SOUL.md            # Bootstrap：agent 人格定义
└── USER.md            # Bootstrap：用户偏好
```

## 命令

三种运行模式（共享同一份 Profile 配置和 Session 存储）：
- `oryxos chat` — 交互式多轮对话（开发调试主要方式，可选 `--profile`）
- `oryxos serve` — HTTP API 服务（默认端口 8080）
- `oryxos gateway` — 常驻守护进程（多 Channel，核心阶段只挂 CLI 和 HTTP API）

核心 12 个命令：`init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`。

构建：`./mvnw clean package`（仓库自带 Maven Wrapper，无需系统 Maven）生成 fat JAR，产物在 `oryxos-boot/target/`，`java -jar` 启动。扩展阶段通过 GraalVM Native Image 编译原生二进制。

**构建环境注意（本机）**：默认 `JAVA_HOME` 指向 JDK 8，构建前需 `export JAVA_HOME="D:/envconfig/JDK/jdk-21.0.11"`（Maven 走 `JAVA_HOME`，不依赖 PATH 里的 `java`）。

## 开发流程

主体开发用 Spec-Kit 跑 spec-driven 流程（constitution → specify → plan → tasks → implement），5 个 user story 按依赖顺序推进：

- **US-1** 对接 LLM（无依赖）
- **US-2** ReAct 循环（依赖 US-1）
- **US-3** Memory 三层记忆 ‖ **US-4** Plugin Tool 体系（都依赖 US-2，可并行）
- **US-5** Web Service（依赖前 4 个，收口）

每个 user story 完成后跑 `/speckit.analyze` 检查 spec 与代码一致性，对应需求文档第 13 章 5 个验收 demo。增量阶段（小颗粒度改动、修 bug、加 Plugin Tool）切手动提示词 + Claude Code，不走 Spec-Kit 完整流程。

核心阶段 10 个 REST 端点（会话管理 4 / Agent 调用 1 / Profile·Memory·Tool 列表 3 / health·info 2）详见 [docs/RequirementDoc.md](docs/RequirementDoc.md) 第 5.8 节。

## 敏感配置

LLM API key、Provider 凭证、MCP server 凭证通过环境变量注入或独立本地配置文件加载，**不明文写死在 Profile YAML 里**（Profile 里用 `${ENV_VAR}` 占位，加载时从环境变量解析），配置加载时做必填项和格式校验。完整加密存储/密钥轮转/对接企业 KMS/Vault 放扩展阶段。

## 参考文档

- [docs/IndustryResearch.md](docs/IndustryResearch.md) — 业界调研（为什么做、定位、Java 生态缺位、单机→分布式→Agent 协作演进）
- [docs/RequirementDoc.md](docs/RequirementDoc.md) — 需求文档（What：五大能力 + 支撑模块 + 4 周节奏 + 5 验收 demo）
- [docs/TechnicalSolution.md](docs/TechnicalSolution.md) — 技术方案（How：7 关键决策 + 9 模块 + 持久化 + 5 端到端流程）
- [docs/AiProgrammingGuilde.md](docs/AiProgrammingGuilde.md) — AI 编程实施指引（Spec-Kit + 5 user story 拆解 + 增量阶段）

技术方案是 Spec-Kit `/speckit.plan` 的输入，喂文档时务必用最新版（模块是 9 个，constitution 要含"Spring AI 只用一半""审计 day one 落库"等决策），否则生成的 plan 会按旧结构走偏。
