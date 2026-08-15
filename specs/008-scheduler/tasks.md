---

description: "定时任务模块（第 25 节）实现任务清单"
---

# Tasks: 定时任务模块（第 25 节）

**Input**: Design documents from `/specs/008-scheduler/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 课件"验收 harness"要求 `AgentSchedulerTest` 一个类覆盖四个坑：①注册 cron+时区 ②锁占用跳过 ③失败不外抛+锁释放 ④会话三元组固定。harness 先行——实现前先写测试并跑红。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story。四个 user story 是同一个 `AgentScheduler` 类的四个行为维度（到点触发/防重叠/失败隔离/带时区注册），按行为增量拆任务、同一文件顺序实施（不标 [P]，避免同文件冲突）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## 关键设计约束（plan.md 声明）

- `AgentScheduler` 落 `oryxos-core`（`com.oryxos.core`），`@Component` 由既有 `@ComponentScan("com.oryxos.core")` 扫入；组合根 `CliSpringBootstrap` 追加 `ThreadPoolTaskScheduler` bean。
- `ScheduleConfig`/`Profile.schedules` 已在第 19 节落地（`Profile.java:51-56`），本节只消费、零新增。
- **生命周期钩子用 `InitializingBean.afterPropertiesSet()` 替代课件 `@PostConstruct`**（`jakarta.annotation-api` 在 core 仅 test scope，`@PostConstruct` 需新增 compile 依赖，否决）。`registerAll()`/`runOnce()` 方法名照课件保真，harness 直接调它们。
- **`lockFor(String taskId)` 必须公开**——harness 测试用它模拟锁占用（课件"上一次还没跑完"用例）。
- **时区断言必须行为式**：`CronTrigger` 无 zone getter、`equals` 只比表达式（已反编译核实）→ 用 `SimpleTriggerContext(Clock.fixed(...))` + `trigger.nextExecution()` 断言落在配置时区预期时刻。
- 测试方法名必须英文（驼峰），课件 harness 中文方法名进 `@DisplayName`。
- 不改任何前序交付物：`Profile`/`ProfileRegistry`/`SessionManager`/`AgentService`/`ReActLoop` 只读。
- 避开 P3C/ASM 解析不了的 Java 18+ 语法形态，静态检查是构建门禁。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 核实无新增依赖、既有契约签名就位，处于干净可改状态

- [x] T001 [P] 核实并记录：①`spring-context:6.2.19` 含 `ThreadPoolTaskScheduler`/`CronTrigger`/`InitializingBean`（解包/javap 验证，已确认无需新增第三方依赖）；②`Profile.schedules()`（`Profile.ScheduleConfig(id,cron,zone,message,config)`，19 节）、`ProfileRegistry.listAll()`、`SessionManager.getOrCreate(String,String,String)`、`AgentService.process(Session,String)` 签名就位；③`jakarta.annotation-api` 在 oryxos-core 仅 test scope（决定用 InitializingBean 而非 @PostConstruct）。无代码产出，只把结论记录到任务备注

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: `AgentSchedulerTest` 验收 harness——所有 user story 的守点承载者，先写先红

**⚠️ CRITICAL**: 本节未完成前任何 user story 都不能开工

- [x] T002 [P] 编写 `AgentSchedulerTest` 在 `oryxos-core/src/test/java/com/oryxos/core/AgentSchedulerTest.java`（先跑红；直接 `new AgentScheduler(taskScheduler, profileRegistry, sessionManager, agentService)`，前三个 mock，第四个 mock）。方法名英文 + `@DisplayName` 保留课件原文，覆盖四坑：
  - `registerAll_registersCronAndZone`（课件：注册时 CronTrigger 带上了配置的 cron 和时区）——`ArgumentCaptor<Trigger>` 抓 `taskScheduler.schedule(eq(runnable), captor.capture())`，断言 `((CronTrigger) captor.getValue()).getExpression()` 等于配置 cron；时区行为式断言：`SimpleTriggerContext(Clock.fixed(reference, ZoneId.of("UTC")))` + `((CronTrigger) trigger).nextExecution(ctx)` 落在配置 zone 的预期时刻
  - `runOnce_lockHeld_skipsTrigger`（课件：上一次还没跑完_本次触发直接跳过）——`scheduler.lockFor("task-1").lock()` 占用后 `scheduler.runOnce(profile, scheduleConfig("task-1"))`，`verify(agentService, never()).process(any(), any())`；finally 放锁
  - `runOnce_processThrows_notPropagatedAndLockReleased`（课件：任务抛异常_不外抛且锁必须被释放）——`when(agentService.process(any(), any())).thenThrow(new RuntimeException("boom"))`；`assertDoesNotThrow(() -> scheduler.runOnce(profile, scheduleConfig("task-1")))`；再触发一次 `scheduler.runOnce(...)`，`verify(agentService, times(2)).process(any(), any())`（二进宫证明锁释放）
  - `runOnce_fixedSessionTriple_reusesSameSession`（课件：会话三元组固定）——mock `sessionManager.getOrCreate("scheduler", "scheduler", profileName)` 返回同一 Session，`runOnce` 两次触发后 `verify(sessionManager, times(2)).getOrCreate(eq("scheduler"), eq("scheduler"), eq(profileName))` 且 `process` 收到同一 Session
  - 辅助：`scheduleConfig(String id)` 造 `new Profile.ScheduleConfig(id, "0 9 * * *", "Asia/Shanghai", "msg", Map.of())`；`profile(String name, ScheduleConfig...)` 造 Profile（`List.of(schedules)`）

**Checkpoint**: harness 写毕跑红（编译期可能因 `AgentScheduler`/`lockFor` 不存在而失败——预期，下一步实现补上）

---

## Phase 3: User Story 1 - 到点自动触发，会话三元组固定 (Priority: P1) 🎯 MVP

**Goal**: `AgentScheduler` 到点把配置消息交给 `AgentService`，会话身份固定（scheduler, scheduler, profileName），复用同一 Session

**Independent Test**: `runOnce_fixedSessionTriple_reusesSameSession` + `runOnce_lockHeld_skipsTrigger` 前半（无锁时 process 被调）——`runOnce` 无锁调用 `process(session, sc.message())` 且三元组正确

### Implementation for User Story 1

- [x] T003 [US1] 实现 `AgentScheduler` 在 `oryxos-core/src/main/java/com/oryxos/core/AgentScheduler.java`：`@Component implements InitializingBean`；字段 `ThreadPoolTaskScheduler taskScheduler`、`ProfileRegistry profileRegistry`、`SessionManager sessionManager`、`AgentService agentService`、`ConcurrentMap<String, Lock> taskLocks = new ConcurrentHashMap<>()`；构造器收前四个；`afterPropertiesSet()` 调 `registerAll()`（空实现先跑绿 US1 单测，US4 再填注册逻辑）；`runOnce(Profile profile, ScheduleConfig sc)`：`sessionManager.getOrCreate("scheduler", "scheduler", profile.name())` 拿 Session，`agentService.process(session, sc.message())`（当前先不处理锁与异常，US2/US3 增量补）；`lockFor(String taskId)`：`taskLocks.computeIfAbsent(taskId, id -> new ReentrantLock())`

**Checkpoint**: US1 完成——`runOnce` 走 `process`、会话三元组固定

---

## Phase 4: User Story 2 - 重叠执行防护 (Priority: P1)

**Goal**: 上一次没跑完，本次触发直接跳过、不排队不堆积

**Independent Test**: `runOnce_lockHeld_skipsTrigger`——锁被占时 `process` 不被调用

### Implementation for User Story 2

- [x] T004 [US2] 在 `runOnce` 加锁：`Lock lock = lockFor(sc.getId()); if (!lock.tryLock()) { log.info("task {} still running, skip this trigger", sc.getId()); return; }`，锁体包住会话获取与 `process`，`finally { lock.unlock(); }` 先不写 catch（US3 补）

**Checkpoint**: US1 + US2 完成——重叠触发被跳过

---

## Phase 5: User Story 3 - 失败隔离 + 锁释放 (Priority: P1)

**Goal**: 一次任务失败不外抛、不崩调度器，锁在 finally 释放不留死锁，照常走审计路径

**Independent Test**: `runOnce_processThrows_notPropagatedAndLockReleased`——`process` 抛异常不外抛、二进宫 `times(2)` 证明锁释放

### Implementation for User Story 3

- [x] T005 [US3] 在 `runOnce` 加异常处理：`try { ... } catch (Exception e) { log.error("scheduled task {} failed", sc.getId(), e); } finally { lock.unlock(); }`——异常不外抛只记日志（`agentService.process` 内部完整 `llm_calls`/`tool_invocations` 审计路径照常走，定时触发不新增审计逻辑）

**Checkpoint**: US1 + US2 + US3 完成——失败隔离、锁释放、审计照常

---

## Phase 6: User Story 4 - cron 按声明的时区注册 (Priority: P2)

**Goal**: `registerAll` 扫所有 Profile 的 schedules，逐条 `CronTrigger(cron, zone)` 带时区动态注册

**Independent Test**: `registerAll_registersCronAndZone`——ArgumentCaptor 抓注册 Trigger，表达式等于配置 cron、`nextExecution` 落在配置 zone 预期时刻

### Implementation for User Story 4

- [x] T006 [US4] 补 `registerAll()`：遍历 `profileRegistry.listAll()`，对每个 `profile.schedules()` 的每个 `sc`，`taskScheduler.schedule(() -> runOnce(profile, sc), new CronTrigger(sc.getCron(), sc.getZoneId()))`——`getZoneId()` 由 `sc.zone()` 转 `ZoneId.of()`（`CronTrigger(String, ZoneId)` 构造器已核实存在；zone 为 null 回退系统默认时区用 `new CronTrigger(cron)` 或 `CronTrigger(cron, ZoneId.systemDefault())`，二选一在实现时定并注释理由）

**Checkpoint**: US1 + US2 + US3 + US4 完成——`AgentSchedulerTest` 四坑全绿

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 组合根接线、全量门禁、H4 不变量、交付物核对

- [x] T007 [P] 在 `oryxos-cli/src/main/java/com/oryxos/cli/CliSpringBootstrap.java` 追加 `@Bean ThreadPoolTaskScheduler taskScheduler()`（poolSize 默认 4）——`AgentScheduler` 的 `@Component` 由既有 `@ComponentScan("com.oryxos.core")` 扫入，上下文启动时经 `InitializingBean` 自动注册调度
- [x] T008 [P] 跑 `mvn clean verify` 全量门禁（含 P3C/SpotBugs/FindSecBugs/PMD），全绿；确认前序节（16-24）测试回归全绿（尤其 `AgentServiceTest`/`ProfileLoaderTest`/`SessionManager` 相关、`ToolExecutorTest`）
- [x] T009 [P] H4 六条不变量自查：①涉外 IO 首行过 `Sandbox.enforce`（本节不新增 IO 面，`AgentScheduler` 只调 `process`，24 节已接线的工具首行校验不受影响）②LLM 调用落 `llm_calls`、工具执行成败落 `tool_invocations`（定时触发走 `AgentService.process` 完整审计路径）③grep 无明文 key（本节不新增配置）④`session_id` 只在 `SessionManager` 内拼接（`AgentScheduler` 只传三元组不拼字符串）⑤无 Reactor/`CompletableFuture`/自建线程池（`ThreadPoolTaskScheduler` 是 Spring 调度线程，`runOnce` 同步阻塞）⑥无 Spring AI 自动工具执行路径
- [ ] T010 [P] 交付物存在性核对：`AgentScheduler.java`/`AgentSchedulerTest.java` 存在且非空；`lockFor`/`runOnce`/`registerAll`/`afterPropertiesSet` 在位；`CliSpringBootstrap` 有 `taskScheduler` bean；harness 四个守点测试逐个对号

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: 依赖 Setup，BLOCKS 所有 user story（harness 先行）
- **User Stories (Phase 3-6)**: 依赖 Foundational；同一文件顺序实施（US1→US2→US3→US4）
- **Polish (Phase 7)**: 依赖所有 user story

### User Story Dependencies

- **US1 (P1)**: 会话身份 + process 路径——地基，先做
- **US2 (P1)**: 依赖 US1（runOnce 已有 process 路径，加锁）
- **US3 (P1)**: 依赖 US2（已有 tryLock/finally，加 catch）
- **US4 (P2)**: 独立于 US1-3（只补 registerAll 注册逻辑）

### Parallel Opportunities

- Phase 1 全部 [P] 可并行
- T002（harness）是唯一地基，写毕后 US1-4 实现按顺序在同一文件增量
- T007-T010（Polish）可并行

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup → 2. Phase 2 harness（跑红）→ 3. Phase 3 US1（`runOnce` 走 process + 三元组）→ 4. **STOP 验证 US1 单测** → 5. US2 锁跳过 → 6. US3 失败隔离 → 7. US4 注册带时区 → 8. Polish（bootstrap bean + 全量门禁）

### Incremental Delivery

每个 user story 是一个行为增量：US1 到点触发跑通 → US2 防重叠 → US3 失败兜底 → US4 带时区注册。每个增量后 `AgentSchedulerTest` 对应守点转绿。

---

## Notes

- 四个 user story 共享同一 `AgentScheduler.java`，实现任务顺序执行（不标 [P]），测试任务 T002 一次性覆盖四坑。
- [P] 任务 = 不同文件、无依赖。
- harness 测试方法名英文 + `@DisplayName` 保留课件原文（如 `@DisplayName("上一次还没跑完_本次触发直接跳过")`）。
- 不改前序交付物（`Profile`/`ProfileRegistry`/`SessionManager`/`AgentService` 只读）。
