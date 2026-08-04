package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Map;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class WebhookNotifyAdapterTest {

  private MockWebServer mockWebServer;
  private WebhookNotifyAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    RestClient restClient = RestClient.create();
    adapter = new WebhookNotifyAdapter(restClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("发送后断言收到的 POST 请求 body 含 content 字段")
  void sendPostsJsonWithContentField() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).build());

    String webhookUrl = mockWebServer.url("/hook").toString();
    NotifyTarget target = new NotifyTarget("webhook", Map.of("url", webhookUrl));
    adapter.send(target, "hello world");

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getHeaders().get("Content-Type")).contains("application/json");
    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"content\"");
    assertThat(body).contains("hello world");
  }

  @Test
  @DisplayName("URL 来自 NotifyTarget.config 而不是硬编码")
  void targetUrlFromNotifyTargetConfigNotHardcoded() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).build());
    mockWebServer.enqueue(new MockResponse.Builder().code(200).build());

    String urlA = mockWebServer.url("/hook-a").toString();
    String urlB = mockWebServer.url("/hook-b").toString();

    NotifyTarget targetA = new NotifyTarget("webhook", Map.of("url", urlA));
    NotifyTarget targetB = new NotifyTarget("webhook", Map.of("url", urlB));

    adapter.send(targetA, "msg-a");
    adapter.send(targetB, "msg-b");

    RecordedRequest reqA = mockWebServer.takeRequest();
    RecordedRequest reqB = mockWebServer.takeRequest();
    assertThat(reqA.getRequestUrl().toString()).isEqualTo(urlA);
    assertThat(reqB.getRequestUrl().toString()).isEqualTo(urlB);
  }

  @Test
  @DisplayName("webhook 返回 5xx 时异常向上抛、不静默吞掉")
  void serverError5xxPropagatesException() {
    mockWebServer.enqueue(new MockResponse.Builder().code(500).build());

    String webhookUrl = mockWebServer.url("/hook").toString();
    NotifyTarget target = new NotifyTarget("webhook", Map.of("url", webhookUrl));

    assertThrows(Exception.class, () -> adapter.send(target, "test"));
  }
}
