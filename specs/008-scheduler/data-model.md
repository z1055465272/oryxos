# Data Model: 定时任务模块（第 25 节）

## 实体

### ScheduleConfig（定时任务定义，既有，19 节落地）

声明在 Profile 的 `schedules` 字段，是定时触发的**定义源**（本节只消费，不修改）。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | String | 是 | 任务唯一标识，同时作为进程内锁的 key |
| `cron` | String | 是 | cron 表达式，Spring `CronExpression` 标准解析 |
| `zone` | String | 否 | 显式时区（IANA，如 `Asia/Shanghai`）；为空回退系统默认时区 |
| `message` | String | 否 | 到点拼给 Agent 的消息（进 `agentService.process`） |
| `config` | Map | 否 | 附加配置（预留，本节不使用） |

**校验规则**（由 Spring 调度层承担，AgentScheduler 不重复解析）：cron 非法时 `new CronTrigger(...)` 抛 `IllegalArgumentException`；zone 非法时 `ZoneId.of(...)` 抛 `DateTimeException`。按课件"cron 触发本身是 Spring 的事"，非法配置在注册时由异常自然暴露（当前无全局注册失败兜底，属可接受行为——第 28 节补状态持久化时一并处理）。

### 任务锁（进程内，非持久化）

| 维度 | 说明 |
|------|------|
| key | `ScheduleConfig.id` |
| 载体 | `ConcurrentHashMap<String, ReentrantLock>` |
| 语义 | `tryLock()` 失败 = 上次未跑完 → 本次触发直接跳过（不排队、不堆积） |
| 生命周期 | 进程内存活；`finally` 释放，防永久卡死 |

## 状态流转（一次触发）

```
调度器到点 (CronTrigger)
  → runOnce(profile, sc)
  → lockFor(sc.id).tryLock()
      ├─ 失败 → 记 info 日志 "task {} still running, skip this trigger" → 结束（本次跳过）
      └─ 成功 → getOrCreate("scheduler", "scheduler", profile.name())
              → agentService.process(session, sc.message())   ← 完整审计路径（llm_calls/tool_invocations）
              → [异常] catch 记 error 日志 → finally 放锁 → 结束
```

## 会话身份约定

定时触发固定三元组 `("scheduler", "scheduler", profile.name())`（课件"会话身份固定"）。`session_id` 拼接只在 `JpaSessionManager` 内部一处（宪法/H4 不变量④），`AgentScheduler` 只传三元组、不拼字符串。同一 Profile 历次触发复用同一 Session，历史靠 `max_history_turns` 截断兜底。
