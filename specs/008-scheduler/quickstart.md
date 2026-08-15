# Quickstart: 定时任务模块（第 25 节）

本文件是可运行的验收指引。实现细节见 `plan.md` 与 `tasks.md`，契约见 `contracts/README.md`。

## 自动化验收（harness）

```bash
# 只跑本节测试（AgentSchedulerTest）
export JAVA_HOME='D:\envconfig\JDK\jdk-21.0.11'
cmd //c "D:\envconfig\apache-maven-3.9.16\bin\mvn.cmd -pl oryxos-core test -Dtest=AgentSchedulerTest"
# 预期：全部通过（四坑全覆盖）
```

覆盖的四个守点（课件"验收 harness"）：

1. **注册 cron + 时区**：`ArgumentCaptor<Trigger>` 抓 `schedule(Runnable, Trigger)` 注册参数，断言 `CronTrigger.getExpression()` 等于配置 cron；时区行为式断言（`nextExecution` 落在配置时区预期时刻）。
2. **重叠跳过**：`lockFor("task-1")` 占用后调 `runOnce`，断言 `agentService` 未被调用（不叠加执行）。
3. **失败隔离 + 锁释放**：`process` 抛异常，`assertDoesNotThrow(runOnce)`；再触发一次，`verify(agentService, times(2))`——证明锁已释放、没卡死。
4. **会话三元组固定**：两次触发断言 `getOrCreate("scheduler", "scheduler", profileName)` 拿到同一 Session。

## 全量门禁

```bash
export JAVA_HOME='D:\envconfig\JDK\jdk-21.0.11'
cmd //c "D:\envconfig\apache-maven-3.9.16\bin\mvn.cmd clean verify"
# 预期：BUILD SUCCESS（含 P3C/SpotBugs/FindSecBugs/PMD）
```

## 人工验证项（harness 判不了，需真人确认）

1. **真实到点触发一次**：在 `.oryxos/profiles/<profile>.yaml` 给某 Profile 加 `schedules`（如每分钟触发，指向一个真实 Provider），启动 `oryxos serve`/`gateway`，等到点看日志出现定时触发、`llm_calls`/`tool_invocations` 有账。
2. **配置驱动体感**：改 cron 表达式重启，验证按新时间跑（不用重新编译）。
3. **端到端预演**：完整走一遍"到点自动触发 → 跑完 ReAct 循环 → 留下审计记录"，为 31 节两个定时 Demo 踩实地基。

## 配置示例（.oryxos/profiles/<profile>.yaml）

```yaml
schedules:
  - id: daily-pr-digest
    cron: "0 9 * * *"
    zone: "Asia/Shanghai"
    message: "请汇总昨天合并的 PR 并生成评审摘要"
```
