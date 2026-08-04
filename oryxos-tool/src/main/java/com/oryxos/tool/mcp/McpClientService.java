package com.oryxos.tool.mcp;

import com.oryxos.tool.registry.DefaultToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * MCP Client 服务：启动时连接所有配置的 MCP server，调 tools/list 拿工具列表，把每个工具包装成 {@link McpToolAdapter} 注册进
 * ToolRegistry.
 *
 * <p>外部依赖失联不拖垮自身启动：单个 server 连接失败或 listTools 失败只 WARN 并跳过该 server 的工具，其余 server 照常注册，
 * 整体不抛异常。同步客户端（宪法 VII），stdio / sse 两种 transport 按配置路由.
 */
@Component
public class McpClientService implements InitializingBean, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

  private final List<McpServerConfig> configs;
  private final DefaultToolRegistry toolRegistry;
  private final Function<McpServerConfig, McpSyncClient> clientFactory;
  private final ConcurrentMap<String, McpSyncClient> clients = new ConcurrentHashMap<>();

  /** Spring 装配路径：读工作区 mcp_servers.yaml，client 工厂用默认同步连接. */
  @Autowired
  public McpClientService(McpServerConfigLoader configLoader, DefaultToolRegistry toolRegistry) {
    this(configLoader.loadAll(), toolRegistry, McpClientService::connectSync);
  }

  /** 测试注入路径：外部提供 client 工厂（可 mock），便于隔离验证. */
  McpClientService(
      List<McpServerConfig> configs,
      DefaultToolRegistry toolRegistry,
      Function<McpServerConfig, McpSyncClient> clientFactory) {
    this.configs = List.copyOf(configs);
    this.toolRegistry = toolRegistry;
    this.clientFactory = clientFactory;
  }

  /** Spring 生命周期：Bean 初始化后连接全部 MCP server 并注册其工具. */
  @Override
  public void afterPropertiesSet() {
    connectAll();
  }

  /** Spring 生命周期：容器关闭时优雅断开全部 MCP client. */
  @Override
  public void destroy() {
    disconnectAll();
  }

  /** 连接全部配置的 MCP server 并注册其工具；逐个隔离，任何单点失败都不影响整体. */
  public void connectAll() {
    for (McpServerConfig cfg : configs) {
      try {
        McpSyncClient client = clientFactory.apply(cfg);
        List<McpSchema.Tool> tools = client.listTools().tools();
        for (McpSchema.Tool tool : tools) {
          toolRegistry.register(new McpToolAdapter(client, tool));
          log.info("MCP server {} 注册工具: {}", cfg.name(), tool.name());
        }
        clients.put(cfg.name(), client);
      } catch (Exception e) {
        log.warn("MCP server {} 连接失败，跳过它的工具: {}", cfg.name(), e.getMessage());
      }
    }
  }

  /** 关闭全部已连接的 MCP client（优雅断开），单个失败只 WARN 不影响其余. */
  public void disconnectAll() {
    for (McpSyncClient client : clients.values()) {
      try {
        client.closeGracefully();
      } catch (Exception e) {
        log.warn("关闭 MCP client 失败: {}", e.getMessage());
      }
    }
    clients.clear();
  }

  /** 默认 client 工厂：按 transport 路由到 stdio / sse 同步连接. */
  private static McpSyncClient connectSync(McpServerConfig cfg) {
    McpClientTransport transport = buildTransport(cfg);
    return McpClient.sync(transport).build();
  }

  private static McpClientTransport buildTransport(McpServerConfig cfg) {
    if ("sse".equals(cfg.transport())) {
      return HttpClientSseClientTransport.builder(cfg.command()).build();
    }
    ServerParameters.Builder params = ServerParameters.builder(cfg.command());
    if (!cfg.args().isEmpty()) {
      params.args(cfg.args());
    }
    params.env(cfg.env());
    return new StdioClientTransport(params.build(), defaultJsonMapper());
  }

  /** 经 ServiceLoader 取 MCP SDK 的默认 JSON mapper（Jackson 实现），无实现时回退 null（由 SDK 内部兜底）. */
  private static McpJsonMapper defaultJsonMapper() {
    for (McpJsonMapperSupplier supplier : ServiceLoader.load(McpJsonMapperSupplier.class)) {
      return supplier.get();
    }
    return null;
  }
}
