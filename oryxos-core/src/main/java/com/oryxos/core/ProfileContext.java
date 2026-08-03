package com.oryxos.core;

/**
 * ThreadLocal 持有的当前 Agent Profile. 虚拟线程下每请求独占一个线程，天然不串.
 *
 * <p>AgentService 入口 set、出口 clear（finally 保证）——工具执行时能取到"当前是哪个 Agent" 的配置；处理抛异常也必须在 finally
 * 中清掉，否则下一个复用此线程的请求会拿到别人的 Profile.
 */
public final class ProfileContext {

  private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

  private ProfileContext() {}

  /** 设置当前线程的 Profile（仅 AgentService 入口调用）. */
  public static void set(Profile profile) {
    CURRENT.set(profile);
  }

  /** 获取当前线程的 Profile；未设置时为 null. */
  public static Profile current() {
    return CURRENT.get();
  }

  /** 清除当前线程的 Profile（AgentService finally 中保证）. */
  public static void clear() {
    CURRENT.remove();
  }
}
