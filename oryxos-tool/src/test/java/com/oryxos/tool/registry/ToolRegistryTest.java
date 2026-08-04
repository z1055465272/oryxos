package com.oryxos.tool.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 工具注册表测试：三种来源的工具都以 OryxTool 身份注册进来；按 Profile.tools 过滤后子集精确匹配、不多不少. */
class ToolRegistryTest {

  private DefaultToolRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultToolRegistry();
  }

  /** 模拟内置工具：实现 OryxTool 的最小桩. */
  private OryxTool builtinTool(String name) {
    return new OryxTool() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getDescription() {
        return "builtin " + name;
      }

      @Override
      public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
      }

      @Override
      public ToolResult execute(String jsonInput) {
        return ToolResult.ok(name);
      }
    };
  }

  @Test
  @DisplayName("三种来源的工具都以 OryxTool 身份注册，按名称可查")
  void threeSourcesRegisterAsOryxTool() {
    OryxTool builtin = builtinTool("read_file");
    OryxTool plugin = builtinTool("plugin_tool"); // 模拟 @Tool 插件包装后的 OryxTool
    OryxTool mcp = builtinTool("mcp_tool"); // 模拟 McpToolAdapter 包装后的 OryxTool

    registry.register(builtin);
    registry.register(plugin);
    registry.register(mcp);

    assertTrue(registry.get("read_file").isPresent());
    assertTrue(registry.get("plugin_tool").isPresent());
    assertTrue(registry.get("mcp_tool").isPresent());
    assertEquals(3, registry.listAll().size());
  }

  @Test
  @DisplayName("按 Profile.tools 过滤后子集精确匹配，不多一个、不少一个")
  void filterByProfileToolsExactSubset() {
    registry.register(builtinTool("read_file"));
    registry.register(builtinTool("write_file"));
    registry.register(builtinTool("http_get"));
    registry.register(builtinTool("shell"));

    List<OryxTool> subset = registry.toolsFor(List.of("read_file", "http_get"));

    // 恰好等于声明列表：不多（没过滤干净）不少（过滤过头）
    assertEquals(2, subset.size(), "子集应恰好等于声明列表");
    assertEquals("read_file", subset.get(0).getName());
    assertEquals("http_get", subset.get(1).getName());
  }

  @Test
  @DisplayName("Profile.tools 声明了未注册工具时跳过，子集仍精确匹配已注册项")
  void filterSkipsUnregisteredDeclaredName() {
    registry.register(builtinTool("read_file"));

    List<OryxTool> subset = registry.toolsFor(List.of("read_file", "ghost_tool"));

    assertEquals(1, subset.size(), "未注册的声明项被跳过，不造成子集多出或空位");
    assertEquals("read_file", subset.get(0).getName());
  }

  @Test
  @DisplayName("未注册工具查不到，registered 名称不区分是否误注册")
  void unknownToolNotPresent() {
    assertFalse(registry.get("nope").isPresent());
    assertNotNull(registry.listAll());
  }

  @Test
  @DisplayName("重名注册以新替旧并覆盖")
  void duplicateRegisterOverwrites() {
    registry.register(builtinTool("read_file"));
    registry.register(
        new OryxTool() {
          @Override
          public String getName() {
            return "read_file";
          }

          @Override
          public String getDescription() {
            return "replacement";
          }

          @Override
          public String getInputSchema() {
            return "{}";
          }

          @Override
          public ToolResult execute(String jsonInput) {
            return ToolResult.ok("new");
          }
        });

    assertEquals(1, registry.listAll().size(), "重名注册应覆盖而非累积");
    assertEquals("replacement", registry.get("read_file").orElseThrow().getDescription());
  }

  @Test
  @DisplayName("MCP 来源工具的 schema 经序列化后以 String 承载，注册后仍可查")
  void mcpToolSchemaStringCarrier() {
    registry.register(builtinTool("mcp_x"));

    OryxTool tool = registry.get("mcp_x").orElseThrow();
    assertNotNull(tool.getInputSchema());
    assertTrue(tool.getInputSchema().startsWith("{"));
  }

  @Test
  @DisplayName("混合注册 @Tool 包装工具与 MCP 工具，按声明过滤子集精确匹配")
  void mixedRegistrationFilterExactSubset() {
    registry.register(builtinTool("read_file")); // 模拟 @Tool 包装后的内置工具
    registry.register(builtinTool("http_get"));
    registry.register(builtinTool("mcp_github")); // 模拟 McpToolAdapter 包装的 MCP 工具

    List<OryxTool> subset = registry.toolsFor(List.of("read_file", "http_get", "mcp_github"));

    assertEquals(3, subset.size(), "三来源混合注册后按声明过滤应精确命中全部三项");
    assertEquals("read_file", subset.get(0).getName());
    assertEquals("http_get", subset.get(1).getName());
    assertEquals("mcp_github", subset.get(2).getName());
  }
}
