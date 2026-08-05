---

description: "Memory 模块（第 22 节）实现任务清单"
---

# Tasks: Memory 模块（第 22 节）

**Input**: Design documents from `/specs/006-memory-module/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 课件"验收 harness"要求 `LongTermMemoryTest` / `MemoryToolsTest` / `MemoryServiceTest` 三个测试类，harness 先行——每个实现任务前先写对应测试并跑红。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## 关键设计约束（plan.md 声明）

- `MemoryService` 接口 + `MemoryScope` 枚举放 **oryxos-core**（`com.oryxos.core`），实现 `DefaultMemoryService` + `LongTermMemory` + `MemoryTools` 放 **oryxos-memory**（`com.oryxos.memory`）。删 init 脚手架桩 `com.oryxos.memory.MemoryService`。
- `PromptBuilder` 构造器新增 `MemoryService` 参数（课件明确"集成点"改造点），`PromptBuilderTest` 同步更新。
- `MemoryTools` 经组合根 `CliSpringBootstrap`（cli 模块）用 `ToolCallbacks.from(memoryTools)` 生成 schema 注册进 `DefaultToolRegistry`，不修改 20 节 `BuiltinToolRegistration.registerAll`。
- 测试方法名必须英文（驼峰），课件 harness 中文方法名进 `@DisplayName`。
- 长期记忆常量：`MAX_ARCHIVE_CHARS = 4000`；区块头 `## 核心记忆` / `## 归档记忆`；条目格式 `- [LocalDate.now()] content`。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 让 oryxos-memory 模块具备实现 Memory 所需的依赖与干净起点

- [x] T001 [P] 在 `oryxos-memory/pom.xml` 增加依赖：`spring-ai-model`（compile，`@Tool` 注解 schema 生成，宪法 II）、`spring-boot-starter-test`（test scope）
- [x] T002 [P] 删除 init 脚手架桩 `oryxos-memory/src/main/java/com/oryxos/memory/MemoryService.java`，更新 `oryxos-memory/src/main/java/com/oryxos/memory/package-info.java` 描述（MemoryService 接口移入 core，本节交付 DefaultMemoryService/LongTermMemory/MemoryTools）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: MemoryService 契约（core）与 LongTermMemory 文件读写（memory）——所有 user story 的地基

**⚠️ CRITICAL**: 本节未完成前任何 user story 都不能开工

- [x] T003 创建 `MemoryScope` 枚举在 `oryxos-core/src/main/java/com/oryxos/core/MemoryScope.java`（CORE / ARCHIVAL 两个值）
- [x] T004 创建 `MemoryService` 接口在 `oryxos-core/src/main/java/com/oryxos/core/MemoryService.java`（`String buildContext(Session session, Profile profile)` / `void remember(String content, MemoryScope scope)` / `List<String> recall(String keyword)`）
- [x] T005 [P] 编写 `LongTermMemoryTest` 在 `oryxos-memory/src/test/java/com/oryxos/memory/LongTermMemoryTest.java`（`@TempDir` 临时目录；构造器收 Path；先跑红）。方法名英文 + `@DisplayName` 保留课件原文：
  - `truncatesArchiveOnly_keepsCoreIntact`（课件：截断只裁归档区_核心记忆一字不能少）：`append(CORE,"用户叫小王，偏好用 Java")` + 500 条 `append("归档流水 i", ARCHIVAL)` 灌超阈值 → `load()` 后 `assertTrue(contains("用户叫小王，偏好用 Java"))`、`assertFalse(contains("归档流水 0"))`、`assertTrue(contains("归档流水 499"))`
  - `writeThenReadImmediately_noCache`（课件：写入后立刻可读_不允许有缓存）：`append("刚记的事", ARCHIVAL)` → `assertTrue(load().contains("刚记的事"))`、`assertFalse(recallByKeyword("刚记的事").isEmpty())`
  - `routesScopeToCorrectSection`（scope 路由到正确区块）：`append(..., CORE)` 落 `## 核心记忆` 下、`append(..., ARCHIVAL)` 落 `## 归档记忆` 下
  - `recallSearchesArchiveOnly`（recall 只搜归档区）：核心区命中词不返回、归档区命中词返回
- [x] T006 实现 `LongTermMemory` 在 `oryxos-memory/src/main/java/com/oryxos/memory/LongTermMemory.java`：常量 `CORE_HEADER="## 核心记忆"`/`ARCHIVE_HEADER="## 归档记忆"`/`MAX_ARCHIVE_CHARS=4000`；`append(content, scope)` 按 scope 定位区块追加 `\n- [date] content`；`load()` 每次 `Files.readString` 不缓存（坑一）、核心区 `extractSection` 完整返回 + 归档区 `truncateIfNeeded`（坑二）；`recallByKeyword(keyword)` 只搜归档区 `String.contains` 行匹配（坑四）；文件不存在返回空、append 自动建目录文件

**Checkpoint**: Foundation ready——LongTermMemory 四坑测试全绿，user story 可开工

---

## Phase 3: User Story 1 - Agent 主动记住值得长期保留的事 (Priority: P1) 🎯 MVP

**Goal**: Agent 能调 `save_memory` 把内容（含归类）写进长期记忆文件

**Independent Test**: `MemoryToolsTest` 的 scope 缺省用例 + `LongTermMemoryTest` 写后立读——同一进程内"记完立刻生效"

### Tests for User Story 1

> **NOTE: 先写测试，先跑红再实现**

- [x] T007 [P] [US1] 在 `oryxos-memory/src/test/java/com/oryxos/memory/MemoryToolsTest.java` 写 `saveMemory_defaultsToArchival`（课件：scope 缺省写归档）：mock `MemoryService`，调 `saveMemory("内容", null)` → `verify(memoryService).remember("内容", MemoryScope.ARCHIVAL)`

### Implementation for User Story 1

- [x] T008 [US1] 实现 `DefaultMemoryService` 在 `oryxos-memory/src/main/java/com/oryxos/memory/DefaultMemoryService.java`：实现 `MemoryService`，构造注入 `LongTermMemory`；`remember(content, scope)` 中 scope 为 null 或缺省值时取 `MemoryScope.ARCHIVAL`（坑三：系统不猜核心），委托 `longTermMemory.append(content, scope)`
- [x] T009 [US1] 实现 `MemoryTools` 的 `saveMemory` 在 `oryxos-memory/src/main/java/com/oryxos/memory/MemoryTools.java`：`@Component` + `@Tool(name="save_memory", description="记住一件值得长期记住的事")`，方法 `ToolResult saveMemory(String content, String scope)`（scope 可空，`@ToolParam` 标注"core 或 archival，不确定就填 archival"），调 `memoryService.remember(...)` 返回 `ToolResult.ok("已记住")`——沿用 20 节内置 Tool 返回 `ToolResult` 的既有形态

**Checkpoint**: US1 完成——Agent 能记住（写核心或归档），记完下一轮立即可见

---

## Phase 4: User Story 2 - Agent 按关键词检索长期记忆 (Priority: P2)

**Goal**: Agent 能调 `recall_memory` 按关键词从归档区检索，未命中返回提示不报错

**Independent Test**: `MemoryToolsTest` 的未命中用例——返回"没有找到相关记忆"不抛异常

### Tests for User Story 2

- [x] T010 [P] [US2] 在 `oryxos-memory/src/test/java/com/oryxos/memory/MemoryToolsTest.java` 写 `recallMemory_returnsNoResultMessage_whenNoMatch`（课件：关键词未命中返回"没有找到相关记忆"而不是抛异常）：mock `MemoryService.recall(keyword)` 返回空列表 → `recallMemory("xyz")` 返回内容含"没有找到相关记忆"，不抛异常

### Implementation for User Story 2

- [x] T011 [US2] 在 `DefaultMemoryService` 加 `recall(keyword)`：委托 `longTermMemory.recallByKeyword(keyword)` 返回匹配行列表
- [x] T012 [US2] 在 `MemoryTools` 加 `recallMemory`：`@Tool(name="recall_memory", description="按关键词检索长期记忆")`，方法 `ToolResult recallMemory(String keyword)`，命中返回 `String.join("\n", hits)`、未命中返回"没有找到相关记忆"（不抛异常）

**Checkpoint**: US1 + US2 完成——记得住、读得出，未命中不报错

---

## Phase 5: User Story 3 - 记忆上下文合成 + PromptBuilder 集成 (Priority: P3)

**Goal**: `buildContext` 合成"核心记忆 + 会话历史"记忆上下文，`PromptBuilder` 组装时拼进 system prompt；工具注册与 MEMORY.md 模板落定

**Independent Test**: `MemoryServiceTest`——`buildContext` 返回核心记忆 + 会话历史组合，归档区不整体注入

### Tests for User Story 3

- [x] T013 [P] [US3] 编写 `MemoryServiceTest` 在 `oryxos-memory/src/test/java/com/oryxos/memory/MemoryServiceTest.java`（`@TempDir` + 真 `LongTermMemory` + 真 `Session`）：`buildContext_returnsCoreAndHistory_archiveNotFullyInjected`（课件：buildContext 返回核心记忆 + 会话历史的组合，归档区不整体注入）——先写核心记忆 + 500 条归档 + 会话消息 → `buildContext` 含核心内容、含某条会话消息、不含 `归档流水 0`（归档被截断不整体注入）、含 `归档流水 499`
- [x] T014 [US3] 在 `DefaultMemoryService` 加 `buildContext(Session session, Profile profile)`：返回核心记忆全文 + 归档记忆截断段（经 `longTermMemory.load()`）+ 会话历史摘要（从 `session.messages()` 取）三段拼接

### Integration for User Story 3

- [x] T015 [US3] 更新 `PromptBuilder` 在 `oryxos-core/src/main/java/com/oryxos/core/PromptBuilder.java`：构造器新增 `MemoryService memoryService` 参数；`buildSystemMessage` 改收 `(Session session, Profile profile)`，在角色设定 + Bootstrap/Skill 之后追加 `memoryService.buildContext(session, profile)` 返回的非空文本（替换现有 `buildMemorySection` 占位）
- [x] T016 [US3] 更新 `PromptBuilderTest` 在 `oryxos-core/src/test/java/com/oryxos/core/PromptBuilderTest.java`：构造器传 mock `MemoryService`；新增断言——`build` 的 systemMessage 含 mock 返回的记忆文本；既有四部分断言保持全绿
- [x] T017 [US3] 更新 `CliSpringBootstrap` 在 `oryxos-cli/src/main/java/com/oryxos/cli/CliSpringBootstrap.java`：新增 `@Bean` 装配 `LongTermMemory`（指向 `WORKSPACE_DIR.resolve("memory/MEMORY.md")`）→ `DefaultMemoryService` → `MemoryTools`；`@ComponentScan` basePackages 增加 `com.oryxos.memory`；用 `ToolCallbacks.from(memoryTools)` 生成 schema、以本地小适配器包装成 `OryxTool` 注册进 `DefaultToolRegistry`（不修改 `BuiltinToolRegistration`）；如 cli pom 缺 `oryxos-memory` 依赖则补上
- [x] T018 [US3] 更新 `InitCommand` 在 `oryxos-cli/src/main/java/com/oryxos/cli/InitCommand.java`：`memory/MEMORY.md` 模板改为两区块约定（`## 核心记忆` / `## 归档记忆` 骨架，保留"不得手动修改"提示）

**Checkpoint**: US3 完成——记忆上下文拼进 system prompt，工具注册 + 模板落定

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量门禁、H4 不变量、交付物核对

- [x] T019 [P] 跑 `mvn clean verify` 全量门禁（含 P3C/SpotBugs/FindSecBugs/PMD），全绿；确认前序节（16-20）测试回归全绿（尤其 `PromptBuilderTest`、`OryxToolContractTest`、`ToolListCommand` 相关测试）
- [x] T020 [P] H4 六条不变量自查：①无涉外 IO 需过 Sandbox（save_memory/recall_memory 是白名单内 `.oryxos/memory/` 本地文件，不涉外）②MemoryTools 走 `ToolExecutor` 自动落 `tool_invocations` ③grep 无明文 key ④`session_id` 只在 `SessionManager` 内拼接（本节不触碰）⑤无 Reactor/CompletableFuture/自建线程池 ⑥无 Spring AI 自动工具执行路径
- [x] T021 [P] 交付物存在性核对：`MemoryService`（core 接口）/`MemoryScope`/`DefaultMemoryService`/`LongTermMemory`/`MemoryTools` + 三个测试类非空；`.oryxos/memory/MEMORY.md` 两区块模板在 `InitCommand`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup——BLOCKS 所有 user story
- **User Stories (Phase 3+)**: 依赖 Foundational；US1 → US2 → US3 顺序（US2 复用 US1 的 DefaultMemoryService/MemoryTools 类，US3 复用前两者 + PromptBuilder 集成）
- **Polish (Phase 6)**: 依赖所有 user story 完成

### User Story Dependencies

- **US1 (P1)**: 依赖 Foundational（LongTermMemory + MemoryService 契约）——无其他故事依赖
- **US2 (P2)**: 依赖 US1（`DefaultMemoryService`/`MemoryTools` 类扩展）
- **US3 (P3)**: 依赖 US1 + US2（复用 `DefaultMemoryService.buildContext`）

### 并行机会

- T001/T002（Setup）可并行
- T005/T003/T004（Foundational 内测试与契约）T005 与其他无文件冲突
- 各 user story 的 [P] 测试任务可与其实现并行起步

### 关键验证命令

```bash
# 只跑 Memory 模块（含 core 依赖，-am 带出）
mvn -pl oryxos-memory -am test
# 只跑 core 的 PromptBuilder 相关测试
mvn -pl oryxos-core test
# 全量门禁
mvn clean verify
```

---

## 实现策略

### MVP First (US1 先行)

1. 完成 Phase 1（Setup）
2. 完成 Phase 2（Foundational）——LongTermMemory 四坑测试全绿
3. 完成 Phase 3（US1）——`save_memory` 可记住
4. **STOP 验证**：US1 独立测试通过

### 增量交付

1. US1（记住）→ 测试绿 → Demo 二的基础写入能力
2. US2（检索）→ 测试绿 → 记住 + 读回
3. US3（上下文合成 + 集成）→ 测试绿 → 完整闭环

## Notes

- [P] 任务 = 不同文件、无依赖
- 每个实现任务前，对应测试先红后绿（harness 先行）
- 课件"验收 harness"写出代码的两个测试（T005 里的截断 + 无缓存）断言逐条保真，是本节最重要保险
- 不 commit / push，同步时机由用户决定
