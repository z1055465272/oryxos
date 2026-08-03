# Tasks: Agent Provider

**Input**: Design documents from `specs/001-agent-provider/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, quickstart.md

**Tests**: 课件明确要求测试先行 (harness 先行), 每个实现任务伴随对应测试。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Maven 多模块项目, 模块路径:
- `oryxos-core/src/main/java/com/oryxos/core/`
- `oryxos-provider/src/main/java/com/oryxos/provider/`
- `oryxos-storage/src/main/java/com/oryxos/storage/`
- `oryxos-storage/src/main/resources/`

测试路径 parallel:
- `oryxos-core/src/test/java/com/oryxos/core/`
- `oryxos-provider/src/test/java/com/oryxos/provider/`
- `oryxos-storage/src/test/java/com/oryxos/storage/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 确认依赖可用, 准备共享配置与基础设施

- [ ] T001 Run `mvn dependency:resolve -pl oryxos-provider,oryxos-storage` to verify Spring AI 1.0.9 + SQLite dependencies are resolvable
- [ ] T002 [P] Create `OryxOsProperties` configuration properties class in `oryxos-provider/src/main/java/com/oryxos/provider/OryxOsProperties.java` — reads `oryxos.providers` list from application.yaml, each with name/base-url/api-key
- [ ] T003 [P] Create `ProviderNotFoundException` runtime exception in `oryxos-provider/src/main/java/com/oryxos/provider/ProviderNotFoundException.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有 User Story 共享的核心数据模型与审计基础设施

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 [P] Create `Profile` record with all fields and nested records (`Identity`, `ProviderRef`, `Settings`, `ChannelRef`, `ScheduleConfig`) in `oryxos-core/src/main/java/com/oryxos/core/Profile.java`
- [ ] T005 [P] Create schema.sql DDL for `llm_calls` table (hand-written, NOT relying on hibernate.ddl-auto=update) in `oryxos-storage/src/main/resources/schema.sql` — MUST include `success` (BOOLEAN NOT NULL) and `error_message` (TEXT) columns
- [ ] T006 [P] Create `LlmCall` JPA entity in `oryxos-storage/src/main/java/com/oryxos/storage/LlmCall.java` — maps to `llm_calls` table with all columns: id, sessionId, provider, model, promptTokens, completionTokens, totalTokens, durationMs, success, errorMessage, createdAt
- [ ] T007 Create `LlmCallRepository` Spring Data JPA interface in `oryxos-storage/src/main/java/com/oryxos/storage/LlmCallRepository.java` — extends JpaRepository<LlmCall, Long>
- [ ] T008 [P] [US1] Write `LlmCallRepositoryTest` in `oryxos-storage/src/test/java/com/oryxos/storage/LlmCallRepositoryTest.java` — verify: hand-written schema.sql creates table correctly; save+read works; `success` and `error_message` columns both exist and accept values

**Checkpoint**: Foundation ready — `Profile` record, `LlmCall` entity + repository + DDL all in place

---

## Phase 3: User Story 1 - Provider 路由与调用 (Priority: P1) 🎯 MVP

**Goal**: `ProviderService.chat(sessionId, Profile, Prompt)` 完整实现 — 显式映射路由、调用 LLM、关闭自动 tool 执行、成功/失败都落审计

**Independent Test**: Mock 两个 ChatModel, 通过 Provider 按 Profile 分别调用, 验证路由准确、响应返回、审计落库。核心测试文件: `ProviderServiceTest` + `ProviderSmokeIT`

### Tests for User Story 1 (harness 先行 — 先写、先红) ⚠️

- [ ] T009 [P] [US1] Write `ProviderServiceTest` in `oryxos-provider/src/test/java/com/oryxos/provider/ProviderServiceTest.java` — 4 key regression tests per 课件 harness:
  - `routesToCorrectProviderTwoProvidersDoNotConflict` (按名路由_两个provider不串台): mock deepseek + kimi, call with kimi profile, verify only kimi called
  - `callFailureMustRecordAuditWithSuccessFalse` (调用失败_审计必须留下success为false的记录): mock throws RuntimeException, assert audit recorded success=false + error_message contains "timeout", exception still propagated
  - `toolSchemaCallDisablesAutoExecution` (带工具schema调用_请求里关闭了自动执行): call with tools, capture Request, assert autoExecuteTools=false, tools present
  - `unknownProviderThrowsException` (未知provider抛异常): call with non-existent provider name, assert throws ProviderNotFoundException
  - All test method names MUST be in English; use `@DisplayName` for 课件 original Chinese names

- [ ] T010 [P] [US1] Write `ProviderSmokeIT` in `oryxos-provider/src/test/java/com/oryxos/provider/ProviderSmokeIT.java` — @Tag("integration"), reads DEEPSEEK_API_KEY from env, makes real LLM call, asserts non-empty response + llm_calls has one more success=true record

### Implementation for User Story 1

- [ ] T011 [US1] Extend `ProviderService` interface in `oryxos-provider/src/main/java/com/oryxos/provider/ProviderService.java` — keep existing `resolve(String)` method, add `chat(String sessionId, Profile profile, Prompt prompt)` method signature
- [ ] T012 [US1] Implement `DefaultProviderService` in `oryxos-provider/src/main/java/com/oryxos/provider/DefaultProviderService.java`:
  - Constructor takes `OryxOsProperties` + `LlmCallRepository`, builds Map<String, ChatModel> from config
  - `chat()`: resolve model from map → throw ProviderNotFoundException if missing → build Prompt with ToolCallingChatOptions (internalToolExecutionEnabled=false) → call model → record audit (success) → catch RuntimeException → record audit (failure, success=false, errorMessage) → re-throw
  - `resolve()`: delegate to providerMap.get()
  - MUST use Spring AI 1.0.x ChatModel.call(Prompt) API, NOT ChatClient

- [ ] T013 [US1] Verify ProviderServiceTest and ProviderSmokeIT PASS against DefaultProviderService implementation

**Checkpoint**: User Story 1 complete — Provider routing, LLM call with auto-exec disabled, success/failure audit

---

## Phase 4: User Story 2 - 工具 Schema 翻译 (Priority: P2)

**Goal**: 将 OryxTool 的 getInputSchema() 翻译为 Spring AI 工具描述格式, 只翻译 schema 不执行

**Independent Test**: 传入 OryxTool, 验证产出物 schema 字段对齐且不含执行逻辑

### Tests for User Story 2 (harness 先行) ⚠️

- [ ] T014 [US2] Write `ToolSchemaAdapterTest` in `oryxos-provider/src/test/java/com/oryxos/provider/ToolSchemaAdapterTest.java`:
  - `schemaFieldsAlignWithOryxTool` (schema字段与OryxTool对齐): create OryxTool with known name/description/schema, translate, verify each field matches
  - `translatedOutputContainsNoExecutionLogic` (翻译产物不含执行逻辑): verify output is pure schema description, no execution hooks
  - `multipleToolsTranslatedIndependently` (多个工具独立翻译): multiple tools, each schema independent

### Implementation for User Story 2

- [ ] T015 [US2] Create `ToolSchemaAdapter` class in `oryxos-provider/src/main/java/com/oryxos/provider/ToolSchemaAdapter.java`:
  - Method `List<ToolDefinition> toSpringAiTools(List<OryxTool> tools)` — converts each OryxTool to Spring AI ToolDefinition
  - Only translates schema, no execution logic
  - Uses OryxTool.getName(), getDescription(), getInputSchema()

- [ ] T016 [US2] Wire `ToolSchemaAdapter` into `DefaultProviderService.chat()` — translate Prompt.getAvailableTools() → Spring AI format, include in request

- [ ] T017 [US2] Verify ToolSchemaAdapterTest PASS and ProviderServiceTest.toolSchemaCallDisablesAutoExecution still PASS

**Checkpoint**: User Story 2 complete — tools can be described to LLM without giving up execution control

---

## Phase 5: User Story 3 - Profile 解析与加载 (Priority: P3)

**Goal**: 从 `.oryxos/profiles/` 目录加载 YAML Profile, 解析全字段, 校验 provider 引用, 坏文件不阻断其余

**Independent Test**: 准备合法 YAML/不存在 provider 的 YAML/语法错误 YAML, 验证加载器和校验行为

### Tests for User Story 3 (harness 先行) ⚠️

- [ ] T018 [US3] Write `ProfileLoaderTest` in `oryxos-core/src/test/java/com/oryxos/core/ProfileLoaderTest.java`:
  - `parsesAllFieldsFromValidYaml` (合法YAML全字段解析): full YAML with all fields → profile parsed correctly
  - `rejectsProfileReferencingNonExistentProvider` (引用不存在的provider报错): YAML with `provider.name: nonexistent`, validators list doesn't include it → loading should log error, not silently pass
  - `badYamlDoesNotBlockRemainingProfiles` (坏文件不阻断其余加载): one bad YAML + one valid YAML → valid one still loaded
  - `envVarPlaceholderResolved` (${ENV_VAR}占位从环境变量解析): `${TEST_ENV_VAR}` in YAML → resolved from System.getenv() or equivalent
  - All test method names in English, `@DisplayName` with 课件 Chinese names

### Implementation for User Story 3

- [ ] T019 [US3] Create `ProfileLoader` class in `oryxos-core/src/main/java/com/oryxos/core/ProfileLoader.java`:
  - Scans `.oryxos/profiles/*.yaml` (or `.yml`)
  - Uses SnakeYAML (`org.yaml.snakeyaml.Yaml`) to parse each file to Map
  - Manually maps YAML fields to Profile record including all nested records
  - Resolves `${ENV_VAR}` placeholders in string values (e.g., api-key)
  - Validates: provider.name exists in provided valid provider names set (passed in as parameter)
  - Bad YAML → log error, continue; bad validation → log error, skip that profile
  - Returns List<Profile> of successfully loaded profiles

- [ ] T020 [US3] Create `ProfileRegistry` class in `oryxos-core/src/main/java/com/oryxos/core/ProfileRegistry.java`:
  - Wraps `Map<String, Profile>` (keyed by profile name)
  - `register(Profile)` — add to registry (for both startup loading and future runtime registration)
  - `get(String name)` — lookup by name, returns Optional<Profile>
  - `listAll()` — returns unmodifiable collection of all profiles
  - Thread-safe (ConcurrentHashMap)

- [ ] T021 [US3] Verify ProfileLoaderTest PASS

**Checkpoint**: User Story 3 complete — Profile loading infrastructure ready

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 跨模块集成验证, 门禁全绿确认

- [ ] T022 Verify all tests PASS: `mvn test -pl oryxos-core,oryxos-provider,oryxos-storage`
- [ ] T023 Run full pipeline: `mvn clean verify` (includes Spotless/Checkstyle/P3C-PMD/SpotBugs), all gates green
- [ ] T024 Manual: `mvn dependency:tree -pl oryxos-provider` confirmation spring-ai-openai and spring-ai-model present at expected versions
- [ ] T025 Manual: `grep -r "sk-" oryxos-*/src/` returns empty (no hardcoded API keys)
- [ ] T026 Run integration smoke manually once: `DEEPSEEK_API_KEY=xxx mvn test -Dgroups=integration -pl oryxos-provider`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup (T001 for dependency check) — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational (T004 Profile, T005-T008 LlmCall) + Setup (T003 ProviderNotFoundException)
  - Tests (T009, T010) MUST be written first, FAIL, then implementation (T011-T012) makes them green
- **User Story 2 (Phase 4)**: Depends on Foundational (T004 Profile with OryxTool) + US1 (T012 DefaultProviderService)
- **User Story 3 (Phase 5)**: Depends on Foundational (T004 Profile record)
- **Polish (Phase 6)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: After Foundational — No dependencies on US2/US3. Tests T009 → Implementation T011-T012
- **US2 (P2)**: After Foundational + US1 (needs DefaultProviderService to wire adapter into chat) — No dependency on US3
- **US3 (P3)**: After Foundational — Independent of US1/US2

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Implementation makes tests green
- Verify checkpoint before moving to next story

### Parallel Opportunities

- T002, T003 can run in parallel (different files in oryxos-provider)
- T004, T005, T006 can run in parallel (different modules/concerns)
- T009, T010 can run in parallel (both are tests)
- T014 is independent of US1 implementation (tests OryxTool → schema translation, no DefaultProviderService needed)
- US2 and US3 can run in parallel after Foundational (if US1 is already done)

---

## Parallel Example: Foundational Phase

```bash
# Launch all parallel foundational tasks together:
Task: "Create Profile record in oryxos-core/.../Profile.java"           (T004)
Task: "Create schema.sql in oryxos-storage/src/main/resources/"         (T005)
Task: "Create LlmCall entity in oryxos-storage/.../LlmCall.java"        (T006)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003)
2. Complete Phase 2: Foundational (T004-T008)
3. Complete Phase 3: User Story 1 (T009-T013)
4. **STOP and VALIDATE**: `mvn test -pl oryxos-provider` → ProviderServiceTest all green
5. MVP is ready: Provider can route calls, LLM audit is written

### Incremental Delivery

1. Setup + Foundational → Core data models ready
2. Add US1 → Provider routing + call + audit (MVP!)
3. Add US2 → Tool schema translation wired in
4. Add US3 → Profile loading from YAML
5. Polish → Full `mvn clean verify` green

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- All test method names MUST be in English; use `@DisplayName` to preserve 课件 original Chinese names
- 课件 harness 中"最值钱的三个测试方法"必须原样落地 (T009 listed them explicitly)
- `mvn clean verify` 必须全绿才是完成 (含 Spotless/Checkstyle/P3C/PMD)
- 不自动 commit, 同步时机由用户决定
