package com.oryxos.tool.builtin;

import com.oryxos.core.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP 内置工具：http_get / http_post.
 *
 * <p>每个方法执行第一步先过 {@link Sandbox#enforce} 域名白名单校验（宪法 VI），校验不过抛异常拦下、请求根本不发出去.
 */
@Component
public class HttpTools {

  private final Sandbox sandbox;
  private final RestClient restClient;

  public HttpTools(Sandbox sandbox, RestClient restClient) {
    this.sandbox = sandbox;
    this.restClient = restClient;
  }

  /** 发起 HTTP GET 请求，返回响应体. */
  @Tool(name = "http_get", description = "发起一个 HTTP GET 请求，返回响应体")
  public ToolResult httpGet(String url) {
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
    try {
      String body = restClient.get().uri(url).retrieve().body(String.class);
      return ToolResult.ok(body != null ? body : "");
    } catch (RuntimeException e) {
      return ToolResult.fail("HTTP GET 失败: " + e.getMessage(), true);
    }
  }

  /** 发起 HTTP POST 请求，body 为 JSON，返回响应体. */
  @Tool(name = "http_post", description = "发起一个 HTTP POST 请求（JSON body），返回响应体")
  public ToolResult httpPost(String url, String body) {
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
    try {
      String response =
          restClient
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(String.class);
      return ToolResult.ok(response != null ? response : "");
    } catch (RuntimeException e) {
      return ToolResult.fail("HTTP POST 失败: " + e.getMessage(), true);
    }
  }
}
