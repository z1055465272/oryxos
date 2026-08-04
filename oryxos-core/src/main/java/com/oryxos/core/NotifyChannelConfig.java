package com.oryxos.core;

/**
 * Agent 通知渠道配置（Profile.notifyChannels 列表的单项）.
 *
 * <p>type 同时作为 {@code notify(channel)} 调用的匹配键；url 支持 {@code ${ENV_VAR}} 占位符， 由 {@link
 * ProfileLoader#resolveEnvVars(String)} 在加载时解析.
 */
public record NotifyChannelConfig(String type, String url) {

  /** Compact constructor validates required fields. */
  public NotifyChannelConfig {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("notify channel type must not be blank");
    }
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("notify channel url must not be blank");
    }
  }
}
