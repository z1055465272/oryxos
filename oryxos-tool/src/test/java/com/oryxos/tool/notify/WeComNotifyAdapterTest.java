package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;

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

class WeComNotifyAdapterTest {

  private MockWebServer mockWebServer;
  private WeComNotifyAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    adapter = new WeComNotifyAdapter(RestClient.create());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("supports 命中企业微信域名")
  void supportsMatchesWecomDomain() {
    assertThat(adapter.supports("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc"))
        .isTrue();
    assertThat(adapter.supports("https://open.feishu.cn/hook")).isFalse();
    assertThat(adapter.supports("https://example.com/hook")).isFalse();
  }

  @Test
  @DisplayName("发送企业微信格式 msgtype.text.text.content")
  void sendPostsWecomJsonFormat() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).build());

    String url = mockWebServer.url("/cgi-bin/webhook/send").toString();
    adapter.send(new NotifyTarget("wecom", Map.of("url", url)), "hello");

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getHeaders().get("Content-Type")).contains("application/json");
    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"msgtype\"");
    assertThat(body).contains("\"text\"");
    assertThat(body).contains("hello");
  }
}
