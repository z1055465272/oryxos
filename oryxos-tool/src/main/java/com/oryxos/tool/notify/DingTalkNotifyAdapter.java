package com.oryxos.tool.notify;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 钉钉群机器人通知适配器.
 *
 * <p>webhook 域名 {@code oapi.dingtalk.com}，请求体为 {@code {"msgtype":"text","text":{"content":"..."}}}.
 *
 * <p>支持「加签」安全模式：配置中提供 {@code secret} 时，按钉钉官方算法在 URL 上追加 {@code timestamp} 和 {@code sign}
 * 查询参数（HmacSHA256 + Base64 + URLEncode）；未配置 secret 时按普通机器人直接发送.
 */
@Component
@Order(3)
public class DingTalkNotifyAdapter implements NotifyChannelAdapter {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final RestClient restClient;

  public DingTalkNotifyAdapter(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public boolean supports(String url) {
    return url != null && url.contains("oapi.dingtalk.com");
  }

  @Override
  public void send(NotifyTarget target, String content) {
    String url = target.config().get("url");
    String secret = target.config().get("secret");
    if (secret != null && !secret.isBlank()) {
      url = appendSignature(url, secret);
    }
    restClient
        .post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("msgtype", "text", "text", Map.of("content", content)))
        .retrieve()
        .toBodilessEntity();
  }

  /** 钉钉加签算法：sign = Base64(HmacSHA256(secret, timestamp + "\n" + secret))， 再 URLEncode 后作为查询参数. */
  static String appendSignature(String url, String secret) {
    long timestamp = System.currentTimeMillis();
    String stringToSign = timestamp + "\n" + secret;
    byte[] digest;
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      digest = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to sign dingtalk webhook request", e);
    }
    String sign =
        URLEncoder.encode(Base64.getEncoder().encodeToString(digest), StandardCharsets.UTF_8);
    String separator = url.contains("?") ? "&" : "?";
    return url + separator + "timestamp=" + timestamp + "&sign=" + sign;
  }
}
