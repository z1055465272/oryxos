# Tasks: Tool 体系

**Input**: Design documents from `specs/005-tool-system/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Included per specification — OryxToolContractTest, ToolRegistryTest, FileToolsTest, ShellToolsTest, HttpToolsTest, McpToolAdapterTest, McpClientServiceTest. Harness 先行原则（测试先于实现）。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `oryxos-core/src/main/java/com/oryxos/core/` — OryxTool/ToolResult/ToolRegistry 接口（16 节已有，本节不改）
- `oryxos-tool/src/main/java/com/oryxos/tool/` — Registry 实现、内置工具、MCP、Sandbox 接口
- `oryxos-tool/src/test/java/com/oryxos/tool/` — 本节全部测试
- `oryxos-cli/src/main/java/com/oryxos/cli/` — CliSpringBootstrap 装配改造
- `.oryxos/mcp_servers.yaml` — MCP server 配置模板

---

## Phase 1: Setup（工具注册表基础设施）

**Purpose**: 交付 `DefaultToolRegistry`（ToolRegistry 接口的具体实现，落 oryxos-tool/registry）——这是 US1/US2/US3 共同的阻塞前置，因为三种来源的工具都要注册进来、按 Profile.tools 过滤

- [ ] T001 [P] Create `DefaultToolRegistry` implementing `ToolRegistry` (concurrent map index, `register`/`get`/`listAll`/`toolsFor(List<String>)`) in `oryxos-tool/src/main/java/com/oryxos/tool/registry/DefaultToolRegistry.java`
- [ ] T002 [P] Create `ToolRegistryTest` — three-source tools register as OryxTool; `toolsFor` filter returns exactly the declared subset (not one more, not one less) in `oryxos-tool/src/test/java/com/oryxos/tool/registry/ToolRegistryTest.java`
- [ ] T003 [P] Create `OryxToolContractTest` — `@ParameterizedTest` over `registry.listAll()`, assert name/description/inputSchema all non-null in `oryxos-tool/src/test/java/com/oryxos/tool/contract/OryxToolContractTest.java`

**Checkpoint**: Registry 实现 + 过滤语义 + 契约测试就位，user story implementation can now begin

---

## Phase 2: User Story 1 - 内置工具让 Agent 读写文件、跑命令、调 API (Priority: P1) 🎯 MVP

**Goal**: FileTools (read_file/write_file/list_dir)、ShellTools (shell)、HttpTools (http_get/http_post) 六个内置工具，每个执行第一步过 `Sandbox.enforce`，越界被拦

**Independent Test**: `./mvnw test -pl oryxos-tool -Dtest=FileToolsTest,ShellToolsTest,HttpToolsTest`

### Tests for User Story 1 (harness 先行，写先于实现)

- [ ] T004 [P] [US1] Create `FileToolsTest` skeleton with `@TempDir`, mock `Sandbox` in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/FileToolsTest.java`
- [ ] T005 [P] [US1] Write test `readFileWithinAllowlistReturnsContent` — 白名单内路径返回成功内容 in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/FileToolsTest.java`
- [ ] T006 [P] [US1] Write test `readFileOutsideAllowlistIsBlocked` — mock Sandbox 命中即抛，断言 execute 抛 RuntimeException in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/FileToolsTest.java`
- [ ] T007 [P] [US1] Write test `writeFileAndListDirWithinAllowlist` — 写入回读 + 列目录成功 in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/FileToolsTest.java`
- [ ] T008 [P] [US1] Create `ShellToolsTest` skeleton with mock `Sandbox` in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/ShellToolsTest.java`
- [ ] T009 [P] [US1] Write test `shellRunsAllowlistedCommand` — 白名单内命令执行返回输出（用 `java -version` 或平台无关命令）in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/ShellToolsTest.java`
- [ ] T010 [P] [US1] Write test `shellOutsideAllowlistIsBlocked` — mock Sandbox 抛异常，断言 execute 抛 RuntimeException、真实子进程不启动 in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/ShellToolsTest.java`
- [ ] T011 [P] [US1] Create `HttpToolsTest` skeleton with MockWebServer + mock `Sandbox` in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/HttpToolsTest.java`
- [ ] T012 [P] [US1] Write test `httpGetWithinAllowlistReturnsBody` — MockWebServer 返回 body 断言成功 in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/HttpToolsTest.java`
- [ ] T013 [P] [US1] Write test `httpGetOutsideAllowlistIsBlocked` — mock Sandbox 抛异常，断言 execute 抛 RuntimeException、请求未发出 in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/HttpToolsTest.java`
- [ ] T014 [P] [US1] Write test `httpPostSendsBody` — MockWebServer 断言收到 POST 且 body 原样 in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/HttpToolsTest.java`

### Implementation for User Story 1

- [ ] T015 [P] [US1] Create `FileTools` @Component — `@Tool` methods read_file/write_file/list_dir, 每个方法首行 `sandbox.enforce(FILE_READ/FILE_WRITE)` in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/FileTools.java`
- [ ] T016 [P] [US1] Create `ShellTools` @Component — `@Tool` method shell, 首行 `sandbox.enforce(SHELL)`, ProcessBuilder 执行带超时 in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/ShellTools.java`
- [ ] T017 [P] [US1] Create `HttpTools` @Component — `@Tool` methods http_get/http_post, 首行 `sandbox.enforce(HTTP_REQUEST)`, RestClient 发请求 in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpTools.java`
- [ ] T018 [US1] Run `./mvnw test -pl oryxos-tool -Dtest=FileToolsTest,ShellToolsTest,HttpToolsTest` — all green

**Checkpoint**: US1 functional — 六个内置工具"正常能跑通 + 越界会被拦"全部通过

---

## Phase 3: User Story 2 - 统一工具抽象与注册，让任意来源的工具对循环透明 (Priority: P1)

**Goal**: 内置工具以 `@Tool` 注解 + `ToolCallbacks.from` 生成 schema 包装成 OryxTool 注册进 DefaultToolRegistry；NotifyTools (19 节) 一并接线；契约三件套全部非空

**Independent Test**: `./mvnw test -pl oryxos-tool -Dtest=OryxToolContractTest,ToolRegistryTest`

### Tests for User Story 2 (harness 先行)

- [ ] T019 [P] [US2] Extend `OryxToolContractTest` — register real built-in tools (FileTools/ShellTools/HttpTools/NotifyTools) into a `DefaultToolRegistry`, `@MethodSource("allRegisteredTools")` returns every registered tool in `oryxos-tool/src/test/java/com/oryxos/tool/contract/OryxToolContractTest.java`
- [ ] T020 [P] [US2] Extend `ToolRegistryTest` — add case: register a `@Tool`-wrapped builtin (via `ToolCallbacks.from`) + an MCP-style adapter (custom OryxTool impl), filter by `toolsFor(["read_file","http_get","mcp_x"])` asserts exact subset in `oryxos-tool/src/test/java/com/oryxos/tool/registry/ToolRegistryTest.java`

### Implementation for User Story 2

- [ ] T021 [US2] Create `BuiltinToolRegistration` @Component — `@PostConstruct` or boot wiring: `ToolCallbacks.from(fileTools, shellTools, httpTools, notifyTools)` → wrap each `ToolCallback` (name/description from `getToolDefinition()`, `execute` delegates `call(String)`) → `registry.register(...)` in `oryxos-tool/src/main/java/com/oryxos/tool/registry/BuiltinToolRegistration.java`
- [ ] T022 [US2] Run `./mvnw test -pl oryxos-tool -Dtest=OryxToolContractTest,ToolRegistryTest` — all green (NotifyTools 的 @Tool 已满足三件套非空)

**Checkpoint**: US2 functional — 内置工具 + NotifyTools 统一以 OryxTool 注册，契约三件套全非空

---

## Phase 4: User Story 3 - 业务方通过 MCP server 接入自己的工具（方式二），失联不拖垮启动 (Priority: P2)

**Goal**: McpServerConfig/Loader + McpToolAdapter + McpClientService，读 mcp_servers.yaml 连接、tools/list 包装注册、失联只 WARN

**Independent Test**: `./mvnw test -pl oryxos-tool -Dtest=McpToolAdapterTest,McpClientServiceTest`

### Tests for User Story 3 (harness 先行)

- [ ] T023 [P] [US3] Create `McpToolAdapterTest` with mock `McpSyncClient` in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpToolAdapterTest.java`
- [ ] T024 [P] [US3] Write test `getNameDescriptionInputSchemaMapFromMcpTool` — adapter 的 name/description/inputSchema 直接映射 McpSchema.Tool in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpToolAdapterTest.java`
- [ ] T025 [P] [US3] Write test `executeForwardsArgumentsAndWrapsResult` — mock client.callTool, 断言 arguments 原样转发、TextContent 包进 ToolResult.ok in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpToolAdapterTest.java`
- [ ] T026 [P] [US3] Write test `executeErrorMarksRetryableFailure` — CallToolResult.isError()==true 时返回 ToolResult.fail(..., true) in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpToolAdapterTest.java`
- [ ] T027 [P] [US3] Create `McpClientServiceTest` with mocked `McpClient.sync` path in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpClientServiceTest.java`
- [ ] T028 [P] [US3] Write test `listToolsWrapsAndRegistersEachTool` — 一个 server 返回 2 个工具 → 都包装注册 in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpClientServiceTest.java`
- [ ] T029 [P] [US3] Write test `serverFailureIsIsolatedNotFatal` — 坏 server (ConnectException) 只 WARN、其余 server 照常注册、整体不抛异常 in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpClientServiceTest.java`

### Implementation for User Story 3

- [ ] T030 [P] [US3] Create `McpServerConfig` record (name/transport/command/args/env, defensive copy) in `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpServerConfig.java`
- [ ] T031 [P] [US3] Create `McpServerConfigLoader` — SnakeYAML 解析 `.oryxos/mcp_servers.yaml` 顶层 `mcpServers:` → List<McpServerConfig>, 缺失/为空 → 空列表, env `${ENV}` 占位解析 in `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpServerConfigLoader.java`
- [ ] T032 [P] [US3] Create `McpToolAdapter` implements OryxTool — 映射 McpSchema.Tool 三件套, execute 解析 JSON → CallToolRequest → client.callTool → ToolResult in `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpToolAdapter.java`
- [ ] T033 [US3] Create `McpClientService` @Component — `@PostConstruct connectAll()`: 逐个 server try/catch (连接失败/listTools 失败只 WARN 跳过), `McpClient.sync(...)` 同步客户端, 按 transport 路由 (stdio→StdioClientTransport, sse→HttpClientSseClientTransport); `@PreDestroy disconnectAll()` in `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpClientService.java`
- [ ] T034 [US3] Run `./mvnw test -pl oryxos-tool -Dtest=McpToolAdapterTest,McpClientServiceTest` — all green

**Checkpoint**: US3 functional — MCP 工具包装注册、失联隔离、参数原样转发全部通过

---

## Phase 5: User Story 4 - 重代码方式三：Java @Tool 注解 Bean 自动注册 (Priority: P3)

**Goal**: CliSpringBootstrap 装配——stub ToolRegistry 替换为 DefaultToolRegistry + 内置工具 + McpClientService; 装配后契约/过滤/失联测试在真实注册表上复跑

**Independent Test**: `./mvnw test -pl oryxos-tool -Dtest=OryxToolContractTest` (装配后的真实注册表遍历)

### Implementation for User Story 4

- [ ] T035 [US4] Modify `CliSpringBootstrap.java` — replace stub anonymous `ToolRegistry` bean with `@Bean DefaultToolRegistry`, add beans: FileTools/ShellTools/HttpTools/NotifyTools(现有 @Component 扫描即可)/BuiltinToolRegistration/McpClientService(现有 @Component 扫描), wire ToolRegistry 注入 in `oryxos-cli/src/main/java/com/oryxos/cli/CliSpringBootstrap.java`
- [ ] T036 [US4] Run `./mvnw test -pl oryxos-tool -Dtest=OryxToolContractTest,ToolRegistryTest` — 确认装配后契约三件套全非空、过滤精确
- [ ] T037 [US4] Add `.oryxos/mcp_servers.yaml` template (empty `mcpServers: []` with commented example) — workspace config file for MCP server declarations in `.oryxos/mcp_servers.yaml`

**Checkpoint**: US4 functional — 装配完成，`tool list` 可见内置工具 + 方式三示例工具（人工项）

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量门禁通过、前序节回归、语法禁区、依赖方向验证

- [ ] T038 Run `./mvnw clean verify` — full build with Spotless/Checkstyle/P3C/PMD, all modules green
- [ ] T039 Verify all pre-existing tests from lessons 16/17/18/19 still pass (regression in oryxos-core, oryxos-provider, oryxos-storage, oryxos-cli, oryxos-channel-cli)
- [ ] T040 Verify no Java 18+ syntax banned by P3C/ASM (no `switch case X ->`, no text blocks with `"""`) in new files
- [ ] T041 Verify dependency direction: grep that `oryxos-provider` does NOT depend on `oryxos-tool`; `oryxos-tool` depends only on `oryxos-core` (no circular dep)
- [ ] T042 Run `quickstart.md` validation scenarios (mcp_servers.yaml template, tool list smoke)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — DefaultToolRegistry is standalone
- **US1 (Phase 2)**: Depends on Setup (built-in tools register into DefaultToolRegistry)
- **US2 (Phase 3)**: Depends on Setup + US1 (needs FileTools/ShellTools/HttpTools beans + NotifyTools for @Tool registration)
- **US3 (Phase 4)**: Depends on Setup (McpClientService registers adapters into registry); independent of US1/US2
- **US4 (Phase 5)**: Depends on US1+US2+US3 (boot wiring assembles all)
- **Polish (Phase 6)**: Depends on all phases complete

### User Story Dependencies

- **US1 (P1)**: After Setup — independent, only needs DefaultToolRegistry + Sandbox
- **US2 (P1)**: Depends on US1 (built-in tool beans) — registration wiring
- **US3 (P2)**: After Setup — independent of US1/US2 (MCP path)
- **US4 (P3)**: Depends on US1+US2+US3 — boot assembly

### Within Each Phase

- Tests (harness) written first, expected to FAIL before implementation
- Implementation follows, tests turn green
- Phase checkpoint: all tests for that phase pass before moving to next

### Parallel Opportunities

- T001–T003: Registry + contract test (different files) — run in parallel
- T004–T014: US1 test methods (within same files, sequential per file) — file-level parallel
- T015, T016, T017: FileTools/ShellTools/HttpTools (different files) — run in parallel
- T023–T029: US3 test methods (two files, sequential per file) — file-level parallel
- T030, T031, T032: McpServerConfig/Loader/Adapter (different files) — run in parallel

---

## Parallel Example: US1 Implementation

```bash
# Launch all independent built-in tool classes together:
Task: "Create FileTools in oryxos-tool/src/main/java/com/oryxos/tool/builtin/FileTools.java"
Task: "Create ShellTools in oryxos-tool/src/main/java/com/oryxos/tool/builtin/ShellTools.java"
Task: "Create HttpTools in oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpTools.java"

# Then sequentially (each depends on previous compile):
Task: "Run FileToolsTest/ShellToolsTest/HttpToolsTest"
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Setup (DefaultToolRegistry + contract test)
2. Complete Phase 2: US1 (FileTools/ShellTools/HttpTools + tests)
3. **STOP and VALIDATE**: `./mvnw test -pl oryxos-tool -Dtest=FileToolsTest,ShellToolsTest,HttpToolsTest` — all green
4. US1 is independently demonstrable (内置工具跑通 + 越界拦截)

### Incremental Delivery

1. Setup → Registry 就位
2. Add US1 → 内置工具 (MVP)
3. Add US2 → @Tool 注册接线 (内置 + NotifyTools)
4. Add US3 → MCP 接入 (方式二)
5. Add US4 → Boot 装配 (方式三)
6. Full `./mvnw clean verify` → all modules green

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Tests follow harness-first: write test, see it fail, implement, see it pass
- `OryxTool.getInputSchema()` 返回 String（16 节已定，不改）; MCP 的 `inputSchema()` (Map) 用 Jackson 序列化成 String
- `ToolCallbacks.from(Object...)` 生成 `ToolDefinition`（name/description/inputSchema 三件套），来自 `org.springframework.ai.support.ToolCallbacks`（spring-ai-model 1.0.9，已核实）
- `McpClient.sync(transport).build()` → `McpSyncClient`（listTools/callTool 全同步，宪法 VII）
- Sandbox 为占位（NoOpSandbox 放行），越界测试用"命中即抛"的 mock Sandbox；24 节换 WhitelistSandbox 后断言不变
- `save_memory`/`recall_memory` 归 22 节 Memory 模块，本节不实现
- Java 18+ 语法禁用: no `switch { case X -> ... }`, no text blocks with `"""...`
