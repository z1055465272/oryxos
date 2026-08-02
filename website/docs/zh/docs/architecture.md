---
title: 系统架构
---

# 系统架构

OryxOS 是一个 Spring Boot 单体应用,对外只有两个入口——**CLI Channel** 和 **Web Service**。消息最终都汇入同一个 ReAct 引擎,引擎调度三块能力(Provider / Memory / Tool),之下是存储层。

![OryxOS 架构流程图](/flow.svg)

## Maven 模块(9 个)

| 模块 | 职责 |
| --- | --- |
| `oryxos-core` | 核心引擎:`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`ContextLoader`、`Session`、`Profile`、`OryxTool` 抽象。所有模块依赖它。 |
| `oryxos-provider` | 能力一:`ProviderService`、Function Calling 适配、provider name 显式映射。 |
| `oryxos-memory` | 能力三:`MemoryService`(三层统一门面)、`LongTermMemory`、`MemoryTools`。 |
| `oryxos-tool` | 能力四:内置 Tool(File/Shell/Http)、MCP Client、`ToolRegistry`、`SandboxChecker`(三合一)。 |
| `oryxos-web` | 能力五:`WebServer`、6 个 `ApiController`、`GlobalExceptionHandler`、OpenAPI。 |
| `oryxos-channel-cli` | 支撑:CLI Channel。 |
| `oryxos-storage` | 支撑:SQLite(`sessions`、`tool_invocations`、`llm_calls` 三表)。 |
| `oryxos-cli` | 支撑:Picocli 命令行入口(12 子命令)。 |
| `oryxos-boot` | 支撑:Spring Boot 启动模块,打成 fat JAR。 |

模块间通过接口解耦:Provider / Memory / Tool 供养 ReAct 引擎,引擎能力经 CLI 与 Web Service 对外提供。扩展阶段加新 Channel / Provider / Tool 只在边缘扩展,不动核心引擎。

## 关键技术决策

- **同步阻塞 + Java 21 virtual thread**,不用响应式。HTTP 层用 Spring MVC + virtual thread,单机撑几千并发。
- **Sandbox 用 Path/Pattern 白名单**(文件路径、Shell 命令、HTTP 域名),在应用层校验。不用 SecurityManager(JDK 17 起废弃、JDK 21 已不可用)。完整容器级沙箱(bwrap/Docker/K8s pod)放扩展。
- **Memory 三层统一门面**:`MemoryService` 对 ReAct 循环只暴露一个接口,内部把会话记忆委托给 `SessionManager`、长期记忆委托给 `LongTermMemory`。核心阶段做会话 + 长期两层(`MEMORY.md` 文件 + `save_memory`/`recall_memory` 两内置 Tool),情景记忆和向量检索放扩展。`recallByKeyword` 接口预留向量检索升级空间。
- **MEMORY.md vs USER.md**:`USER.md` 是 Bootstrap 文件(用户手写、OryxOS 只读不写,是用户"初始设定");`MEMORY.md` 是长期记忆(Agent 通过 `save_memory` 写入、OryxOS 读写,是 Agent"成长记录")。两者都进 system prompt,但来源和生命周期不同。
- **持久化**:Profile/Bootstrap/Memory/SKILL.md/MCP 配置放文件系统(用户可编辑、git 跟踪);Session/审计/元数据落 SQLite(Spring Data JPA)。注意 SQLite 的 `hibernate.ddl-auto=update` 对表结构演进支持弱,表结构变更需手动维护建表脚本或引入 Flyway/Liquibase。

## 敏感配置

LLM API key、Provider 凭证、MCP server 凭证通过环境变量注入或独立本地配置文件加载,**不明文写死在 Profile YAML 里**(Profile 里用 `${ENV_VAR}` 占位,加载时从环境变量解析),配置加载时做必填项和格式校验。完整加密存储/密钥轮转/对接企业 KMS/Vault 放扩展阶段。
