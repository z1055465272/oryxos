# Tasks: ReAct 循环引擎 + 编排层 + 上下文供给层（第 17 节）

**Input**: Design documents from `specs/002-react-loop/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/README.md

**Tests**: 课件"验收 harness"明确要求五个单测类（harness 先行，测试先于/伴随实现）；另加 storage 单测验证 tool_invocations 表。关键回归测试方法名译成英文，课件原文进 `@DisplayName`。

**Organization**: 按用户故事分组，每阶段独立可测。

## 格式: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 归属用户故事（US1-US6）
- 描述含确切文件路径

---

## Phase 1: Setup（共享基建）

**Purpose**: 确认无新模块、依赖方向与既有依赖可用

- [ ] T001 确认复用既有 9 模块、不新建模块；记录依赖方向约束：oryxos-core 不得 import oryxos-provider/oryxos-storage 具体类（见 plan.md Structure Decision）
- [ ] T002 确认 Spring AI BOM 1.0.9 已锁定、`ChatResponse`/`Generation`/`AssistantMessage`/`AssistantMessage.ToolCall`/`ToolResponseMessage`/`ToolResponse` API 已在本地依赖核实（H3 已完成，记录结论到 research.md 决策 1）

---

## Phase 2: Foundational（阻塞性前置——值对象/契约/存储审计/跨模块适配）

**Purpose**: 所有用户故事的公共地基，**未完成前任何 US 不能开工**

### 核心值对象与契约（oryxos-core）

- [ ] T003 创建 `oryxos-core/src/main/java/com/oryxos/core/ToolCall.java`：record `(String id, String name, String arguments)`
- [ ] T004 创建 `oryxos-core/src/main/java/com/oryxos/core/Response.java`：record `(String text, List<ToolCall> toolCalls)`，含 `hasToolCalls()`
- [ ] T005 创建 `oryxos-core/src/main/java/com/oryxos/core/Session.java`：`sessionId/profileName/channel/userId/List<Message>/status`；sealed `Message`（`UserMessage`/`AssistantMessage(content, toolCalls)`/`ToolResultMessage(toolCallId, toolName, content)`）；方法 `appendUserMessage/appendAssistant/appendToolResult/messages()/markArchived()`（appendToolResult 把 ToolCall+ToolResult 落成 ToolResultMessage）
- [ ] T006 扩展 `oryxos-core/src/main/java/com/oryxos/core/Prompt.java`：新增 `systemMessage` + `messages`（`List<Session.Message>`），保留 `userMessage` + `availableTools` 与旧构造器 `Prompt(String)`、`Prompt(String, List<OryxTool>)`（向后兼容）
- [ ] T007 迁移 `ProviderService` 接口到 `oryxos-core/src/main/java/com/oryxos/core/ProviderService.java`：只留 `Response chat(String sessionId, Profile profile, Prompt prompt)`；**删除** `oryxos-provider/.../ProviderService.java`；`resolve(String)` 移到 DefaultProviderService 具体类
- [ ] T008 创建契约接口（oryxos-core）：`ToolRegistry.java`（`Optional<OryxTool> get(String)`/`Collection<OryxTool> listAll()`）、`SessionManager.java`（`void save(Session)`）、`ToolInvocationStore.java`（`void save(ToolInvocationRecord)`）、`ToolInvocationRecord.java`（record 含 sessionId/toolName/inputJson/resultJson/success/errorMessage/durationMs/createdAt）

### Provider 适配（oryxos-provider）

- [ ] T009 适配 `oryxos-provider/src/main/java/com/oryxos/provider/DefaultProviderService.java`：实现 `com.oryxos.core.ProviderService`；`chat` 把 Prompt 的 `systemMessage`+`messages` 映射成 Spring AI 消息序列（`SystemMessage`/`UserMessage`/`AssistantMessage(toolCalls)`/`ToolResponseMessage(id,name,responseData)`），`messages` 为空回退 `UserMessage(userMessage)`；调用后把 `ChatResponse` 转成 `Response`（text + toolCalls）；保留显式映射路由、llm_calls 审计（成功/失败）、`internalToolExecutionEnabled(false)`
- [ ] T010 适配 `oryxos-provider/src/test/java/com/oryxos/provider/ProviderServiceTest.java`：mock ChatModel 返回含真实 `AssistantMessage` 的 `ChatResponse`（getResult/getOutput 非空），断言路由/审计/自动执行关闭不变；**新增**多轮消息映射测试（systemMessage+messages 被正确翻译成 Spring AI 消息序列）
- [ ] T011 适配 `oryxos-provider/src/test/java/com/oryxos/provider/ProviderSmokeIT.java`：`chat` 返回 `Response`，断言非空文本

### 存储审计（oryxos-storage）

- [ ] T012 创建 `oryxos-storage/src/main/java/com/oryxos/storage/ToolInvocation.java`：JPA 实体映射 `tool_invocations` 表（id/session_id/tool_name/input_json/result_json/success/error_message/duration_ms/created_at，风格对齐 LlmCall）
- [ ] T013 创建 `oryxos-storage/src/main/java/com/oryxos/storage/ToolInvocationRepository.java`：`extends JpaRepository<ToolInvocation, Long>`
- [ ] T014 创建 `oryxos-storage/src/main/java/com/oryxos/storage/JpaToolInvocationStore.java`：实现 `com.oryxos.core.ToolInvocationStore`，把 `ToolInvocationRecord` 映射为实体后 `repository.save`（依赖倒置适配）
- [ ] T015 修改 `oryxos-storage/src/test/resources/schema.sql`：补 `tool_invocations` 建表（与 V1 脚本同 DDL）
- [ ] T016 创建 `oryxos-storage/src/test/java/com/oryxos/storage/ToolInvocationRepositoryTest.java`：`@DataJpaTest` 手工建表脚本，验证成功记录存读、失败记录 success=false+error_message、表含 success/error_message 两列（对齐 LlmCallRepositoryTest）

**Checkpoint**: 地基就绪——值对象/契约/存储审计/provider 适配完成，各 US 可开工

---

## Phase 3: User Story 1+3 - ReAct 循环引擎（单轮收尾 + 死循环兜底）(Priority: P1) 🎯 MVP

**Goal**: ReActLoop 能一轮收尾、多轮工具闭环、转满最大轮数强制停；PromptBuilder 四部分组装。
**独立测试**: `ReActLoopTest`/`PromptBuilderTest` 全绿。

### Tests（harness 先行）⚠️

- [ ] T017 [P] [US1] 创建 `oryxos-core/src/test/java/com/oryxos/core/ReActLoopTest.java`：无工具调用一轮收尾（chat 恰一次、返回模型文本）；多轮工具闭环（第一轮带工具调用、第二轮纯文本，chat 两次、toolExecutor 一次）
- [ ] T018 [P] [US3] `ReActLoopTest` 关键回归：`stopsAfterMaxIterations_whenModelKeepsRequestingTools`（`@DisplayName("模型一直要调工具_转满最大轮数强制停")`）——mock ProviderService 每轮都返回工具调用、maxIterations=10，断言 chat 恰 10 次、回复含"达到最大轮数"
- [ ] T019 [P] [US1] `ReActLoopTest` 消息累积：跑完后 session.messages() 含用户消息 + 各轮 LLM 响应 + 工具结果
- [ ] T020 [P] [US1] 创建 `oryxos-core/src/test/java/com/oryxos/core/PromptBuilderTest.java`：四部分组装（systemMessage 含 identity.prompt + bootstrap 内容 + skill 内容 + 当前日期；messages 为历史；availableTools 为 profile 工具解析结果）；空工具/空历史不崩
- [ ] T021 [P] [US3] `PromptBuilderTest` 坑二回归：历史超 `maxHistoryTurns`（默认 20）被截断，最后一条是当前用户消息
- [ ] T022 [P] [US1] `PromptBuilderTest` system prompt 末尾含当前日期时间

### Implementation

- [ ] T023 [US1] 创建 `oryxos-core/src/main/java/com/oryxos/core/PromptBuilder.java`：构造 `(ContextLoader, ToolRegistry)`；`build(Session, Profile)` 组装 systemMessage（identity.prompt + ContextLoader 输出 + 当前日期时间）、truncate 历史到 maxHistoryTurns（默认 20 条）、经 ToolRegistry 解析 profile.tools 为 availableTools、userMessage 取当前用户消息；记忆第二部分留空占位并注明 22 节接入
- [ ] T024 [US1] 创建 `oryxos-core/src/main/java/com/oryxos/core/ReActLoop.java`：构造 `(ProviderService, PromptBuilder, ToolExecutor)`；`run(Session, String, Profile)` 主循环——appendUserMessage → for i<maxIterations(默认 10)：build Prompt → `providerService.chat(session.id(), profile, prompt)` → appendAssistant → `hasToolCalls()` 无则返回文本、有则逐个 `toolExecutor.execute(session.id(), call)` 并 appendToolResult → 转满返回"达到最大轮数，已停止"。主循环手写、不用 Spring AI Agent 抽象，异常不吞

**Checkpoint**: 单轮/多轮/兜底三条路径在 harness 下全绿

---

## Phase 4: User Story 2+4 - ToolExecutor 工具执行与审计 (Priority: P1/P2)

**Goal**: 模型要求的工具被集中执行（执行权只此一处），无论成败写 tool_invocations 审计。
**独立测试**: `ToolExecutorTest` 全绿。

### Tests（harness 先行）⚠️

- [ ] T025 [P] [US2] 创建 `oryxos-core/src/test/java/com/oryxos/core/ToolExecutorTest.java`：工具成功→`ToolInvocationStore.save` 收到 `success=true` 记录、返回结果
- [ ] T026 [P] [US4] `ToolExecutorTest`：工具返回 `ToolResult.fail`→审计 `success=false` + errorMessage；**工具抛 RuntimeException→审计 success=false 带原因后上抛（不吞）**
- [ ] T027 [P] [US2] `ToolExecutorTest`：未知工具名→审计 success=false 且返回失败结果（不抛）

### Implementation

- [ ] T028 [US2] 创建 `oryxos-core/src/main/java/com/oryxos/core/ToolExecutor.java`：构造 `(ToolRegistry, ToolInvocationStore)`；`execute(String sessionId, ToolCall)`——`enforceSandbox` 调用位（留空注明 24 节接线，见 H4 不变量①）→ 查 registry（未知→审计失败+返回 fail）→ 计时执行 `tool.execute(arguments)` → 按 ToolResult 成败写 `ToolInvocationRecord`（成功 resultJson、失败 errorMessage）→ 工具抛异常则审计失败后上抛。执行权只此一处

**Checkpoint**: 执行与审计路径在 harness 下全绿

---

## Phase 5: User Story 5 - 统一编排（AgentService + ProfileContext）(Priority: P2)

**Goal**: 三种触发源共用同一入口；线程上下文设置/清理正确（异常路径也清理）。
**独立测试**: `AgentServiceTest` 全绿。

### Tests（harness 先行）⚠️

- [ ] T029 [P] [US5] 创建 `oryxos-core/src/test/java/com/oryxos/core/AgentServiceTest.java`：处理期间 `ProfileContext.current()` 可取到当前 Profile（doAnswer 内断言）、结束后为 null
- [ ] T030 [P] [US5] `AgentServiceTest` 关键回归：`clearsProfileContext_whenProcessingThrows`（`@DisplayName("处理中抛异常_ProfileContext也必须被清掉")`）——mock ReActLoop.run 抛异常，`assertThrows` 后 `assertNull(ProfileContext.current())`
- [ ] T031 [P] [US5] `AgentServiceTest`：成功后 `sessionManager.save(session)` 被调用（会话持久化）

### Implementation

- [ ] T032 [US5] 创建 `oryxos-core/src/main/java/com/oryxos/core/ProfileContext.java`：`ThreadLocal<Profile>`，静态 `set/current/clear`
- [ ] T033 [US5] 创建 `oryxos-core/src/main/java/com/oryxos/core/AgentService.java`：构造 `(ProfileRegistry, ReActLoop, SessionManager)`；`process(Session, String)`——`profileRegistry.get(session.profileName())` → `ProfileContext.set(profile)` → try { `reActLoop.run(...)`; `sessionManager.save(session)`; return reply } finally { `ProfileContext.clear()` }

**Checkpoint**: 编排与线程上下文在 harness 下全绿

---

## Phase 6: User Story 6 - 上下文供给（ContextLoader）(Priority: P2)

**Goal**: 按 Profile 读 Bootstrap 与 SKILL.md 正文，无缓存、缺失行为正确。
**独立测试**: `ContextLoaderTest` 全绿（临时目录，不碰真实 .oryxos）。

### Tests（harness 先行）⚠️

- [ ] T034 [P] [US6] 创建 `oryxos-core/src/test/java/com/oryxos/core/ContextLoaderTest.java`：无缓存回归——临时目录 bootstrap 文件内容 A→load 后改 B→再次 load 读到 B
- [ ] T035 [P] [US6] `ContextLoaderTest`：Bootstrap 缺失至少 WARN 且不阻断（loadSystemPrompt 不抛）；Skill 引用缺失**报错**（`.oryxos/skills/<missing>/SKILL.md` 不存在 → assertThrows）
- [ ] T036 [P] [US6] `ContextLoaderTest`：存在的 SKILL.md 正文被预载拼入（Q1=B 裁定）

### Implementation

- [ ] T037 [US6] 创建 `oryxos-core/src/main/java/com/oryxos/core/ContextLoader.java`：构造 `(Path workspaceDir)`；`loadSystemPrompt(Profile)`——遍历 `profile.bootstrap()` 读 `<workspace>/<name>`（缺失 log.warn + 跳过），遍历 `profile.skills()` 读 `<workspace>/skills/<name>/SKILL.md`（缺失抛异常）并预载正文，拼接返回；每次调用重新读文件、无缓存

**Checkpoint**: 上下文供给在 harness 下全绿

---

## Phase 7: Polish & Cross-Cutting

**Purpose**: 跨节一致性、依赖方向、全量门禁

- [ ] T038 模块依赖方向检查：`grep -rn "com.oryxos.provider\|com.oryxos.storage" oryxos-core/src/main/java --include="*.java"` 无输出（core 不反向依赖）
- [ ] T039 前序节回归：`mvn -pl oryxos-provider,oryxos-storage test` 全绿（第 16 节契约改动后回归）
- [ ] T040 全量门禁：`mvn clean verify` BUILD SUCCESS（单测全绿 + P3C/SpotBugs/FindSecBugs/PMD 无阻断）
- [ ] T041 宪法 v1.2.0 / CLAUDE.md 原则四 / TechnicalSolution §8.3 同步已在 clarify 阶段完成，复核无残留矛盾表述（grep "不预载\|正文不预载" 排除 29 节语境）
- [ ] T042 更新 `specs/002-react-loop/quickstart.md` 验证结果（如实现过程发现偏差）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞所有 US**（值对象/契约/provider 适配/存储审计先行）
- **US1+3 (Phase 3)**: 依赖 Foundational（ReActLoop 需 ProviderService/Prompt/Session/ToolExecutor 契约；测试用 mock ToolExecutor）
- **US2+4 (Phase 4)**: 依赖 Foundational（ToolExecutor 需 ToolRegistry/ToolInvocationStore）
- **US5 (Phase 5)**: 依赖 Foundational + ReActLoop 实现（AgentService 需真实 ReActLoop 可 mock，但编排语义依赖循环）
- **US6 (Phase 6)**: 依赖 Foundational（可与其他 US 并行）
- **Polish (Phase 7)**: 依赖全部 US

### 并行机会

- Phase 2 中 T003-T008（值对象/契约）、T009-T011（provider 适配）、T012-T016（存储审计）为三个并行轨道
- Phase 3 中 ReActLoopTest 与 PromptBuilderTest 可并行；Phase 4/5/6 的测试类相互独立可并行
- 所有测试任务（T017-T036）mark [P] 的可并行编写

### 实现策略

1. 先 Phase 2 地基（值对象→契约→provider 适配→存储审计）
2. Phase 3 引擎（US1/US3 是 MVP）
3. Phase 4 执行器（US2/US4）
4. Phase 5 编排（US5）、Phase 6 供给（US6，可与 Phase 5 并行）
5. Phase 7 全量门禁收尾

---

## 关键回归测试清单（课件点名，方法名英文 + @DisplayName 保留课件原文）

| 测试 | 文件 | 断言 |
|------|------|------|
| `stopsAfterMaxIterations_whenModelKeepsRequestingTools` | ReActLoopTest | chat 恰 10 次，回复含"达到最大轮数" |
| `clearsProfileContext_whenProcessingThrows` | AgentServiceTest | 异常后 `ProfileContext.current()` 为 null |

## 跨节改造清单（用户裁定接受，需在验收报告标注）

1. `ProviderService` 接口迁入 core、`chat` 返回 `Response`（resolve 移 DefaultProviderService）→ 改第 16 节接口签名
2. `Prompt` 向后兼容扩展（systemMessage + messages）→ 改第 16 节值对象
3. `DefaultProviderService` 消息序列适配（单条→完整序列）→ 改第 16 节实现
4. 宪法原则四 v1.2.0（Skill 正文预载）→ 改治理文档
