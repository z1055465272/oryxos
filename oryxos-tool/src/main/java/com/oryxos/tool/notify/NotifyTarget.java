package com.oryxos.tool.notify;

import java.util.Collections;
import java.util.Map;

/**
 * 通知目标值对象：渠道类型 + 配置键值对.
 *
 * <p>由 NotifyTools 从 NotifyChannelConfig 转换而来，适配器按 channelType 自行解释 config.
 */
public record NotifyTarget(String channelType, Map<String, String> config) {

  public NotifyTarget {
    config = config != null ? Map.copyOf(config) : Collections.emptyMap();
  }
}
