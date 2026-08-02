---

description: "第一周任务清单：Provider 抽象 + ReAct 循环"
---

# Tasks: 第一周 — Provider 抽象 + ReAct 循环

**Input**: Design documents from `specs/001-week1-llm-react-loop/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 本周 spec 未显式要求测试任务；保留实现后的手工验收（quickstart.md）。核心类（ReActLoop/PromptBuilder/SandboxChecker）建议用轻量单元测试守护宪法边界，作为可选任务标注。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Maven 多模块，Java 源码在 `<module>/src/main/java/com/oryxos/...`
- 测试在 `<module>/src/test/java/com/oryxos/...`
- 涉及模块：`oryxos-core` / `oryxos-provider` / `oryxos-tool` / `oryxos-channel-cli` / `oryxos-cli` / `oryxos-boot`
- 保留骨架（本周不实现）：`oryxos-memory` / `oryxos-web` / `oryxos-storage`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 项目初始化与基础结构确认

- [ ] T001 确认 Maven 9 模块骨架可编译：`mvn -q compile` 通过（已有 pom.xml，验证模块依赖管理）
- [ ] T002 确认 `oryxos-boot` 主类可启动 Spring 上下文（已有 OryxOSApplication.java）
- [ ] T003 [P] 在 `oryxos-boot/src/main/resources/application.yaml` 配置 `ai.providers`（deepseek/kimi 占位，api-key 用 `${ENV_VAR}`）与 `http.allowed_domains`（含 `wttr.in`）
- [ ] T004 [P] 在 `oryxos-cli` 扩充 `OryxOsCli` 主入口，注册 `init` / `chat` 子命令（Picocli）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心数据结构与抽象，所有 user story 的前置

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T005 在 `oryxos-core` 实现 `Profile.java`（name/description/provider{name,model,temperature}/tools/settings{max_iterations=10,max_history_turns=20}，见 data-model.md）
- [ ] T006 [P] 在 `oryxos-core` 实现 `Message.java`（role/type/content/toolName/toolInput/toolResult/createdAt，四类型 USER_TEXT/ASSISTANT_TEXT/TOOL_CALL/TOOL_RESULT）
- [ ] T007 在 `oryxos-core` 实现 `Session.java` 与 `SessionManager.java`（内存版，sessionId=channel+user+profile 联合键，见 data-model.md）
- [ ] T008 在 `oryxos-core` 实现 `ProfileRegistry.java`（Profile 内存索引，register/get/validation）
- [ ] T009 扩充 `oryxos-core` 的 `OryxTool.java` / `ToolResult.java` 接口（getName/getDescription/getInputSchema/execute；success/content/errorMessage/retryable）
- [ ] T010 [P] 在 `oryxos-core` 定义审计接口 `audit/ToolInvocationRecorder.java` 与 `audit/LlmCallRecorder.java` + 内存实现（宪法原则五，见 data-model.md 审计记录器；第四周 storage 模块落地时换 JPA 实现，接口不变）
- [ ] T011 在 `oryxos-tool` 定义 `sandbox/Sandbox.java` 接口 + `SandboxAction`（type=FILE_READ/FILE_WRITE/SHELL_COMMAND/HTTP_REQUEST，接口中立）
- [ ] T012 在 `oryxos-tool` 实现 `sandbox/WhitelistSandbox.java`（仅 HTTP_REQUEST 域名白名单校验）+ `SandboxViolationException`
- [ ] T013 [P] 在 `oryxos-tool` 实现 `ToolRegistry.java`（register/get/all/forProfile）

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 对接任意主流 LLM (Priority: P1) 🎯 MVP

**Goal**: 配置一家 LLM 服务商即可对话，多 Provider 显式映射不串线

**Independent Test**: 配置 deepseek/kimi 密钥 → `oryxos chat` 发起一次消息 → 获得该服务商模型回复

### Implementation for User Story 1

- [ ] T014 [P] [US1] 在 `oryxos-provider` 实现 `ProviderConfig.java`：从 application.yaml 读 `ai.providers` 列表（name/model/apiKey/baseUrl）
- [ ] T015 [P] [US1] 在 `oryxos-boot` 配置 Spring AI `ChatModel` Beans（deepseek/kimi，基于 ProviderConfig，`@Qualifier` 区分）
- [ ] T016 [US1] 在 `oryxos-provider` 实现 `ProviderService.java`：组装显式 `Map<String, ChatModel>`（宪法原则三，禁用类型扫描），暴露 `chatModel(name)` / `call(profile, prompt)` / `availableProviders()`
- [ ] T017 [US1] 在 `oryxos-provider` 实现 `FunctionCallingAdapter.java`：`OryxTool` → Spring AI 工具 schema（只做格式转换，不用自动执行）
- [ ] T018 [US1] 在 `oryxos-cli` 实现 `ConfigLoader.java`：解析 `${ENV_VAR}` 环境变量，必填校验缺失即清晰报错（宪法原则：凭证不落明文）
- [ ] T019 [US1] 在 `oryxos-provider` 实现 `UnknownProviderException.java` + `ProviderService` 调用失败语义（技术方案 §3.3：不做 fallback，直接报错）；覆盖服务不可达/断网错误路径（可加 mock 测试：`ChatModel` 抛异常时 `ProviderService` 上抛、不静默吞掉）

**Checkpoint**: US-1 complete - 至少一家 Provider 能对话（可通过临时 main 或后续 chat 验证）

---

## Phase 4: User Story 2 - ReAct 循环（Agent 大脑）(Priority: P1)

**Goal**: Agent 自主调用工具完成"查天气穿衣"任务，对话日志按序累积

**Independent Test**: 验收 Demo 一——`oryxos chat` 输入"查一下北京天气并告诉我穿什么"，Agent 调 http_get → 看结果 → 给建议

### Implementation for User Story 2

- [ ] T020 [P] [US2] 在 `oryxos-tool` 实现 `HttpTools.java`（`http_get`：GET 请求 + 超时，execute 开头 `Sandbox.enforce(HTTP_REQUEST, url)`，见 contracts/oryx-tool.md）
- [ ] T021 [P] [US2] 在 `oryxos-tool` 实现 `HttpTools` 注册进 `ToolRegistry`（启动收集所有 `@Tool`/OryxTool）
- [ ] T022 [US2] 在 `oryxos-core` 实现 `react/PromptBuilder.java`：system prompt（Profile 描述 + 当前日期）+ 对话历史（max_history_turns 截断）+ Tool 列表（forProfile 转 schema），见 contracts/react-loop.md
- [ ] T023 [US2] 在 `oryxos-core` 实现 `react/ToolExecutor.java`：ToolRegistry.get → 执行 → 写 ToolInvocationRecorder（成功/失败都写）→ 按 retryable 返回错误
- [ ] T024 [US2] 在 `oryxos-core` 实现 `react/ReActLoop.java`：自实现主循环（宪法原则一），同步阻塞（原则七），`ChatResponse.getToolCalls()` 手动取 tool call、`ToolExecutor` 手动执行（原则二），max_iterations=10 收敛，见 contracts/react-loop.md
- [ ] T025 [US2] 在 `oryxos-core` 实现 `service/AgentService.java`：`process(session, userText)` 编排（取 Profile → ReActLoop.run → 返回响应），每次 LLM 调用写 LlmCallRecorder
- [ ] T026 [US2] 在 `oryxos-channel-cli` 实现 `CliChannel.java`：`oryxos chat` 读 stdin 写 stdout，维护 Session，`/quit` 退出，调 AgentService.process
- [ ] T027 [US2] 在 `oryxos-cli` 实现 `ChatCommand.java`（Picocli 子命令，启动 Spring 上下文取 AgentService）
- [ ] T028 [US2] 在 `oryxos-cli` 实现 `InitCommand.java`：`oryxos init` 建 `.oryxos/` 工作区 + 最小 `agents/default/AGENT.md` 示例（frontmatter 派生 Profile）

### Tests for User Story 2 (OPTIONAL - 建议，守护宪法边界) ⚠️

- [ ] T029 [P] [US2] 单元测试 `ReActLoop`：无 tool call 直接返回 / 有 tool call 走执行 / 超 max_iterations 收敛，在 oryxos-core/src/test/.../ReActLoopTest.java
- [ ] T030 [P] [US2] 单元测试 `WhitelistSandbox`：域名白名单放行/拒绝，在 oryxos-tool/src/test/.../WhitelistSandboxTest.java

**Checkpoint**: US-2 complete - Demo 一"查天气穿衣"跑通

> **FR-010 覆盖说明**：多个 Agent 互不干扰由 `ProfileRegistry`（按 name 独立索引）+ `SessionManager`（按 sessionId 独立会话）天然保证；本周 CLI 单会话场景，多 Agent 并发验证属第四周（多 Agent 并存演示）任务，不单列本周任务。

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 收尾与交叉关注点

- [ ] T031 [P] 全模块 `mvn clean package -DskipTests` 编译打包通过
- [ ] T032 按 quickstart.md 完整跑一遍 Demo 一验收（场景 A 无工具直接答 / 场景 B 查天气穿衣 / 场景 C 多轮上下文 / 场景 D 沙箱拒绝）
- [ ] T033 核对宪法边界：ReActLoop 无 Spring AI 自动 tool 执行、ProviderService 显式映射、审计内存实现记录完整（原则一/二/三/五）
- [ ] T034 git commit 标记第一周完成（便于回退）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **US-1 (Phase 3)**: Depends on Foundational (Profile/Session/审计接口)
- **US-2 (Phase 4)**: Depends on Foundational + US-1 (ReAct 循环每轮调 LLM)
- **Polish (Final Phase)**: Depends on all stories complete

### User Story Dependencies

- **US-1 (P1)**: Can start after Foundational - No dependencies on other stories
- **US-2 (P1)**: Depends on US-1 (ReActLoop 调 ProviderService)

### Within Each User Story

- Models before services
- Services before orchestration/endpoints
- Core implementation before integration

### Parallel Opportunities

- Phase 1: T003/T004 可并行
- Phase 2: T006/T010/T013 可并行（不同文件）
- Phase 3: T014/T015 可并行；T017/T018/T019 在 T016 后可并行
- Phase 4: T020/T021 可并行（Tool 不依赖 ReAct）；T022/T023/T024 顺序依赖；T025/T026/T027/T028 依赖前面
- Phase 5: T031 可先跑（编译验证）

---

## Parallel Example: User Story 1

```bash
# Launch provider config + beans together (T014 + T015):
Task: "Implement ProviderConfig in oryxos-provider/src/main/java/com/oryxos/provider/ProviderConfig.java"
Task: "Configure ChatModel beans in oryxos-boot"

# Launch adapter + configloader together (T017 + T018):
Task: "Implement FunctionCallingAdapter in oryxos-provider/.../FunctionCallingAdapter.java"
Task: "Implement ConfigLoader in oryxos-cli/.../ConfigLoader.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1 → 跑通一家 Provider 对话
4. **STOP and VALIDATE**: 用 `oryxos chat`（临时接线）验证单 Provider 对话
5. 再进入 Phase 4 US-2

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add US-1 → 单 Provider 对话可演示（MVP!）
3. Add US-2 → Demo 一"查天气穿衣"跑通
4. 每个 story 增加价值不破坏前序

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 每个 user story 独立可测：US-1 单 Provider 对话 / US-2 完整 Demo 一
- 宪法边界（原则一/二/三/五）由 T029/T030/T033 守护
- 审计写入为内存实现，第四周 storage 模块换 JPA（见 plan.md Complexity Tracking）
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
