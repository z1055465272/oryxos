package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * 定时任务模块验收 harness（课件第 25 节）.
 *
 * <p>runOnce 拆成独立方法直接测（不真等时间）；cron 触发本身是 Spring 的事，只验注册参数传对了。四个守点： ①注册带 cron+时区（行为式） ②锁被占直接跳过
 * ③失败不外抛+锁 finally 释放（二进宫）④会话三元组固定.
 */
class AgentSchedulerTest {

  private final ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
  private final ProfileRegistry profileRegistry = mock(ProfileRegistry.class);
  private final SessionManager sessionManager = mock(SessionManager.class);
  private final AgentService agentService = mock(AgentService.class);
  private final AgentScheduler scheduler =
      new AgentScheduler(taskScheduler, profileRegistry, sessionManager, agentService);

  @Test
  @DisplayName("注册时CronTrigger带上了配置的cron和时区")
  void registerAll_registersCronAndZone() {
    Profile profile = profile("digest", scheduleConfig("task-1"));
    when(profileRegistry.listAll()).thenReturn(List.of(profile));
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

    scheduler.registerAll();

    verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
    CronTrigger trigger = (CronTrigger) triggerCaptor.getValue();
    assertThat(trigger.getExpression()).isEqualTo("0 0 9 * * *");
    // 时区行为式断言：UTC 参考 2026-08-15T08:00 = 上海 16:00（UTC+8），下一次 09:00 上海 = 次日 UTC 01:00。
    // 若时区被服务器默认顶替（非 Asia/Shanghai），nextExecution 会落在别的时刻——这正是坑四要拦的。
    Instant reference = Instant.parse("2026-08-15T08:00:00Z");
    SimpleTriggerContext ctx = new SimpleTriggerContext(Clock.fixed(reference, ZoneId.of("UTC")));
    Instant next = trigger.nextExecution(ctx);
    assertThat(next).isEqualTo(Instant.parse("2026-08-16T01:00:00Z"));
  }

  @Test
  @DisplayName("上一次还没跑完_本次触发直接跳过")
  void runOnce_lockHeld_skipsTrigger() throws Exception {
    // ReentrantLock 可重入：同线程 lock() 后再 tryLock() 会成功，模拟"上一次还占着锁"必须从另一线程持锁。
    // 真实场景里调度器触发来自 scheduler 线程池，与本触发线程天然不同，故跨线程占用即等价真实"上一次未跑完"。
    CountDownLatch acquired = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder =
        new Thread(
            () -> {
              Lock lock = scheduler.lockFor("task-1");
              lock.lock();
              acquired.countDown(); // 已持锁，主线程可以验证了
              try {
                release.await(); // 等主线程验证完跳过行为再放锁
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                lock.unlock(); // 谁持锁谁释放
              }
            });
    holder.start();
    acquired.await(); // 确保锁已被另一线程占用

    scheduler.runOnce(profile("digest", scheduleConfig("task-1")), scheduleConfig("task-1"));
    verify(agentService, never()).process(any(), any()); // 没有叠加执行

    release.countDown();
    holder.join();
  }

  @Test
  @DisplayName("任务抛异常_不外抛且锁必须被释放")
  void runOnce_processThrows_notPropagatedAndLockReleased() {
    when(agentService.process(any(), any())).thenThrow(new RuntimeException("boom"));

    assertDoesNotThrow(
        () ->
            scheduler.runOnce(
                profile("digest", scheduleConfig("task-1")), scheduleConfig("task-1")));

    scheduler.runOnce(
        profile("digest", scheduleConfig("task-1")), scheduleConfig("task-1")); // 再触发一次
    verify(agentService, times(2)).process(any(), any()); // 能进来——锁真的放了，没有永久卡死
  }

  @Test
  @DisplayName("会话三元组固定_两次触发拿到同一Session")
  void runOnce_fixedSessionTriple_reusesSameSession() {
    Session session = new Session("sched", "digest", "scheduler", "scheduler");
    when(sessionManager.getOrCreate("scheduler", "scheduler", "digest")).thenReturn(session);

    scheduler.runOnce(profile("digest", scheduleConfig("task-1")), scheduleConfig("task-1"));
    scheduler.runOnce(profile("digest", scheduleConfig("task-1")), scheduleConfig("task-1"));

    verify(sessionManager, times(2)).getOrCreate(eq("scheduler"), eq("scheduler"), eq("digest"));
    verify(agentService, times(2)).process(session, "msg");
  }

  private static Profile profile(String name, Profile.ScheduleConfig... schedules) {
    return new Profile(
        name,
        "desc",
        new Profile.Identity("agent", "role"),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(schedules),
        List.of(),
        new Profile.Settings(10, 20));
  }

  private static Profile.ScheduleConfig scheduleConfig(String id) {
    // Spring 6.2 CronExpression 需 6 段（含秒）；5 段 cron "0 9 * * *" 会被 parse 拒绝
    return new Profile.ScheduleConfig(id, "0 0 9 * * *", "Asia/Shanghai", "msg", Map.of());
  }
}
