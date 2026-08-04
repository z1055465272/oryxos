package com.oryxos.tool.mcp;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * mcp_servers.yaml 单项配置：一个外部 MCP server 的连接信息.
 *
 * @param name server 唯一名
 * @param transport 传输方式：stdio / sse
 * @param command 启动命令（stdio 用）
 * @param args 启动参数（可空）
 * @param env 环境变量（可空，支持 ${ENV} 占位）
 */
public record McpServerConfig(
    String name, String transport, String command, List<String> args, Map<String, String> env) {

  public McpServerConfig {
    args = args != null ? List.copyOf(args) : Collections.emptyList();
    env = env != null ? Map.copyOf(env) : Collections.emptyMap();
  }
}
