---
title: 路线图
---

# 路线图

OryxOS 的交付分两段:**核心阶段先把 Agent OS 的运行时内核用 Java 做扎实**(能力上对齐业界开源 Agent OS 基础层);**真正的差异化治理层在核心内核之上、由扩展阶段和社区共建陆续补齐**。核心阶段是地基,企业级治理是终局。

## 核心阶段(4 周,每周约 3 小时)

| 周次 | 核心能力 | 周末可演示成果 |
| --- | --- | --- |
| 第一周 | 对接 LLM + ReAct 循环 | Agent 能多轮对话并调 HTTP Tool 完成简单任务 |
| 第二周 | Memory + Tool 体系 | Agent 能记住偏好、调文件读写、调外部 MCP 工具 |
| 第三周 | Web Service | 外部系统能通过 10 个 REST 端点调用 OryxOS |
| 第四周 | 多 Agent 演示 + 工程化收尾 | 多 Agent 并存、CLI 完整、Session 跨重启恢复、主页可访问 |

## 扩展阶段(社区接力)

- **渠道和模型层**:多 Channel(企业微信/飞书/钉钉/Slack/邮件)、Provider Fallback 与可靠性、Adaptive Routing
- **记忆和能力层**:Memory 自动抽取、语义检索(向量库)、情景记忆、Memory Wiki、完整 Skill 体系
- **工具和安全层**:MCP Server 暴露、Tool Policy、Tool LRU 加载、完整 Sandbox 隔离(Docker/K8s pod)
- **治理和运维层**:Web 仪表板、SSO 和多租户(SAML/OIDC)、完整审计与可追溯、集群化部署与高可用
- **企业集成层**:ERP/CRM/CMDB/监控系统/内网知识库 connector

## 社区共建

Skills Marketplace、SDK 多语言(Java/Python/TypeScript/Go)、可视化 Profile 编辑器、Native 文件生成、多区域部署、Kubernetes Operator、移动端管理台、Voice Channel、RISC-V 和边缘部署。
