# Feature Specification: ReAct 循环引擎 + 编排层 + 上下文供给层

**Feature Branch**: `017-lesson17-react-loop`

**Created**: 2026-08-03

**Status**: Draft

**Input**: User description: "第17节需求：ReAct循环——Agent的推理-行动引擎，含AgentService编排层与ContextLoader上下文供给层"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 单轮问答快速响应 (Priority: P1)

用户通过 CLI 向 Agent 发一条不需要工具就能回答的问题（如"你好，介绍一下自己"），Agent 的推理引擎调一次大模型后直接返回最终回复，中间不执行任何工具。

**Why this priority**: 这是 ReAct 循环的最短路径——没有工具调用时一轮即收尾，验证了"模型判断不需要工具就直接回答"这个核心停止条件。如果这步不工作，后续所有多轮场景都跑不起来。

**Independent Test**: Mock ProviderService 返回无工具调用的响应，断言 ReActLoop.run 调 ProviderService 恰好一次、返回内容与模型响应一致。

**Acceptance Scenarios**:

1. **Given** 用户消息"介绍一下自己"和已配置的 Profile，**When** ReActLoop 开始执行，**Then** 调一次 ProviderService、拿到响应后一轮返回最终结果，不执行任何工具。
2. **Given** 用户消息为空字符串，**When** ReActLoop 执行，**Then** 仍正常组装 Prompt 并调用一次 LLM 返回结果。

---

### User Story 2 - 多轮工具调用完成任务 (Priority: P1)

用户问"今天北京天气怎么样，我该穿什么"，Agent 第一轮调 LLM 决定调用 http_get 工具查天气，执行工具拿到 JSON 结果后进入第二轮，模型看到天气数据后给出穿搭建议并返回最终响应。

**Why this priority**: 这是 ReAct 的完整闭环——Reason（想）→ Act（做）→ Observe（看）→ 再 Reason 直到完成。多轮工具调用是 Agent 区别于 chatbot 的关键能力。

**Independent Test**: Mock ProviderService 第一次返回"要调 http_get"的工具调用、第二次返回"建议穿羽绒服"的文本响应；Mock ToolExecutor 返回模拟天气 JSON。断言 ReActLoop.run 调了两次 ProviderService、一次 ToolExecutor，最终响应是第二次的文本。

**Acceptance Scenarios**:

1. **Given** 用户消息需要工具才能回答，**When** LLM 第一轮返回工具调用请求，**Then** ToolExecutor 执行工具、结果回填到 Session、进入第二轮继续推理。
2. **Given** 工具执行完成后 LLM 第二轮不再要求工具，**When** LLM 返回纯文本，**Then** ReActLoop 结束循环并返回最终响应。

---

### User Story 3 - 工具调用死循环安全兜底 (Priority: P1)

Agent 陷入"每轮都要调工具、永不收敛"的循环（例如模型出现幻觉反复调同一个工具），系统在达到最大轮数（默认 10）后强制停止，返回清晰的停止提示，不会无限消耗 token 和计算资源。

**Why this priority**: 这是课件点名的最重要坑之一（坑一：死循环）。没有上限兜底，一次对话可能把用户 token 用光，在生产环境这是致命问题。

**Independent Test**: Mock ProviderService 永远返回带工具调用的响应（永不收敛），设置 maxIterations=10。断言循环恰好转了 10 轮、返回消息包含"达到最大轮数"提示。

**Acceptance Scenarios**:

1. **Given** 模型每轮都要求调工具且 maxIterations=10，**When** 循环转了 10 轮，**Then** 强制停止、返回"达到最大轮数"提示。
2. **Given** Profile 设置了 maxIterations=5，**When** 模型持续要求调工具，**Then** 最多 5 轮就强制停止。

---

### User Story 4 - 每轮对话完整可审计 (Priority: P2)

一次会话完成后，Session 里记录了每一轮 LLM 说了什么、每次工具调用入参和返回结果。即使某次工具调用失败（如 http_get 超时），审计记录也包含 success=false 和错误原因，不会静默丢失。

**Why this priority**: 这是宪法原则五（审计表 Day One 写入）在本节的具体落地，也是课件坑三（不累积就无法审计）。审计完整性是 OryxOS 企业级定位的核心差异。

**Independent Test**: 跑完一次多轮对话后，断言 Session 的 messages 里包含了所有轮的 LLM 响应和工具调用结果；Mock 一次工具执行失败，断言 tool_invocations 表里有一条 success=false 且 error_message 非空的记录。

**Acceptance Scenarios**:

1. **Given** Agent 执行了 2 轮 LLM 调用和 1 次工具调用，**When** 循环结束，**Then** Session 对话历史包含 2 条 LLM 响应和 1 条工具结果。
2. **Given** 某次工具执行抛出异常，**When** ToolExecutor 处理该异常，**Then** tool_invocations 表写入 success=false 且 error_message 包含异常原因。

---

### User Story 5 - 统一编排入口支持三种触发源 (Priority: P2)

CLI、Web API、定时任务三种触发方式都通过同一个 `AgentService.process` 方法进入，编排层负责在调用前后设置和清理上下文（ProfileContext），保证工具执行时能取到当前 Agent 的配置。

**Why this priority**: 这是课件"三种触发源共用同一入口"的架构约束。ThreadLocal 泄漏是课件点名的最阴险 bug——在单请求测试里永远不报错，只在并发复用时串号。编排层的正确性直接影响后续所有节的可靠性。

**Independent Test**: 调 AgentService.process，验证处理期间 ProfileContext 可取到当前 Profile；Mock ReActLoop 抛异常，验证 finally 块清掉了 ProfileContext（断言 ProfileContext.current() 为 null）。

**Acceptance Scenarios**:

1. **Given** 一个会话和用户消息，**When** 调 AgentService.process，**Then** 处理期间 ProfileContext.current() 返回当前 Profile、finally 后 ProfileContext.current() 为 null。
2. **Given** ReActLoop 执行中抛异常，**When** 异常向上传播，**Then** ProfileContext 仍然被清掉（finally 块执行），Session 仍被尝试持久化。

---

### User Story 6 - 上下文实时更新无缓存 (Priority: P2)

运维人员修改了 AGENT.md 或某个 Bootstrap 文件后，下一次对话立即使用新内容，无需重启。同时，如果 Profile 引用了不存在的文件，系统给出明确的错误或警告而非静默跳过。

**Why this priority**: 课件指出静默跳过"人格悄悄丢了"是最难查的软故障。无缓存设计保证 Agent 行为的可调试性和即时生效能力。

**Independent Test**: 调用 ContextLoader 两次，中间修改被读取的文件内容，断言第二次读到的是新内容。配置一个不存在的 Bootstrap 文件路径，断言 ContextLoader 产生了 WARN 级别日志。

**Acceptance Scenarios**:

1. **Given** 某个 Bootstrap 文件内容为"A"，**When** ContextLoader 第一次读取后文件被修改为"B"，**Then** 下一次 build 立即读到"B"。
2. **Given** Profile 的 bootstrap 列表包含不存在的文件路径，**When** ContextLoader 加载时，**Then** 至少输出 WARN 级别日志，不阻断整体 prompt 组装。
3. **Given** Profile 的 skills 引用指向不存在的 Skill 目录，**When** ContextLoader 加载时，**Then** 报错（非 WARN）。

---

### Edge Cases

- Profile 的 maxIterations 设为 0 或负数时，循环如何处理？（应至少执行一轮后判断，或启动前校验）
- maxHistoryTurns 设为 0 时，对话历史部分是否完全为空？
- 用户消息为空时，系统 prompt 和历史是否仍正常组装？
- Profile 的 tools 列表为空时，PromptBuilder 的工具列表部分是否产生合理输出（不崩溃）？
- Session 对话历史已为空（首次对话）时，截断逻辑是否不抛异常？
- ToolExecutor 收到一个不存在的工具名称时，返回包含错误信息的 ToolResult 而非抛异常。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: ReActLoop 必须接收 Session、用户消息和 Profile，驱动循环——每轮组装 Prompt → 调 LLM → 判断是否有工具调用 → 无则返回最终响应 → 有则执行工具 → 结果回填 → 继续下轮，直到无工具调用或达到 maxIterations（默认 10）。

- **FR-002**: PromptBuilder 必须每轮按四部分顺序组装 prompt：(1) system prompt（AGENT.md 正文 + Bootstrap + Skill 元数据，末尾附当前日期时间）；(2) 长期记忆（由 Memory 模块提供，未就位时留空）；(3) 会话历史（只留最近 maxHistoryTurns 轮，默认 20，超出截断）；(4) 当前可用工具列表（Function Calling 格式）。

- **FR-003**: ToolExecutor 必须从 ToolRegistry 查找工具、执行、返回 ToolResult，每次执行无论成败都写入 tool_invocations 审计表。执行权只在这一个地方集中，不得有第二条工具执行路径。

- **FR-004**: AgentService 必须作为三种触发源的统一编排入口，process(Session, String) 内部：将 Profile 放入 ProfileContext（ThreadLocal）→ 调 ReActLoop.run → 持久化 Session → finally 清掉 ProfileContext。异常传播时不吞掉，但 ProfileContext 必须被清。

- **FR-005**: ContextLoader 必须按 Profile 的 bootstrap 和 skills 字段读取对应文件拼成文本，每次组装 prompt 都重新读文件不缓存。Bootstrap 文件缺失至少输出 WARN，Skill 引用缺失必须报错。

- **FR-006**: tool_invocations 表必须包含 id、session_id、tool_name、input_json、result_json、success、error_message、duration_ms、created_at 列。使用手工建表脚本，不依赖 Hibernate 自动 DDL。

- **FR-007**: ReActLoop 的每次 LLM 调用必须通过 ProviderService.chat(sessionId, Profile, Prompt) 进行，传入 sessionId 以保证 llm_calls 审计表能按 session 关联。

- **FR-008**: 每轮 LLM 响应和每次工具执行结果必须累积回 Session 对话历史，保证事后完整可审计。

### Key Entities

- **ToolInvocation**（审计实体）: 记录一次工具调用的完整信息——关联的 session、工具名称、输入参数、执行结果、成功/失败标识、错误信息、执行耗时、创建时间。
- **Session**（已有，第 18 节正式建模）: 本节使用其对话历史累积能力和 profileName 属性来驱动循环和审计关联。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 单轮无工具调用场景下，ReActLoop 恰好调一次 ProviderService 并在一轮内返回最终结果。
- **SC-002**: 多轮工具调用场景下，ReActLoop 能正确完成"调 LLM → 执行工具 → 回填 → 再调 LLM → 最终响应"的完整闭环。
- **SC-003**: 模型持续要求调工具时，循环恰好转 maxIterations 轮后强制停止，一轮不多。
- **SC-004**: 每次 LLM 调用和工具执行都在审计表里有对应记录（成功和失败都记），可通过 session_id 关联追溯。
- **SC-005**: 修改 Bootstrap 或 Skill 文件内容后，下一次 ContextLoader 调用立即读到最新内容，无缓存延迟。
- **SC-006**: AgentService.process 在正常结束和异常退出两种情况下都能清掉 ProfileContext，ThreadLocal 不泄漏。
- **SC-007**: 所有自动化测试（ReActLoopTest、PromptBuilderTest、ToolExecutorTest、AgentServiceTest、ContextLoaderTest）通过，`mvn test` 全绿。

## Assumptions

- 第 16 节的 ProviderService、Profile（含 maxIterations、maxHistoryTurns 等字段）、ProfileRegistry、LlmCall 实体和 Repository 已就位可用。
- ToolRegistry、OryxTool 接口、SandboxChecker 由后续节（第 20、24 节）交付，本节 ToolExecutor 预留调用位——工具体系尚未就位，测试时 mock ToolRegistry。
- Memory 模块（MemoryService）由后续节（第 22 节）交付，本节 PromptBuilder 在"长期记忆"部分预留空位或接口调用点。
- SessionManager 接口和 Session 持久化由后续节（第 18 节）交付，本节 AgentService 调用 sessionManager.save(session) 时依赖该接口存在——若不存在则本节先定义接口。
- 项目使用 JDK 21 + Spring Boot 3.x + Spring AI Alibaba，同步阻塞模型，配合 Virtual Thread。
- 凭证走环境变量，不在代码或配置文件中硬编码。
