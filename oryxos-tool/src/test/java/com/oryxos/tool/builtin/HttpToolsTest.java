package com.oryxos.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oryxos.core.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.IOException;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** HTTP 工具测试：正常路径用 MockWebServer（本地假 HTTP，不算外网依赖）；越界用 mock Sandbox 命中即抛，断言请求未发出. */
class HttpToolsTest {

  private MockWebServer mockWebServer;
  private Sandbox sandbox;
  private HttpTools httpTools;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    sandbox = mock(Sandbox.class);
    httpTools = new HttpTools(sandbox, RestClient.create());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("http_get 取回响应体")
  void httpGetWithinAllowlistReturnsBody() throws Exception {
    mockWebServer.enqueue(
        new MockResponse.Builder().code(200).body("{\"weather\":\"sunny\"}").build());

    String url = mockWebServer.url("/beijing").toString();
    ToolResult result = httpTools.httpGet(url);

    assertTrue(result.success());
    assertNotNull(result.content());
    assertTrue(result.content().contains("sunny"));
  }

  @Test
  @DisplayName("http_get 命中白名单外域名被拦下，抛异常且请求未发出")
  void httpGetOutsideAllowlistIsBlocked() {
    String evilUrl = "https://evil.example.com/";
    doThrow(new RuntimeException("sandbox blocked"))
        .when(sandbox)
        .enforce(new SandboxAction(ActionType.HTTP_REQUEST, evilUrl));

    assertThrows(RuntimeException.class, () -> httpTools.httpGet(evilUrl));
  }

  @Test
  @DisplayName("http_get 先过校验再发请求")
  void httpGetEnforceBeforeRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).body("ok").build());

    String url = mockWebServer.url("/x").toString();
    httpTools.httpGet(url);

    verify(sandbox).enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
  }

  @Test
  @DisplayName("http_post 发出请求且 body 原样")
  void httpPostSendsBody() throws Exception {
    mockWebServer.enqueue(new MockResponse.Builder().code(200).body("{\"id\":1}").build());

    String url = mockWebServer.url("/submit").toString();
    ToolResult result = httpTools.httpPost(url, "{\"name\":\"test\"}");

    assertTrue(result.success());
    RecordedRequest request = mockWebServer.takeRequest();
    assertEquals("POST", request.getMethod());
    assertEquals("{\"name\":\"test\"}", request.getBody().readUtf8());
    assertNotNull(result.content());
  }

  @Test
  @DisplayName("http_post 命中白名单外域名被拦下，抛异常且请求未发出")
  void httpPostOutsideAllowlistIsBlocked() {
    String evilUrl = "https://evil.example.com/";
    doThrow(new RuntimeException("sandbox blocked"))
        .when(sandbox)
        .enforce(new SandboxAction(ActionType.HTTP_REQUEST, evilUrl));

    assertThrows(RuntimeException.class, () -> httpTools.httpPost(evilUrl, "{}"));
  }
}
