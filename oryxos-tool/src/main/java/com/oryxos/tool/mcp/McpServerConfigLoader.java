package com.oryxos.tool.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * 读取并解析 {@code .oryxos/mcp_servers.yaml} 为 {@link McpServerConfig} 列表.
 *
 * <p>顶层键 {@code mcpServers:}；文件缺失/为空返回空列表（无 MCP server 配置是合法状态，不报错）。 env 值支持 {@code ${ENV_VAR}}
 * 占位，运行时从环境变量解析（与 Profile 凭证同口径，不明文落盘）.
 */
public class McpServerConfigLoader {

  private static final Logger log = LoggerFactory.getLogger(McpServerConfigLoader.class);

  private final Path configPath;

  public McpServerConfigLoader(Path configPath) {
    this.configPath = configPath;
  }

  /** 加载全部 MCP server 配置；文件缺失返回空列表. */
  @SuppressWarnings("unchecked")
  public List<McpServerConfig> loadAll() {
    if (!Files.exists(configPath)) {
      return Collections.emptyList();
    }
    String yamlText;
    try {
      yamlText = Files.readString(configPath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.warn("读取 mcp_servers.yaml 失败，按无 MCP server 处理: {}", e.getMessage());
      return Collections.emptyList();
    }
    if (yamlText.isBlank()) {
      return Collections.emptyList();
    }

    Yaml yaml = new Yaml();
    Object root = yaml.load(yamlText);
    if (!(root instanceof Map)) {
      log.warn(
          "mcp_servers.yaml 顶层应为映射，实际为 {}，按无 MCP server 处理", root == null ? "空" : root.getClass());
      return Collections.emptyList();
    }
    Object servers = ((Map<String, Object>) root).get("mcpServers");
    if (!(servers instanceof List)) {
      log.warn("mcp_servers.yaml 缺 mcpServers 列表，按无 MCP server 处理");
      return Collections.emptyList();
    }

    List<McpServerConfig> configs = new ArrayList<>();
    for (Object item : (List<Object>) servers) {
      if (!(item instanceof Map)) {
        log.warn("跳过非法 MCP server 配置项（非映射）: {}", item);
        continue;
      }
      McpServerConfig config = toConfig((Map<String, Object>) item);
      if (config == null) {
        continue;
      }
      configs.add(config);
    }
    return configs;
  }

  private McpServerConfig toConfig(Map<String, Object> raw) {
    String name = stringOf(raw, "name");
    String transport = stringOf(raw, "transport");
    String command = stringOf(raw, "command");
    if (name == null || name.isBlank()) {
      log.warn("跳过缺少 name 的 MCP server 配置");
      return null;
    }
    if (transport == null || transport.isBlank()) {
      log.warn("跳过缺少 transport 的 MCP server 配置: {}", name);
      return null;
    }
    if ("stdio".equals(transport) && (command == null || command.isBlank())) {
      log.warn("跳过 stdio 传输但缺少 command 的 MCP server 配置: {}", name);
      return null;
    }

    List<String> args = listOf(raw, "args");
    Map<String, String> env = envOf(raw, "env");
    return new McpServerConfig(name, transport, command, args, env);
  }

  private static String stringOf(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    return value instanceof String s ? s : null;
  }

  private static List<String> listOf(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (!(value instanceof List)) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (Object item : (List<?>) value) {
      if (item instanceof String s) {
        result.add(s);
      }
    }
    return result;
  }

  private static Map<String, String> envOf(Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (!(value instanceof Map)) {
      return Collections.emptyMap();
    }
    java.util.Map<String, Object> rawEnv = (java.util.Map<String, Object>) value;
    java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : rawEnv.entrySet()) {
      if (entry.getValue() instanceof String s) {
        result.put(entry.getKey(), resolveEnvPlaceholder(s));
      }
    }
    return result;
  }

  /** 解析 ${ENV_VAR} 占位：命中环境变量则替换，未命中保留原文（运行时缺变量时由调用方处理）. */
  private static String resolveEnvPlaceholder(String value) {
    if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
      return value;
    }
    String varName = value.substring(2, value.length() - 1);
    String resolved = System.getenv(varName);
    return resolved != null ? resolved : value;
  }
}
