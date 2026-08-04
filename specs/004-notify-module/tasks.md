# Tasks: Notify 模块

**Input**: Design documents from `specs/004-notify-module/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Included per specification — WebhookNotifyAdapterTest (第一批), NotifyToolsTest (第二批), NotifyChannelConfigTest, SandboxPlaceholderTest. Harness 先行原则。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `oryxos-tool/src/main/java/com/oryxos/tool/` — Notify 实现、Sandbox 占位
- `oryxos-tool/src/test/java/com/oryxos/tool/` — Notify 测试
- `oryxos-core/src/main/java/com/oryxos/core/` — Profile、ProfileLoader、ProfileContext、NotifyChannelConfig
- `oryxos-core/src/test/java/com/oryxos/core/` — ProfileLoaderTest（改造）

---

## Phase 1: Setup (Sandbox 占位 + 目录结构)

**Purpose**: 创建 Sandbox 占位类型，为 23/24 节预留接口；创建 notify/builtin/sandbox 包结构

- [ ] T001 [P] Create Sandbox interface in `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/Sandbox.java`
- [ ] T002 [P] Create SandboxAction record in `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/SandboxAction.java`
- [ ] T003 [P] Create ActionType enum in `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/ActionType.java` (values: FILE_READ, FILE_WRITE, SHELL, HTTP_REQUEST)
- [ ] T004 [P] Create NoOpSandbox @Component (implements Sandbox, enforce() no-op) in `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/NoOpSandbox.java`
- [ ] T005 [P] Create SandboxPlaceholderTest (verify NoOpSandbox.enforce() does not throw) in `oryxos-tool/src/test/java/com/oryxos/tool/sandbox/SandboxPlaceholderTest.java`

**Checkpoint**: Sandbox 占位类型就位，目录结构创建完成

---

## Phase 2: Foundational (NotifyChannelConfig + Profile 改造)

**Purpose**: Profile 的 notify_channels 字段从 List<String> 升级为 List<NotifyChannelConfig>——这是 US1/US2 的阻塞前置

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T006 [P] Create NotifyChannelConfig record (type, url, 空字符串校验) in `oryxos-core/src/main/java/com/oryxos/core/NotifyChannelConfig.java`
- [ ] T007 [P] Create NotifyChannelConfigTest (解析环境变量占位符 ${VAR}、空 type/url 报错) in `oryxos-core/src/test/java/com/oryxos/core/NotifyChannelConfigTest.java`
- [ ] T008 Modify Profile.java: change `List<String> notifyChannels` to `List<NotifyChannelConfig> notifyChannels` in `oryxos-core/src/main/java/com/oryxos/core/Profile.java`
- [ ] T009 Modify ProfileLoader.java: replace `getStringList(raw, "notify_channels")` with `parseNotifyChannels(raw)` that parses structured `[{type, url}]` list, resolve env var placeholders in `oryxos-core/src/main/java/com/oryxos/core/ProfileLoader.java`
- [ ] T010 Modify ProfileLoaderTest.java: update `notify_channels` YAML test data from `["webhook-ops"]` to structured `[{type: "webhook", url: "http://..."}]` in `oryxos-core/src/test/java/com/oryxos/core/ProfileLoaderTest.java`; keep all existing tests passing
- [ ] T011 Modify ProfileContext.java: add @Component, add `resolveNotifyChannel(String channel)` instance method (delegates to static ThreadLocal, returns NotifyChannelConfig matching by type; null channel → first entry; empty list → throw) in `oryxos-core/src/main/java/com/oryxos/core/ProfileContext.java`
- [ ] T012 [P] Add ProfileContext.resolveNotifyChannel test cases to `oryxos-core/src/test/java/com/oryxos/core/ProfileLoaderTest.java` (or separate `ProfileContextTest.java`): verify channel resolution by type, default-to-first, empty list throws, null channel returns first

**Checkpoint**: Foundation ready — NotifyChannelConfig and Profile 改造完成，user story implementation can now begin

---

## Phase 3: User Story 1 - Webhook 适配器发送通知 (Priority: P1) 🎯 MVP

**Goal**: NotifyChannelAdapter 接口 + NotifyTarget + WebhookNotifyAdapter 实现，可独立用 MockWebServer 验证

**Independent Test**: `./mvnw test -pl oryxos-tool -Dtest=WebhookNotifyAdapterTest`

### Tests for User Story 1 (harness 先行，写先于实现)

- [ ] T013 [P] [US1] Create WebhookNotifyAdapterTest skeleton with MockWebServer setup in `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`
- [ ] T014 [P] [US1] Write test: `sendPostsJsonWithContentField` — MockWebServer 接收 POST, body 含 `{"content":"..."}` in `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`
- [ ] T015 [P] [US1] Write test: `targetUrlFromNotifyTargetConfigNotHardcoded` — 两个不同 URL 的 NotifyTarget 各自命中对应 webhook in `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`
- [ ] T016 [P] [US1] Write test: `serverError5xxPropagatesException` — webhook 返回 5xx 时异常向上传播，不静默吞 in `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`

### Implementation for User Story 1

- [ ] T017 [P] [US1] Create NotifyTarget record (channelType, Map<String,String> config, defensive copy) in `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyTarget.java`
- [ ] T018 [P] [US1] Create NotifyChannelAdapter interface (void send(NotifyTarget target, String content)) in `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyChannelAdapter.java`
- [ ] T019 [US1] Create WebhookNotifyAdapter @Component (RestClient POST, Content-Type JSON, body {content: ...}, url from target.config().get("url")) in `oryxos-tool/src/main/java/com/oryxos/tool/notify/WebhookNotifyAdapter.java`
- [ ] T020 [US1] Run `mvn test -pl oryxos-tool -Dtest=WebhookNotifyAdapterTest` — all 3 tests green

**Checkpoint**: US1 functional — WebhookNotifyAdapter passes MockWebServer tests independently

---

## Phase 4: User Story 2 - Agent 通过 Notify 工具推送 (Priority: P2)

**Goal**: NotifyTools @Tool notify(content, channel) 串联渠道解析 → 安全校验 → 发送；顺序断言由 InOrder 钉死

**Independent Test**: `./mvnw test -pl oryxos-tool -Dtest=NotifyToolsTest`

### Tests for User Story 2 (harness 先行)

- [ ] T021 [P] [US2] Create NotifyToolsTest skeleton with @Mock Sandbox, @Mock NotifyChannelAdapter, ProfileContext setup in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/NotifyToolsTest.java`
- [ ] T022 [P] [US2] Write test: `notifyChannelsNotConfiguredReturnsError` — Profile.notifyChannels 为空列表时返回 ToolResult.fail, 不调 adapter.send in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/NotifyToolsTest.java`
- [ ] T023 [P] [US2] Write test: `channelParameterDefaultToFirstChannel` — channel 参数 null/blank 时取第一个 NotifyChannelConfig in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/NotifyToolsTest.java`
- [ ] T024 [P] [US2] Write test: `enforceCalledBeforeSendInOrder` — InOrder 断言 sandbox.enforce 先于 adapter.send 被调用 (用 argThat 校验 ActionType.HTTP_REQUEST) in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/NotifyToolsTest.java`

### Implementation for User Story 2

- [ ] T025 [US2] Create NotifyTools @Component with @Tool notify(String content, String channel) method — 三步: resolveNotifyChannel → sandbox.enforce → adapter.send, 返回 ToolResult in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/NotifyTools.java`
- [ ] T026 [US2] Run `mvn test -pl oryxos-tool -Dtest=NotifyToolsTest` — all 4 tests green

**Checkpoint**: US2 functional — NotifyTools passes mock tests independently

---

## Phase 5: User Story 3 - Agent 配置中声明通知渠道 (Priority: P3)

**Goal**: 验证 NotifyChannelConfig 解析和 ProfileLoader 集成完整（大部分实现已在 Foundational Phase 2 完成）

**Independent Test**: `./mvnw test -pl oryxos-core -Dtest=ProfileLoaderTest,NotifyChannelConfigTest`

### Tests for User Story 3

*(Already created in T007 and T012 during Foundational phase; verify pass)*

- [ ] T027 [US3] Verify NotifyChannelConfigTest passes (env var placeholder resolution, empty type/url validation) in `oryxos-core/src/test/java/com/oryxos/core/NotifyChannelConfigTest.java`
- [ ] T028 [US3] Verify ProfileLoaderTest passes with structured notify_channels YAML data in `oryxos-core/src/test/java/com/oryxos/core/ProfileLoaderTest.java`
- [ ] T029 [US3] Verify ProfileContext resolveNotifyChannel tests pass in `oryxos-core/src/test/java/com/oryxos/core/ProfileLoaderTest.java`

**Checkpoint**: US3 functional — Profile 通知渠道配置解析验证通过

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量门禁通过、前序节回归、依赖方向验证

- [ ] T030 Run `mvn clean verify` — full build with P3C/SpotBugs/FindSecBugs/PMD, all modules green
- [ ] T031 Verify all pre-existing tests from lessons 16/17/18 still pass (regression check in oryxos-core, oryxos-provider, oryxos-storage, oryxos-cli, oryxos-channel-cli)
- [ ] T032 Run `quickstart.md` validation scenarios: cross-module regression commands

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup (ProfileContext needs Sandbox types for compilation)
- **US1 (Phase 3)**: Depends on Foundational (needs NotifyChannelConfig to exist for compilation, though US1 only uses NotifyTarget internally — independent of Profile changes)
- **US2 (Phase 4)**: Depends on US1 (needs NotifyChannelAdapter) + Foundational (needs ProfileContext.resolveNotifyChannel)
- **US3 (Phase 5)**: Depends on Foundational (verification-only phase, most work done in Phase 2)
- **Polish (Phase 6)**: Depends on all phases complete

### User Story Dependencies

- **US1 (P1)**: Can run after Foundational — independent, only uses NotifyTarget (no Profile coupling)
- **US2 (P2)**: Depends on US1 (adapter interface) + Foundational (Profile, ProfileContext, Sandbox)
- **US3 (P3)**: Implemented in Foundational; Phase 5 is verification-only

### Within Each Phase

- Tests (harness) written first, expected to FAIL before implementation
- Implementation follows, tests turn green
- Phase checkpoint: all tests for that phase pass before moving to next

### Parallel Opportunities

- T001–T005: All Sandbox tasks (different files, no dependencies) — run in parallel
- T006, T007: NotifyChannelConfig + test — run in parallel
- T013–T016: All US1 test methods (within same file, one developer sequential)
- T017, T018: NotifyTarget + NotifyChannelAdapter (different files) — run in parallel
- T021–T024: All US2 test methods (within same file, one developer sequential)

---

## Parallel Example: Foundational Phase

```bash
# Launch all independent foundational tasks together:
Task: "Create NotifyChannelConfig record in oryxos-core/.../NotifyChannelConfig.java"
Task: "Create NotifyChannelConfigTest in oryxos-core/.../NotifyChannelConfigTest.java"

# Then sequentially (each depends on previous):
Task: "Modify Profile.java notifyChannels type"
Task: "Modify ProfileLoader.java parseNotifyChannels"
Task: "Modify ProfileLoaderTest.java YAML test data"
Task: "Modify ProfileContext.java add resolveNotifyChannel + @Component"
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Setup (Sandbox 占位)
2. Complete Phase 2: Foundational (Profile 改造)
3. Complete Phase 3: US1 (WebhookNotifyAdapter + tests)
4. **STOP and VALIDATE**: `mvn test -pl oryxos-tool -Dtest=WebhookNotifyAdapterTest` — all green
5. US1 is independently demonstrable (MockWebServer proves adapter works)

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add US1 → Test independently → MockWebServer 验证通过 (MVP!)
3. Add US2 → Test independently → InOrder 顺序断言通过
4. Add US3 → Verify tests pass (already implemented)
5. Full `mvn clean verify` → all modules green

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Tests follow harness-first: write test, see it fail, implement, see it pass
- `NotifyTools` 的完整 @Tool 注册接线依赖 20 节 ToolRegistry; 本节 NotifyToolsTest 用 mock 独立验证三步逻辑
- 前序节 ProfileLoaderTest 的 `notify_channels` YAML 格式必须同步更新（从 list of strings → list of objects）
- Commit after each phase checkpoint
- Java 18+ 语法禁用: no `switch { case X -> ... }`, no text blocks with `"""...`
