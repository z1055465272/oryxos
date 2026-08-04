package com.oryxos.tool.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把 MCP server 暴露的工具适配成 {@code OryxTool}：三件套直接映射 tools/list 返回，执行时经 MCP 协议（JSON-RPC）转发给 对应
 * server，结果包装成 {@link ToolResult}.
 */
public class McpToolAdapter implements OryxTool {

  private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final McpSyncClient client;
  private final McpSchema.Tool tool;

  public McpToolAdapter(McpSyncClient client, McpSchema.Tool tool) {
    this.client = client;
    this.tool = tool;
  }

  @Override
  public String getName() {
    return tool.name();
  }

  @Override
  public String getDescription() {
    return tool.description();
  }

  @Override
  public String getInputSchema() {
    try {
      return MAPPER.writeValueAsString(tool.inputSchema());
    } catch (JsonProcessingException e) {
      log.warn("MCP 工具 {} 的 inputSchema 序列化失败，返回空 schema", tool.name(), e);
      return "{}";
    }
  }

  @Override
  public ToolResult execute(String jsonInput) {
    try {
      Map<String, Object> arguments = parseArguments(jsonInput);
      McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(tool.name(), arguments);
      McpSchema.CallToolResult result = client.callTool(request);
      String content = joinTextContent(result);
      if (Boolean.TRUE.equals(result.isError())) {
        return ToolResult.fail(content.isEmpty() ? "MCP 调用失败" : content, true);
      }
      return ToolResult.ok(content);
    } catch (RuntimeException e) {
      log.warn("MCP 工具 {} 调用失败", tool.name(), e);
      return ToolResult.fail("MCP 调用失败: " + e.getMessage(), true);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseArguments(String jsonInput) {
    if (jsonInput == null || jsonInput.isBlank()) {
      return Map.of();
    }
    try {
      Object parsed = MAPPER.readValue(jsonInput, Object.class);
      return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("工具参数不是合法 JSON: " + jsonInput, e);
    }
  }

  private static String joinTextContent(McpSchema.CallToolResult result) {
    if (result.content() == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (McpSchema.Content content : result.content()) {
      if (content instanceof McpSchema.TextContent text) {
        if (!sb.isEmpty()) {
          sb.append('\n');
        }
        sb.append(text.text());
      }
    }
    return sb.toString();
  }
}
