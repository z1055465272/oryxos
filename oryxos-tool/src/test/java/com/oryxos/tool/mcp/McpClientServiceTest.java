package com.oryxos.tool.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.tool.registry.DefaultToolRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MCP 客户端服务测试：mock McpSyncClient，listTools 返回的工具被包装注册；连接失败只 WARN、其余工具照常注册、启动不炸. */
class McpClientServiceTest {

  private DefaultToolRegistry registry;
  private McpClientService service;

  @BeforeEach
  void setUp() {
    registry = new DefaultToolRegistry();
  }

  private static McpSchema.Tool mcpTool(String name) {
    return new McpSchema.Tool(name, name, "desc", Map.of("type", "object"), null, null, null);
  }

  @Test
  @DisplayName("listTools 返回的工具被包装注册进注册表")
  void listToolsWrapsAndRegistersEachTool() {
    McpSyncClient goodClient = mock(McpSyncClient.class);
    when(goodClient.listTools())
        .thenReturn(new McpSchema.ListToolsResult(List.of(mcpTool("good_mcp_tool")), null));

    McpServerConfig good = new McpServerConfig("good", "stdio", "echo", List.of(), Map.of());
    service = new McpClientService(List.of(good), registry, cfg -> goodClient);
    service.connectAll();

    assertTrue(registry.get("good_mcp_tool").isPresent());
    assertEquals("good_mcp_tool", registry.get("good_mcp_tool").orElseThrow().getName());
  }

  @Test
  @DisplayName("某个 MCP server 失联不能拖垮启动和其他工具")
  void serverFailureIsIsolatedNotFatal() {
    McpSyncClient goodClient = mock(McpSyncClient.class);
    when(goodClient.listTools())
        .thenReturn(new McpSchema.ListToolsResult(List.of(mcpTool("good_mcp_tool")), null));

    McpSyncClient badClient = mock(McpSyncClient.class);
    doThrow(new RuntimeException("connection refused")).when(badClient).listTools();

    McpServerConfig good = new McpServerConfig("good", "stdio", "echo", List.of(), Map.of());
    McpServerConfig bad = new McpServerConfig("bad", "stdio", "echo", List.of(), Map.of());
    service =
        new McpClientService(
            List.of(bad, good),
            registry,
            cfg -> cfg.name().equals("good") ? goodClient : badClient);

    // 不抛异常——外部依赖的可用性不是自己的可用性
    service.connectAll();

    assertTrue(registry.get("good_mcp_tool").isPresent(), "好的 server 照常注册");
    assertTrue(registry.get("bad_mcp_tool").isEmpty(), "坏的 server 没有注册");
  }

  @Test
  @DisplayName("client 构造异常同样被隔离，只跳过该 server")
  void clientConstructionFailureIsIsolated() {
    McpSyncClient goodClient = mock(McpSyncClient.class);
    when(goodClient.listTools())
        .thenReturn(new McpSchema.ListToolsResult(List.of(mcpTool("good_mcp_tool")), null));

    McpServerConfig good = new McpServerConfig("good", "stdio", "echo", List.of(), Map.of());
    McpServerConfig bad = new McpServerConfig("bad", "stdio", "echo", List.of(), Map.of());
    service =
        new McpClientService(
            List.of(good, bad),
            registry,
            cfg -> {
              if (cfg.name().equals("bad")) {
                throw new RuntimeException("启动命令不存在");
              }
              return goodClient;
            });

    service.connectAll();

    assertTrue(registry.get("good_mcp_tool").isPresent());
    assertEquals(1, registry.listAll().size());
  }

  @Test
  @DisplayName("无 server 配置时 connectAll 为空操作不报错")
  void emptyConfigConnectAllIsNoop() {
    service = new McpClientService(List.of(), registry, cfg -> mock(McpSyncClient.class));
    service.connectAll();
    assertTrue(registry.listAll().isEmpty());
  }
}
