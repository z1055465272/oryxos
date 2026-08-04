package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class DingTalkNotifyAdapterTest {

  private MockWebServer mockWebServer;
  private DingTalkNotifyAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    adapter = new DingTalkNotifyAdapter(RestClient.create());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("supports 命中钉钉域名")
  void supportsMatchesDingtalkDomain() {
    assertThat(adapter.supports("https://oapi.dingtalk.com/robot/send?access_token=abc")).isTrue();
    assertThat(adapter.supports("https://open.feishu.cn/hook")).isFalse();
    assertThat(adapter.supports("https://example.com/hook")).isFalse();
  }

  @Test
  @DisplayName("发送钉钉格式 msgtype.text.text.content")
  void sendPostsDingTalkJsonFormat() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).build());

    String url = mockWebServer.url("/robot/send").toString();
    adapter.send(new NotifyTarget("dingtalk", Map.of("url", url)), "hello");

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getHeaders().get("Content-Type")).contains("application/json");
    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"msgtype\"");
    assertThat(body).contains("\"text\"");
    assertThat(body).contains("hello");
  }

  @Test
  @DisplayName("配置 secret 时 URL 追加 timestamp 与 sign 加签参数")
  void sendWithSecretAddsTimestampAndSign() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).build());

    String baseUrl = mockWebServer.url("/robot/send").toString();
    String secret = "SEC123456";
    adapter.send(new NotifyTarget("dingtalk", Map.of("url", baseUrl, "secret", secret)), "hello");

    RecordedRequest request = mockWebServer.takeRequest();
    HttpUrl requestUrl = request.getRequestUrl();
    assertThat(requestUrl).isNotNull();
    String timestamp = requestUrl.queryParameter("timestamp");
    String sign = requestUrl.queryParameter("sign");
    assertThat(timestamp).isNotNull();
    assertThat(sign).isNotNull();

    // 请求 URL 携带的是 URL 编码后的签名，直接与编码形式比较
    String expected = expectEncodedSign(secret, timestamp);
    assertThat(sign).isEqualTo(expected);
  }

  @Test
  @DisplayName("无 secret 时 URL 不带加签参数")
  void sendWithoutSecretDoesNotAddSignatureParams() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).build());

    String baseUrl = mockWebServer.url("/robot/send").toString();
    adapter.send(new NotifyTarget("dingtalk", Map.of("url", baseUrl)), "hello");

    RecordedRequest request = mockWebServer.takeRequest();
    HttpUrl requestUrl = request.getRequestUrl();
    assertThat(requestUrl).isNotNull();
    assertThat(requestUrl.queryParameter("timestamp")).isNull();
    assertThat(requestUrl.queryParameter("sign")).isNull();
  }

  @Test
  @DisplayName("appendSignature 返回值含 timestamp 与 sign 且签名可通过重算校验")
  void appendSignatureProducesVerifiableSign() throws Exception {
    String baseUrl = "https://oapi.dingtalk.com/robot/send?access_token=abc";
    String secret = "SECsecret";
    String signed = DingTalkNotifyAdapter.appendSignature(baseUrl, secret);

    assertThat(signed).startsWith(baseUrl + "&");
    assertThat(signed).contains("timestamp=");
    assertThat(signed).contains("sign=");

    String timestamp =
        signed.substring(
            signed.indexOf("timestamp=") + "timestamp=".length(), signed.indexOf("&sign="));
    String sign = signed.substring(signed.indexOf("&sign=") + "&sign=".length());
    assertThat(sign).isEqualTo(expectEncodedSign(secret, timestamp));
  }

  /** Base64(HmacSHA256) 再按钉钉算法 URL 编码（URL 里实际携带的值）. */
  private static String expectEncodedSign(String secret, String timestamp) throws Exception {
    return Base64.getEncoder()
        .encodeToString(hmac(secret, timestamp))
        .replace("+", "%2B")
        .replace("/", "%2F")
        .replace("=", "%3D");
  }

  private static byte[] hmac(String secret, String timestamp) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return mac.doFinal((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8));
  }
}
