package com.oryxos.tool.notify;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Webhook 通知适配器：通用 HTTP webhook 推送（核心阶段唯一实现）.
 *
 * <p>企业微信、飞书、钉钉的群机器人都提供 webhook 地址，核心阶段用一个通用适配器即可覆盖. 专用 API（签名算法、AccessToken 刷新）留给扩展阶段新增实现类.
 */
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

  private final RestClient restClient;

  public WebhookNotifyAdapter(RestClient restClient) {
    this.restClient = restClient;
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
