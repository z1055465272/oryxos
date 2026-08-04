package com.oryxos.tool.notify;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通用 HTTP webhook 通知适配器：兜底实现，处理所有未被平台适配器识别的 URL.
 *
 * <p>企业微信、飞书、钉钉的群机器人都提供 webhook 地址， 但各自请求体格式不同，由 {@link WeComNotifyAdapter}、{@link
 * FeishuNotifyAdapter}、{@link DingTalkNotifyAdapter} 分别承接； 本适配器只负责无特定平台特征的通用 webhook.
 */
@Component
@Order(100)
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

  private final RestClient restClient;

  public WebhookNotifyAdapter(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public boolean supports(String url) {
    return true;
  }

  @Override
  public void send(NotifyTarget target, String content) {
    String url = target.config().get("url");
    restClient
        .post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("content", content))
        .retrieve()
        .toBodilessEntity();
  }
}
