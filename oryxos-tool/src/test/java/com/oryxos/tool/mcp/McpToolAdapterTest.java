package com.oryxos.tool.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MCP 工具适配器测试：mock McpSyncClient，listTools 返回的工具被包装注册；execute 转发参数原样、结果包成 ToolResult. */
class McpToolAdapterTest {

  private McpSyncClient client;
  private McpToolAdapter adapter;

  private static final McpSchema.Tool TOOL =
      new McpSchema.Tool(
          "github_search",
          "GitHub 搜索工具",
          "按关键词搜索 GitHub 仓库",
          Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string"))),
          null,
          null,
          null);

  @BeforeEach
  void setUp() {
    client = mock(McpSyncClient.class);
    adapter = new McpToolAdapter(client, TOOL);
  }

  @Test
  @DisplayName("getName/getDescription/getInputSchema 直接映射 MCP tools/list 返回")
  void nameDescriptionSchemaMapFromMcpTool() {
    assertEquals("github_search", adapter.getName());
    assertEquals("按关键词搜索 GitHub 仓库", adapter.getDescription());
    assertTrue(adapter.getInputSchema().contains("\"q\""));
    assertTrue(adapter.getInputSchema().contains("\"string\""));
  }

  @Test
  @DisplayName("execute 转发参数原样、结果包成 ToolResult")
  void executeForwardsArgumentsAndWrapsResult() {
    McpSchema.TextContent text = new McpSchema.TextContent("https://api.github.com/search");
    McpSchema.CallToolResult callResult =
        new McpSchema.CallToolResult(List.of(text), false, null, null);
    when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(callResult);

    ToolResult result = adapter.execute("{\"q\":\"spring ai\"}");

    assertTrue(result.success());
    assertTrue(result.content().contains("https://api.github.com/search"));
    verify(client).callTool(any(McpSchema.CallToolRequest.class));
  }

  @Test
  @DisplayName("CallToolResult isError=true 时返回可重试失败")
  void executeErrorMarksRetryableFailure() {
    McpSchema.TextContent text = new McpSchema.TextContent("boom");
    McpSchema.CallToolResult callResult =
        new McpSchema.CallToolResult(List.of(text), true, null, null);
    when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(callResult);

    ToolResult result = adapter.execute("{\"q\":\"x\"}");

    assertFalse(result.success());
    assertTrue(result.retryable());
    assertTrue(result.error().contains("boom"));
  }

  @Test
  @DisplayName("client 调用抛异常时返回可重试失败，不吞异常也不上抛")
  void executeClientExceptionMarksRetryableFailure() {
    when(client.callTool(any(McpSchema.CallToolRequest.class)))
        .thenThrow(new RuntimeException("connection refused"));

    ToolResult result = adapter.execute("{\"q\":\"x\"}");

    assertFalse(result.success());
    assertTrue(result.retryable());
    assertTrue(result.error().contains("connection refused"));
  }
}
