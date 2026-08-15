# Research: 定时任务模块（第 25 节）

## Phase 0 研究结论

本节对本地依赖与既有代码的核实结论，全部已在实际环境验证。

### 1. Spring 调度 API 可用性（本地依赖核实）

- `org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler` 存在于 `spring-context:6.2.19`（`oryxos-core` 的既有 compile 依赖，已解包核实 class 存在）。
- `org.springframework.scheduling.support.CronTrigger` 存在于同一 jar，构造器 `(String)` / `(String, TimeZone)` / `(String, ZoneId)` 齐备（javap 核实）。
- `org.springframework.scheduling.TaskScheduler.schedule(Runnable, Trigger)` 签名确认。
- **结论：无新增第三方依赖**——`ThreadPoolTaskScheduler` + `CronTrigger` + `InitializingBean` 全在 spring-context，`ReentrantLock`/`ConcurrentHashMap`/`java.time` 全为 JDK 内置。

**Decision**: 用 Spring 自带 `ThreadPoolTaskScheduler` + `CronTrigger(String, ZoneId)` 动态注册。
**Rationale**: 与课件一致（"别自己写调度器"），本地依赖已核实存在，零新增依赖。
**Alternatives considered**: 自研调度器（违反课件原则）；`@Scheduled` 静态注解（cron 编译期写死，违反"配置即 Agent"）。

### 2. `@PostConstruct` 不可用 → `InitializingBean`

- `jakarta.annotation-api` 在 oryxos-core 依赖树中**仅 test scope**（`dependency:tree` 核实，经 spring-boot-starter-test 引入）。
- main 代码用 `@PostConstruct` 需新增 compile 依赖；本项目组合根 `CliSpringBootstrap` 已用 Spring 原生 `InitializingBean`（`memoryToolRegistration` bean）。

**Decision**: `AgentScheduler` 实现 `InitializingBean`，`afterPropertiesSet()` 调 `registerAll()`。
**Rationale**: Spring 原生接口，不新增第三方依赖，与既有组合根模式一致；`registerAll()`/`runOnce()` 方法名照课件保真，harness 直接调它们不依赖生命周期机制。
**Alternatives considered**: `@PostConstruct`（需新增 compile 依赖，否决）；`@EventListener(ApplicationReadyEvent.class)`（语义正确但引入额外时机概念，harness 无需，否决）。

### 3. 时区断言必须是行为式

- `CronTrigger` **无 zone getter**（javap 核实字段 `zoneId` 为 private final，无 getter），`equals()` 只比较 `expression` 不比较 zone（反编译核实）。
- 课件 harness 第一坑"注册时 `CronTrigger` 带上了配置的 cron 和时区"——cron 可用 `getExpression()` 断言，**时区必须行为式**：`SimpleTriggerContext(Clock.fixed(reference, ZoneId.of("UTC")))` 固定参考时刻，调 `trigger.nextExecution(ctx)`，断言返回 Instant 落在配置时区的预期时刻（例如配置 zone=Asia/Shanghai、cron 为每整点，固定 UTC 参考时刻 08:30，则 nextExecution 应为 16:00 UTC = 上海 00:00 次日... 以具体用例为准）。

**Decision**: 注册断言拆两段——表达式用 `((CronTrigger) trigger).getExpression()` 断言字符串相等；时区用 `nextExecution` 行为断言。
**Rationale**: 行为断言是唯一可靠方式（API 不暴露 zone），也恰好验证了"cron 按时区跑"这件事本身，比抓 zone 字段更贴近坑四语义。
**Alternatives considered**: 反射读 `zoneId` 字段（脆、违反不反射原则，否决）；断言 `CronTrigger.toString()`（只含表达式，不含 zone，不可行）。

### 4. `Profile.schedules` / `ScheduleConfig` 已落地（前序节核实）

- `Profile.java:51-56`：`ScheduleConfig(id, cron, zone, message, config)` record 已在（第 19 节随 Profile 落地，git log 核实 `7ce09b0`）。
- `ProfileLoader.parseSchedules` 已解析 `schedules` YAML 数组。
- 本节交付物"配置：Profile 新增 schedules 字段"已由前序完成，**零新增**；`AgentScheduler` 只消费 `profile.schedules()`。

### 5. 会话身份与既有契约

- `SessionManager.getOrCreate(channel, user, profileName)`（18 节接口）：`session_id` 拼接只在实现内部（`JpaSessionManager`）一处，CLI 传 `"cli"`、定时传 `"scheduler"`——本节按课件约定固定传 `("scheduler", "scheduler", profile.name())`。
- `AgentService.process(Session, String)`（17 节）：统一入口，内部 `ProfileContext.set/clear` + `ReActLoop` + `sessionManager.save`，定时触发与手动触发共用，不新增上下文管理。

### 6. 前序节交付物存在性（H0 依赖检查）

`AgentService` / `Profile` / `ProfileRegistry` / `SessionManager` / `Session` / `ProfileLoader` / `CliSpringBootstrap` 全部存在（find/grep 核实）。harness 依赖的 `ProfileRegistry.listAll()`（16 节）签名确认。
