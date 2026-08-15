package com.oryxos.core;

import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 定时任务调度器（第三种触发源"钟推"）.
 *
 * <p>启动时扫所有 Profile 的 {@code schedules} 配置逐条动态注册进 Spring {@link ThreadPoolTaskScheduler}（cron +
 * 显式时区， 不靠编译期写死的 {@code @Scheduled}）；到点触发 {@link #runOnce}：按任务 id 拿进程内锁防重叠（上一次没跑完直接跳过），固定三元组 {@code
 * ("scheduler","scheduler",profileName)} 经 {@link SessionManager#getOrCreate} 拿会话，再调 {@link
 * AgentService#process} 走跟 CLI/Web 完全一样的入口——ReAct 循环、工具、审计一字不改。失败只记日志不外抛（finally 放锁），审计照常走 {@code
 * AgentService.process} 内部完整路径. 职责只到"拼一条消息交给统一入口"为止，不做任务状态持久化/运行控制（第 28 节）.
 */
@Component
public class AgentScheduler implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

  /** 定时触发的固定 channel 与 user：同一 Profile 历次触发复用同一 Session（会话身份约定）. */
  private static final String SCHEDULER_IDENTITY = "scheduler";

  private final ThreadPoolTaskScheduler taskScheduler;
  private final ProfileRegistry profileRegistry;
  private final SessionManager sessionManager;
  private final AgentService agentService;
  private final Map<String, Lock> taskLocks = new ConcurrentHashMap<>();

  /** 构建调度器（组合根注入四个既有组件）. */
  public AgentScheduler(
      ThreadPoolTaskScheduler taskScheduler,
      ProfileRegistry profileRegistry,
      SessionManager sessionManager,
      AgentService agentService) {
    this.taskScheduler = taskScheduler;
    this.profileRegistry = profileRegistry;
    this.sessionManager = sessionManager;
    this.agentService = agentService;
  }

  @Override
  public void afterPropertiesSet() {
    registerAll();
  }

  /** 扫所有 Profile 的 schedules 逐条注册进调度器（cron + 显式时区，坑一/坑四）——触发规则来自配置，不是编译期写死. */
  public void registerAll() {
    for (Profile profile : profileRegistry.listAll()) {
      for (Profile.ScheduleConfig sc : profile.schedules()) {
        taskScheduler.schedule(() -> runOnce(profile, sc), toCronTrigger(sc));
        log.info(
            "Registered scheduled task id={} profile={} cron={} zone={}",
            sc.id(),
            profile.name(),
            sc.cron(),
            sc.zone());
      }
    }
  }

  /** 单次触发：拿锁 → 拿会话 → process → 放锁；异常不外抛（坑三）. */
  public void runOnce(Profile profile, Profile.ScheduleConfig sc) {
    Lock lock = lockFor(sc.id());
    if (!lock.tryLock()) {
      log.info("task {} still running, skip this trigger", sc.id()); // 坑二：不排队、不堆积
      return;
    }
    try {
      // 会话身份固定三元组：同一 Profile 的历次定时触发复用同一 Session，历史靠 max_history_turns 截断兜底
      Session session =
          sessionManager.getOrCreate(SCHEDULER_IDENTITY, SCHEDULER_IDENTITY, profile.name());
      agentService.process(session, sc.message()); // 走跟 CLI/Web 完全一样的入口
    } catch (Exception e) {
      log.error("scheduled task {} failed", sc.id(), e); // 坑三：失败不能悄悄没声音，也不能带崩调度器
    } finally {
      lock.unlock(); // 不管成功失败，锁必须放掉，不然这个任务就永远"卡住"了
    }
  }

  /** 按任务 id 取进程内锁（防重叠，坑二）；harness 用它模拟"上一次还占着锁". */
  public Lock lockFor(String taskId) {
    return taskLocks.computeIfAbsent(taskId, id -> new ReentrantLock());
  }

  /** cron 表达式 + 显式时区组装 CronTrigger；zone 未声明时回退系统默认时区（配置即 Agent，时区显式为佳）. */
  private CronTrigger toCronTrigger(Profile.ScheduleConfig sc) {
    return sc.zone() == null
        ? new CronTrigger(sc.cron())
        : new CronTrigger(sc.cron(), ZoneId.of(sc.zone()));
  }
}
