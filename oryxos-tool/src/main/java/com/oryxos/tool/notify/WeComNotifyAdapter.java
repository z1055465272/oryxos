package com.oryxos.tool.notify;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 企业微信群机器人通知适配器.
 *
 * <p>webhook 域名 {@code qyapi.weixin.qq.com}，请求体为 {@code
 * {"msgtype":"text","text":{"content":"..."}}}.
 */
@Component
@Order(1)
public class WeComNotifyAdapter implements NotifyChannelAdapter {

  private final RestClient restClient;

  public WeComNotifyAdapter(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public boolean supports(String url) {
    return url != null && url.contains("qyapi.weixin.qq.com");
  }

  @Override
  public void send(NotifyTarget target, String content) {
    restClient
        .post()
        .uri(target.config().get("url"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("msgtype", "text", "text", Map.of("content", content)))
        .retrieve()
        .toBodilessEntity();
  }
}
