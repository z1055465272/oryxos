# Feature Specification: 第一周 — Provider 抽象 + ReAct 循环

**Feature Branch**: `001-week1-llm-react-loop`

**Created**: 2026-08-02

**Status**: Draft

**Input**: 需求文档（docs/RequirementDoc.md 第 13 章 Demo 一）+ 技术方案（docs/TechnicalSolution.md 第 1.1、3.2、5、10 章）+ AI 编程指南（docs/AiProgrammingGuilde.md §4.1、§4.2）

## User Scenarios & Testing

> 本 spec 覆盖 OryxOS 第一周交付：**对接 LLM（核心能力一）+ ReAct 循环（核心能力二）**，验收 Demo 一"查天气穿衣"。涉及模块：`oryxos-core`、`oryxos-provider`、`oryxos-channel-cli`、`oryxos-cli`。
>
> 核心用户是**部署 OryxOS 的企业业务团队**。本周交付是运行时内核的地基：让任意 Agent 能对话、能自主调用工具完成一个真实任务。记忆、工具体系补全、Web Service 属于后续周次，本周不交付。

## Clarifications

### Session 2026-08-02

- Q: 第一周 Agent 定义形态 → A: 最小 AGENT.md（仅 frontmatter 派生 Profile，正文指令注入推迟到后续周次）
- Q: 天气数据源选择 → A: 推迟到 plan 阶段决策（候选：无密钥公开 API / 需密钥天气服务）

### User Story 1 - 对接任意主流 LLM（Priority: P1）

企业团队配置一家 LLM 服务商后，即可与任意 Agent 对话；后续可切换或并存多家服务商，业务 Agent 不感知具体调用的是哪家。

**Why this priority**: 运行时内核的地基。没有 LLM 调用，所有 Agent 能力都无法运行；同时为 User Story 2 提供"每轮思考"的支撑。

**Independent Test**: 配置一家服务商 → 发起一次对话 → 获得符合预期的回复，即可验证。

**Acceptance Scenarios**:

1. **Given** 系统配置了至少一家 LLM 服务商，**When** 用户通过命令行发起一条消息，**Then** 系统返回该服务商模型生成的自然语言回复
2. **Given** 系统配置了多家 LLM 服务商，**When** 不同 Agent 被指定使用不同服务商，**Then** 每个 Agent 的对话均由指定服务商处理，不发生串线
3. **Given** 配置缺少必要信息（如未提供服务商密钥），**When** 系统启动或用户发起对话，**Then** 系统给出清晰、可定位的错误提示，而非静默失败
4. **Given** 服务商密钥来自环境变量，**When** 查看任何配置文件，**Then** 配置文件中不出现明文密钥

---

### User Story 2 - ReAct 循环（Agent 大脑）（Priority: P1）

用户发起一个需要"先查数据再作答"的请求时，Agent 能自主规划：调用工具获取信息 → 观察结果 → 决定下一步，直到给出最终答案；当不需要工具时直接回答。

**Why this priority**: Agent 区别于普通聊天机器人的核心机制。依赖 User Story 1（每轮调用 LLM 思考），并为后续周次的记忆注入与工具体系提供承载循环。

**Independent Test**: 验收 Demo 一——用户请求"查一下北京天气并告诉我穿什么"，Agent 完整走完"调工具 → 看结果 → 给建议"，对话日志正确累积。

**Acceptance Scenarios**:

1. **Given** 用户发起无需工具的消息（如"你好"），**When** Agent 处理该消息，**Then** 直接返回自然语言回复，不触发任何工具调用
2. **Given** 用户发起需要外部数据的消息（如"查一下北京天气并告诉我穿什么"），**When** Agent 处理该消息，**Then** 自动调用天气数据工具、读取结果、据此给出穿衣建议
3. **Given** 一次对话过程中 Agent 调用了工具，**When** 对话结束后查看会话记录，**Then** 完整对话（用户消息、工具调用、工具结果、最终回答）按顺序正确累积
4. **Given** 一次任务需要多轮工具调用，**When** 调用次数达到单任务上限（默认 10 轮），**Then** 循环停止并返回基于已有信息的回答，不无限执行
5. **Given** 用户通过命令行多轮对话，**When** 连续发送多条消息，**Then** 后续消息能参考此前的对话上下文

---

### Edge Cases

- 配置的 LLM 服务商密钥缺失、非法或服务不可达，对话与启动如何报错
- 模型连续多轮不产生最终回答（陷入循环）时如何收敛（依赖单任务轮数上限）
- 天气数据源调用超时、无网络或返回异常数据时，Agent 如何降级回答
- 多个 Agent 同时被调用时的并发隔离（互不串扰）
- 用户中断命令行对话（Ctrl+C）时已累积的消息如何处理

## Requirements

### Functional Requirements

- **FR-001**: 系统 MUST 允许配置至少一家主流 LLM 服务商，并支持多家服务商并存
- **FR-002**: 系统 MUST 允许每个业务 Agent 指定其使用的服务商，且不同 Agent 的路由互不串线
- **FR-003**: 系统 MUST 支持用户通过命令行发起多轮对话，后续轮次能参考先前上下文
- **FR-004**: 系统 MUST 自动判断一次请求是否需要调用工具，需要时调用、观察结果、决定下一步，直至产出最终回答（机制层，ReAct 循环）
- **FR-005**: 系统 MUST 对单次任务设置工具调用轮数上限（默认 10），达到上限后基于已有信息收敛
- **FR-006**: 系统 MUST 完整累积每次对话的消息序列（用户消息、工具调用、工具结果、最终回答）
- **FR-007**: 系统 MUST 支持调用外部数据源工具（本周为天气数据源，具体工具 `http_get`，见 FR-008 边界）以完成"查数据再作答"类任务
- **FR-008**: 系统 MUST 对至少一种外部数据访问设置访问边界（本周为域名白名单），超出边界拒绝执行
- **FR-009**: 系统 MUST 保证配置的敏感凭证（服务商密钥）不出现于明文配置中
- **FR-010**: 系统 MUST 支持多个 Agent 同时被调用时互不干扰
- **FR-011**: 系统 MUST 支持从 Agent 定义（AGENT.md 形态）的 frontmatter 派生运行 Profile（服务商、模型、温度、工具列表）；本周不加载正文指令

### Key Entities

- **Agent（业务代理）**: 一个可被调用的业务能力单元，具有身份、任务描述与绑定的工具；本周以**最小 AGENT.md** 定义——仅从其 frontmatter 派生运行 Profile（服务商、模型、温度、工具列表），正文指令注入推迟到后续周次
- **Session（会话）**: 一次对话的生命周期容器，按渠道与用户组织，承载消息序列；本周为内存版
- **Message（消息）**: 会话内的一条记录，包括用户输入、工具调用、工具结果、Agent 回答等类型
- **Provider（服务商）**: 一家可被调用的 LLM 服务商，Agent 通过它发起模型调用；本周支持至少一家（DeepSeek 或 Kimi）
- **Tool（工具）**: 一个可被 Agent 调用的能力单元，含输入输出契约；本周交付天气数据访问工具（HTTP GET）
- **Profile（运行配置）**: Agent 的运行配置，含服务商、模型、温度等参数，由 AGENT.md frontmatter 派生（本周不引入正文指令加载）

## Success Criteria

### Measurable Outcomes

- **SC-001**: 用户配置一家服务商后，在 5 分钟内即可完成首次对话（零代码）
- **SC-002**: 配置多家服务商后，随机抽测 10 次跨 Agent 调用，串线（路由到错误服务商）次数为 0
- **SC-003**: 演示"查天气穿衣"场景时，Agent 在 2 分钟内完成"调用工具→读取结果→给出建议"全流程
- **SC-004**: 演示"查天气穿衣"场景时，完整对话日志按序累积，工具调用轮数未超出上限
- **SC-005**: 不需要工具的消息直接回答，不触发任何工具调用（通过验收场景 1 验证）
- **SC-006**: 第一个验收 Demo（`oryxos chat` 查天气穿衣）跑通，即视为本周交付完成

## Assumptions

- 本周至少跑通一家服务商（DeepSeek 或 Kimi），由业务方提供有效访问密钥
- 天气数据源待 plan 阶段决策：无密钥公开 API（域名白名单即可）或需密钥天气服务（引入第二套密钥管理）；FR-008 域名白名单按所选数据源配置
- 单任务工具调用轮数上限默认 10，可配置；本周采用默认值
- Session 本周为内存版，持久化与跨重启恢复属于后续周次（US-5）
- 目标环境为内网使用，本周不实现访问认证与细粒度权限控制
- 本周交付"可运行的最小完整内核"的一部分——对接 LLM + ReAct 循环，其余能力后续周次补齐
- 实现遵循项目宪法：自实现 ReAct Loop、Spring AI 仅用于协议转换与 schema 生成、Provider 显式映射、同步执行模型（Java 21 Virtual Thread）
