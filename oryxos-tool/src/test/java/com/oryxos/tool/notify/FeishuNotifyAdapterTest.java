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

class FeishuNotifyAdapterTest {

  private MockWebServer mockWebServer;
  private FeishuNotifyAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    adapter = new FeishuNotifyAdapter(RestClient.create());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("supports 命中飞书域名（含国际版）")
  void supportsMatchesFeishuDomain() {
    assertThat(adapter.supports("https://open.feishu.cn/open-apis/bot/v2/hook/abc")).isTrue();
    assertThat(adapter.supports("https://open.larksuite.com/open-apis/bot/v2/hook/abc")).isTrue();
    assertThat(adapter.supports("https://qyapi.weixin.qq.com/hook")).isFalse();
    assertThat(adapter.supports("https://example.com/hook")).isFalse();
  }

  @Test
  @DisplayName("发送飞书格式 msg_type.content.text")
  void sendPostsFeishuJsonFormat() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).build());

    String url = mockWebServer.url("/open-apis/bot/v2/hook").toString();
    adapter.send(new NotifyTarget("feishu", Map.of("url", url)), "hello");

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getHeaders().get("Content-Type")).contains("application/json");
    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"msg_type\"");
    assertThat(body).contains("\"content\"");
    assertThat(body).contains("hello");
  }
}
