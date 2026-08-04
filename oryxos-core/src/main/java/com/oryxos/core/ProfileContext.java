package com.oryxos.core;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ThreadLocal 持有的当前 Agent Profile. 虚拟线程下每请求独占一个线程，天然不串.
 *
 * <p>AgentService 入口 set、出口 clear（finally 保证）——工具执行时能取到"当前是哪个 Agent" 的配置；处理抛异常也必须在 finally
 * 中清掉，否则下一个复用此线程的请求会拿到别人的 Profile.
 */
@Component
public class ProfileContext {

  private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

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

  /**
   * 从当前 Profile 解析通知渠道配置.
   *
   * <p>如果 channel 参数为 null 或 blank，返回第一个渠道（缺省行为）； 否则按 type 字段匹配。
   *
   * @param channel 渠道类型标识，可为 null（取第一个）
   * @return 匹配的 NotifyChannelConfig
   * @throws IllegalStateException 当前 Profile 未设置或 notifyChannels 为空
   * @throws IllegalArgumentException 指定 type 的渠道未找到
   */
  public NotifyChannelConfig resolveNotifyChannel(String channel) {
    Profile p = current();
    if (p == null) {
      throw new IllegalStateException("ProfileContext 未设置当前 Profile");
    }
    List<NotifyChannelConfig> channels = p.notifyChannels();
    if (channels.isEmpty()) {
      throw new IllegalStateException("当前 Profile 未配置 notify_channels");
    }
    if (channel == null || channel.isBlank()) {
      return channels.get(0);
    }
    for (NotifyChannelConfig c : channels) {
      if (c.type().equals(channel)) {
        return c;
      }
    }
    throw new IllegalArgumentException("未找到通知渠道: " + channel);
  }
}
