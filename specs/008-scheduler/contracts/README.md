# Contracts: 定时任务模块（第 25 节）

## 内部契约（AgentScheduler ↔ 既有组件）

本节不新增对外 API / 配置键 / 数据表（任务状态持久化与运行控制是第 28 节）。契约面是 `AgentScheduler` 消费的既有接口，全部只读不改：

| 组件 | 消费方法 | 契约要点 |
|------|---------|---------|
| `ProfileRegistry` | `listAll()` | 返回全部 Profile 的不可修改快照（16 节） |
| `Profile` | `schedules()` | `List<ScheduleConfig>`，空=无定时任务（19 节） |
| `SessionManager` | `getOrCreate(channel, user, profileName)` | 幂等获取/创建，session_id 拼接只在实现内部（18 节） |
| `AgentService` | `process(Session, String)` | 统一入口，内部含完整审计路径（17 节） |

## AgentScheduler 公开方法（新交付物，供 harness 与 Spring 生命周期调用）

| 方法 | 签名 | 说明 |
|------|------|------|
| `registerAll` | `void registerAll()` | 扫所有 Profile 的 schedules 逐条 `taskScheduler.schedule(runOnce, CronTrigger(cron, zone))` 注册 |
| `runOnce` | `void runOnce(Profile, ScheduleConfig)` | 单次触发：拿锁 → 拿会话 → `process` → 放锁；异常不外抛 |
| `lockFor` | `Lock lockFor(String taskId)` | 按任务 id 取进程内锁（`computeIfAbsent`），harness 用于模拟锁占用 |

## 生命周期

`AgentScheduler implements InitializingBean`——`afterPropertiesSet()` 调 `registerAll()`（Spring 上下文启动时自动执行）。理由见 plan.md Constitution Check 决策 1（`@PostConstruct` 的 jakarta.annotation 仅 test scope，用 Spring 原生接口替代）。

## 会话身份约定

定时触发固定三元组 `("scheduler", "scheduler", profile.name())`——channel 与 user 均固定为 `"scheduler"`，同一 Profile 历次触发复用同一 Session。
