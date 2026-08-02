---
title: OryxOS 是什么
---

# OryxOS 是什么

OryxOS 是基于 Java 实现的、面向企业的 **Agent OS**——运行和管理 AI Agent 的底座系统。它装在企业自己的基础设施上,向上为各类业务 Agent(运维助手、客服助手、HR 助手、销售助手、知识管理助手等)提供统一的运行环境,向下接入模型、渠道、工具、记忆、身份和审计基础设施。

业务方在 OryxOS 上**配置** Agent(prompt、模型、Tool 列表、渠道绑定),Agent 跑起来;写 Tool 接入企业自有系统,Agent 就能用上。**业务方不需要写 Agent 后端代码,Agent 是配置出来的,不是写代码写出来的。**

## 为什么需要 OryxOS

业界对企业 AI Agent 的需求已经形成共识,但真正的难点不在"做出一个 Agent",而在"让它在企业里可控地跑起来"。大量 agent pilot 永远到不了 production——集成、数据访问、实施成本、变更管理、安全治理,没有一个是"模型不够强"能解决的,全都是"底座不够稳、不够可控"的问题。

对银行、政府、电信、能源、医疗这些**严监管企业**,需求是确定且刚性的:

- 核心数据不能出企业
- 系统必须完全可审计
- 新组件要过现有安全合规流程
- 技术栈要跟现有体系对齐

他们不会把核心业务 Agent 跑在 SaaS 上,不会跑在绑定公有云的产品上,也很难把一个有 CVE 史、默认权限宽松的项目放进生产。**他们需要一个私有部署、完全可审计、能纳入现有 IT 治理、跟现有技术栈对齐的 Agent 底座。** OryxOS 填这个位置。

## 设计目标

四个词:**统一、私有、易接入、可观测**。

- **统一**——企业内多个 Agent 共享一套底座,上一个新 Agent 不用重复造轮子。
- **私有**——数据和部署完全在企业自己手里,模型可接外部 API 也可用本地 Ollama / vLLM。
- **易接入**——基于标准 Spring Boot 工程结构,跟现有 ERP/CRM/CMDB/SSO/监控直接对接,Tool 用 MCP 任何语言都能写。
- **可观测**——标准 Prometheus 指标、结构化日志、健康检查,适配企业现有监控告警体系。

## 定位

| | 产物 | 谁来用 | 跑在哪层 | 跟 OryxOS 的关系 |
| --- | --- | --- | --- | --- |
| **框架**(LangChain / Spring AI) | 代码 | 开发者 | 最底层组件 | 被 OryxOS 复用(做 LLM 调用) |
| **编排平台**(Dify / Coze) | 一条 workflow | 业务人员拖拽 | 应用层 | 可跑在 OryxOS 之上 |
| **大厂中台 / SaaS**(Glean / Bedrock AgentCore) | 完整应用 | 企业采购方 | SaaS,绑云生态 | 不同象限,不可私有部署 |
| **OryxOS** | 配置出来的常驻 Agent | 业务方配置 + 写 Tool | 运行时层 | 装在自己机器上的运行时 |

一句话:**框架给你代码要你自己搭运行环境;编排平台给你流程跑在运行时之上;OryxOS 给你运行时本身——让 Agent 能常驻、可治理、可审计地跑起来的底座。**

## 设计文档

- [业界调研](https://github.com/oryxos/oryxos/tree/main/docs/IndustryResearch.md) — 什么是 Agent OS、业界做到哪、Java 生态缺在哪
- [需求文档](https://github.com/oryxos/oryxos/tree/main/docs/RequirementDoc.md) — 五大核心能力 + 支撑模块
- [技术方案](https://github.com/oryxos/oryxos/tree/main/docs/TechnicalSolution.md) — 关键技术决策、模块架构、持久化
- [实施指引](https://github.com/oryxos/oryxos/tree/main/docs/AiProgrammingGuilde.md) — Spec-Kit 流程与五个 user story
