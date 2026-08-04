package com.oryxos.tool.registry;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ToolRegistry 接口的默认实现：把三种来源（内置 / {@code @Tool} 插件 / MCP）的工具统一成 {@code OryxTool} 注册进索引， ReAct
 * 循环按名查询、按 Profile.tools 过滤子集，不感知来源.
 *
 * <p>注册集中在启动期（内置工具装配 + {@code McpClientService.connectAll}），运行期只读；重名注册视为配置冲突，WARN 后以新替旧.
 */
public class DefaultToolRegistry implements ToolRegistry {

  private static final Logger log = LoggerFactory.getLogger(DefaultToolRegistry.class);

  private final ConcurrentMap<String, OryxTool> tools = new ConcurrentHashMap<>();

  /** 注册一个工具；name 已存在时 WARN 并覆盖（以新注册为准）. */
  public void register(OryxTool tool) {
    OryxTool previous = tools.put(tool.getName(), tool);
    if (previous != null) {
      log.warn("Tool 重名覆盖: {}（旧实现被新注册替换）", tool.getName());
    }
  }

  @Override
  public Optional<OryxTool> get(String name) {
    return Optional.ofNullable(tools.get(name));
  }

  @Override
  public Collection<OryxTool> listAll() {
    return List.copyOf(tools.values());
  }

  /**
   * 按 Profile.tools 声明列表过滤出子集——恰好等于声明列表：每个声明项最多一个工具，未注册的声明项跳过（WARN）不计入"多".
   *
   * <p>不多一个（未过滤干净）与不少一个（过滤过头）都是错，由 {@code ToolRegistryTest} 钉死.
   */
  public List<OryxTool> toolsFor(List<String> toolNames) {
    List<OryxTool> result = new ArrayList<>();
    for (String name : toolNames) {
      OryxTool tool = tools.get(name);
      if (tool != null) {
        result.add(tool);
      } else {
        log.warn("Profile.tools 声明了未注册的工具，已跳过: {}", name);
      }
    }
    return result;
  }
}
