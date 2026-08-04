package com.oryxos.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Agent 的全字段运行配置 record，从 YAML 文件解析而来.
 *
 * <p>全部字段一次建全，后续各节按需取用对应字段.
 */
public record Profile(
    String name,
    String description,
    Identity identity,
    ProviderRef provider,
    List<String> tools,
    List<String> skills,
    List<String> mcpServers,
    List<ChannelRef> channels,
    List<NotifyChannelConfig> notifyChannels,
    List<ScheduleConfig> schedules,
    List<String> bootstrap,
    Settings settings) {

  /** 防御性拷贝，避免外部可变对象影响 record 的不变性. */
  public Profile {
    tools = tools != null ? List.copyOf(tools) : Collections.emptyList();
    skills = skills != null ? List.copyOf(skills) : Collections.emptyList();
    mcpServers = mcpServers != null ? List.copyOf(mcpServers) : Collections.emptyList();
    channels = channels != null ? List.copyOf(channels) : Collections.emptyList();
    notifyChannels = notifyChannels != null ? List.copyOf(notifyChannels) : Collections.emptyList();
    schedules = schedules != null ? List.copyOf(schedules) : Collections.emptyList();
    bootstrap = bootstrap != null ? List.copyOf(bootstrap) : Collections.emptyList();
  }

  /** Agent 身份设定. */
  public record Identity(String agentName, String prompt) {}

  /** Provider 选择引用. */
  public record ProviderRef(String name, String model, Double temperature) {}

  /** Channel 接入配置. */
  public record ChannelRef(String name, Map<String, String> config) {
    public ChannelRef {
      config = config != null ? Map.copyOf(config) : Collections.emptyMap();
    }
  }

  /** 定时调度配置. */
  public record ScheduleConfig(
      String id, String cron, String zone, String message, Map<String, String> config) {
    public ScheduleConfig {
      config = config != null ? Map.copyOf(config) : Collections.emptyMap();
    }
  }

  /** 运行时设置. */
  public record Settings(Integer maxIterations, Integer maxHistoryTurns) {}
}
