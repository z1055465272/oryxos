package com.oryxos.tool.notify;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 飞书群机器人通知适配器.
 *
 * <p>webhook 域名 {@code open.feishu.cn} 或 {@code open.larksuite.com}（国际版）， 请求体为 {@code
 * {"msg_type":"text","content":{"text":"..."}}}.
 */
@Component
@Order(2)
public class FeishuNotifyAdapter implements NotifyChannelAdapter {

  private final RestClient restClient;

  public FeishuNotifyAdapter(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public boolean supports(String url) {
    return url != null && (url.contains("open.feishu.cn") || url.contains("open.larksuite.com"));
  }

  @Override
  public void send(NotifyTarget target, String content) {
    restClient
        .post()
        .uri(target.config().get("url"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("msg_type", "text", "content", Map.of("text", content)))
        .retrieve()
        .toBodilessEntity();
  }
}
