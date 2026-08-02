---
title: 功能特性
---

# 功能特性

核心阶段优先做五大核心能力,基于这五个能力可以扩展出企业里大量真实场景。

| 能力 | 说明 |
| --- | --- |
| **① 对接 LLM** | 通过 Provider 抽象层对接主流大模型(DeepSeek、通义、Kimi、智谱、混元、豆包、Anthropic、OpenAI 等),Agent 不感知具体调的是哪家,运行时切换无 lock-in。基于 Spring AI,不重复造轮子。 |
| **② ReAct 循环** | Agent 的大脑。LLM 思考是否调工具、调哪个,调用后看结果再决定下一步,直到给出最终响应。核心循环自实现(约数十行 Java),不依赖 Spring AI 的 Agent 抽象,完全可控。 |
| **③ Memory 三层记忆** | 会话记忆(当前对话历史)+ 长期记忆(`MEMORY.md` 文件,跨对话保留用户偏好/项目背景)+ 情景记忆(任务过程状态,扩展阶段)。让 Agent 跨对话记住用户,这是 Agent OS 区别于 chatbot 的核心体验。 |
| **④ Plugin Tool 体系** | 内置 5 个基础 Tool(文件/Shell/HTTP/save_memory/recall_memory)+ Plugin 三档接入(见下)。让 Agent 真正能干企业的活。 |
| **⑤ Web Service** | 完整 REST API 对外门面,业务系统用 HTTP 调一下就能用上 Agent。核心阶段 10 个端点。这是企业把 AI 能力嵌入已有业务系统的唯一通道。 |

## Plugin Tool 三档接入

业务方扩展 OryxOS 能力,按门槛从低到高三种方式,**能用低门槛就不用高门槛**:

| 方式 | 门槛 | 做法 | 适合场景 |
| --- | --- | --- | --- |
| **零代码**(主推) | 写一份 markdown | 写 `SKILL.md` 描述意图 + 复用社区现成 MCP server(GitHub/Slack/Notion…),LLM 自己理解任务、组合调用 | 快速上线新场景 |
| **轻代码** | 任何语言写 MCP server | 通过标准 MCP 协议暴露工具,OryxOS 作为 MCP Client 接入 | 接入企业自有系统(ERP/CRM/CMDB) |
| **重代码** | Java Spring Bean | 用 `@Tool` 注解写 Java 方法,启动时自动扫描注册 | 深度集成企业内部 Java 服务、复用现有 Spring Bean |

## 非协商原则(Constitution)

OryxOS 开发的非协商原则,不可由 AI 自行修改:

1. **JDK 21 + Spring Boot 3.x 单体应用**,Maven 多模块(9 个),单二进制部署。
2. **五大核心能力优先**,企业级治理(多租户/SSO/完整审计/Tool Policy)放扩展阶段。
3. **自实现 ReAct loop**,不用 Spring AI 的 Agent 抽象。
4. **Spring AI 只用一半**:只用它的 Provider 抽象、协议转换、`@Tool` 的 JSON Schema 生成;**禁用自动 tool 执行**,tool 调度完全由 `ReActLoop` + `ToolExecutor` 控制——否则 tool 会被调两次。
5. **Plugin Tool 三档接入**,主推 SKILL.md + MCP 零代码方式。
6. **核心阶段 SQLite + MEMORY.md 文件存储**,向量检索放扩展;审计相关的 `tool_invocations` 和 `llm_calls` 两张表核心阶段就写入落库(可审计地基 day one 立起来)。
7. **每个 user story 完成后有可演示 demo**,优先级是跑通而非完美。
