package com.oryxos.tool.notify;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 通知适配器路由：按 URL 特征选择具体渠道适配器发送.
 *
 * <p>注入容器中全部 {@link NotifyChannelAdapter}（Spring 按 {@code @Order} 排序）， 依次用 {@code supports(url)}
 * 匹配，第一个命中者执行；通用 webhook 兜底恒 true 且排最后， 因此正常配置下必有一个适配器可用。所有平台都不匹配（理论上 不会发生）时抛出明确异常，不静默吞掉.
 *
 * <p>本类不实现 {@link NotifyChannelAdapter}，避免注入 {@code List<NotifyChannelAdapter>} 时把自身也算进候选.
 */
@Component
public class NotifyChannelAdapterRouter {

  private final List<NotifyChannelAdapter> adapters;

  public NotifyChannelAdapterRouter(List<NotifyChannelAdapter> adapters) {
    this.adapters = List.copyOf(adapters);
  }

  /** 按 URL 特征选择适配器并发送；无适配器支持时抛异常. */
  public void send(NotifyTarget target, String content) {
    String url = target.config().get("url");
    for (NotifyChannelAdapter adapter : adapters) {
      if (adapter.supports(url)) {
        adapter.send(target, content);
        return;
      }
    }
    throw new IllegalArgumentException("No notify adapter supports url: " + url);
  }
}
