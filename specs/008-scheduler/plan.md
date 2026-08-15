# Implementation Plan: 定时任务模块（第 25 节）

**Branch**: `025-lesson25-scheduler` | **Date**: 2026-08-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-scheduler/spec.md`

## Summary

交付定时任务能力——给 `AgentService` 加第三条触发路径（"钟推"）：`AgentScheduler` 启动时扫所有 Profile 的 `schedules` 字段（`Profile.ScheduleConfig` 已在第 19 节随 `Profile`/`ProfileLoader` 落地，本节只消费），逐条动态注册进 Spring `ThreadPoolTaskScheduler`（`CronTrigger` 带 cron + 时区）；到点触发 `runOnce`：按任务 id 拿进程内 `ReentrantLock`（`tryLock` 失败=上次没跑完直接跳过），固定三元组 `("scheduler", "scheduler", profileName)` 经 `SessionManager.getOrCreate` 拿 Session，再 `agentService.process(session, sc.message())` 走跟 CLI/Web 完全一样的入口，ReAct 循环/工具/审计一字不改；异常在 `runOnce` 内 catch 只记日志不外抛（finally 放锁），失败照常走 `AgentService.process` 内部完整审计。

**现状与课件差异（已在 H0 核实并记录）**：

1. **`ScheduleConfig`/`Profile.schedules` 已存在**（第 19 节随 `Profile`/`ProfileLoader` 落地，`Profile.java:51-56` 的 `ScheduleConfig(id, cron, zone, message, config)`），本节交付物"配置：Profile 新增 schedules 字段"已由前序完成，**零新增**。
2. **`@PostConstruct` → `InitializingBean`**：课件骨架用 `@PostConstruct`，但 `jakarta.annotation-api` 在 oryxos-core 依赖树里只有 test scope（已核实 dependency:tree），main 代码用 `@PostConstruct` 需新增 compile 依赖。本项目组合根已用 Spring 原生 `InitializingBean`（`CliSpringBootstrap.memoryToolRegistration`），改用 `InitializingBean.afterPropertiesSet()` 调 `registerAll()`——Spring 自带、与既有模式一致、不新增第三方依赖。`registerAll()`/`runOnce()` 两个方法名照课件保真，仅生命周期钩子换 Spring 原生接口（harness 不测生命周期机制，只直接调 `registerAll()`/`runOnce()`）。
3. **`lockFor(String taskId)` 需暴露**：harness 测试直接调 `scheduler.lockFor("task-1")` 模拟占用，故 `AgentScheduler` 需提供按任务 id 取锁的方法（`ConcurrentMap<String, Lock>` + `computeIfAbsent`）。
4. **时区断言是行为式**：`CronTrigger` 无 zone getter、`equals()` 只比表达式（已反编译核实），harness 第一坑"注册带上了 cron 和时区"的时区部分必须行为式验证——`SimpleTriggerContext(Clock.fixed(...))` 固定参考时刻 + `trigger.nextExecution()` 断言落在配置时区的预期时刻。
5. **`ThreadPoolTaskScheduler` 需显式 bean**：`@Component` 的 `AgentScheduler` 由既有 `@ComponentScan("com.oryxos.core")` 扫入，但 `ThreadPoolTaskScheduler` bean 需在组合根 `CliSpringBootstrap` 显式声明（Spring Boot 自动配置同名 `taskScheduler` 会因 `@ConditionalOnMissingBean(name="taskScheduler")` 退避，无双 bean 冲突）。

## Technical Context

**Language/Version**: Java 21（virtual thread 并发，本项目固定）

**Primary Dependencies**: Spring Boot 3.x（`spring-context` 提供 `ThreadPoolTaskScheduler`/`CronTrigger`/`InitializingBean`，**已核实存在于本地依赖 6.2.19**）；无新增第三方依赖（`java.util.concurrent.locks.ReentrantLock`/`ConcurrentHashMap`、`java.time` 全为 JDK 内置）。

**Storage**: 无持久化新增（任务状态持久化与执行历史是第 28 节的事，本节不做）。

**Testing**: JUnit 5 + Mockito（`spring-boot-starter-test`）。`AgentSchedulerTest` 直接 `new AgentScheduler(threadPoolTaskSchedulerMock, profileRegistryMock, sessionManagerMock, agentServiceMock)` 测四坑：注册参数（ArgumentCaptor 抓 `schedule(Runnable, Trigger)`，断言 `CronTrigger` 表达式 + 行为式时区）、重叠跳过（`lockFor` 占用后 `runOnce` 跳过）、失败隔离+锁释放（`process` 抛异常不外抛、二进宫 verify `times(2)`）、会话三元组固定。

**Target Platform**: 本地 CLI / 服务器（`oryxos-chat` / `serve` / `gateway` 共用 `CliSpringBootstrap` 组合根；`@Component` 的 `AgentScheduler` 随上下文启动即注册调度，chat 模式下定时任务同样到点触发——核心阶段可接受，第 28 节补运行控制）

**Project Type**: Java 多模块 Maven 单体

**Performance Goals**: 每次触发一次 `tryLock` + 一次会话获取，常数级开销可忽略

**Constraints**: `AgentScheduler` 落 `oryxos-core`（`com.oryxos.core`，与 `Profile`/`ProfileRegistry`/`SessionManager`/`AgentService` 同模块，不产生跨模块新依赖）；`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）

**Scale/Scope**: 单实例，进程内本地锁防重叠；分布式协调（选主/分布式锁/租约）、失败重试/告警、任务持久化与运行控制全部放扩展阶段（第 28 节及以后）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 |
|------|------|
| I. 自实现 ReAct Loop | 不触碰 `ReActLoop`；`AgentScheduler` 只调 `AgentService.process`（既有统一入口）。通过 |
| II. Spring AI 只用两件事 | 不涉及（定时不用 `@Tool`、不引入自动工具执行）。通过 |
| III. Provider 显式映射 | 不涉及。通过 |
| IV. 目录=Agent / Skill 加载 | 不涉及。通过 |
| V. 审计 Day One | `runOnce` 调 `AgentService.process`，内部走 `ReActLoop`/`ProviderService` 完整 `llm_calls`/`tool_invocations` 审计路径，与手动触发无差异；失败照样记账。通过 |
| VI. Sandbox | 不涉及（定时只拼消息交给入口，不新增 IO 面）。通过 |
| VII. 同步执行 | 全部同步阻塞；`ThreadPoolTaskScheduler` 的触发线程内同步跑 `runOnce`，无 Reactor/CompletableFuture。通过 |
| VIII. Tool 模块三合一 | 不涉及（定时模块归 core，不拆 Tool 模块）。通过 |

**声明本节的强制设计决策**（宪法 v1.1.0"新建/改名必须在 plan 里声明理由"）：

1. **生命周期钩子用 `InitializingBean` 替代课件 `@PostConstruct`**。理由：`jakarta.annotation-api` 在 oryxos-core 仅有 test scope，main 代码用 `@PostConstruct` 需新增 compile 依赖，违反"无新增第三方依赖"；`InitializingBean` 是 Spring 原生接口、本项目组合根已用（`CliSpringBootstrap.memoryToolRegistration`），`afterPropertiesSet()` 内部调 `registerAll()`，方法名与行为对 harness 完全保真（harness 直接调 `registerAll()`/`runOnce()`，不依赖生命周期机制）。
2. **`lockFor(String taskId)` 作为公开方法暴露**。理由：harness 测试要"模拟上一次还占着锁"，必须能拿到某任务 id 的锁（`scheduler.lockFor("task-1")`）。这是课件验收 harness 隐含要求的接口，非自作主张新增。
3. **`ThreadPoolTaskScheduler` bean 在 `CliSpringBootstrap` 显式声明**（`@Bean ThreadPoolTaskScheduler taskScheduler()`，poolSize 默认 4）。理由：`AgentScheduler` 以 `@Component` 注入具体类 `ThreadPoolTaskScheduler`，需上下文提供该 bean；显式声明符合本项目"组合根显式装配"的一贯风格，Spring Boot 自动配置的同名 bean 会因 `@ConditionalOnMissingBean(name="taskScheduler")` 自动退避，无冲突。

**复杂性说明**：无跨模块新依赖。核心改动全在 `oryxos-core`（新增 `AgentScheduler` + 测试），组合根 `CliSpringBootstrap` 追加一个 `ThreadPoolTaskScheduler` bean。不触碰任何前序交付物。

## Project Structure

### Documentation (this feature)

```text
specs/008-scheduler/
├── plan.md              # This file
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
├── contracts/           # Phase 1 输出
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成）
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── Profile.java                 # 已有（19节）：含 ScheduleConfig record，只读不动
├── ProfileRegistry.java         # 已有（16节）：listAll() 被 AgentScheduler 消费，只读不动
├── SessionManager.java          # 已有（18节）：getOrCreate 三元组被消费，只读不动
├── AgentService.java            # 已有（17节）：process(session, msg) 被消费，只读不动
└── AgentScheduler.java          # 新增：@Component，InitializingBean.afterPropertiesSet→registerAll()，runOnce(profile, sc)

oryxos-core/src/test/java/com/oryxos/core/
└── AgentSchedulerTest.java      # 新增：四坑全覆盖（harness 先行）

oryxos-cli/src/main/java/com/oryxos/cli/
└── CliSpringBootstrap.java      # 修改：新增 @Bean ThreadPoolTaskScheduler taskScheduler()
```

> 注：课件骨架里 `AgentScheduler` 的字段是 `ThreadPoolTaskScheduler`、`ProfileRegistry`、`AgentService`、`taskLocks`；但 `runOnce` 体用了 `sessionManager.getOrCreate(...)`——故构造器还需 `SessionManager`（课件骨架简写，实现按 runOnce 实际需要补全）。

**Structure Decision**: `AgentScheduler` 放 `oryxos-core`（与 `Profile`/`ProfileRegistry`/`SessionManager`/`AgentService` 同模块，符合"模块落位：AgentScheduler/ScheduleConfig→oryxos-core"），`@Component` 由既有 `@ComponentScan("com.oryxos.core")` 扫入；组合根只补一个 `ThreadPoolTaskScheduler` bean。

## Complexity Tracking

> 无宪法违规。上述三个决策均在 Constitution Check 声明理由，不属于违规。唯一触碰的前序文件是 `CliSpringBootstrap`（组合根，追加一个 bean 方法，不改既有 bean 的任何签名）。

## Phase 0 / Phase 1 artifacts

见 `research.md`、`data-model.md`、`contracts/`、`quickstart.md`。
