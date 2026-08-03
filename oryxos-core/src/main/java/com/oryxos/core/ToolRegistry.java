package com.oryxos.core;

import java.util.Collection;
import java.util.Optional;

/**
 * 工具注册表契约：统一管理所有 OryxTool（内置 / MCP / Plugin）. 第 20 节在 oryxos-tool 模块实现；本节 ToolExecutor 依赖此接口查找工具.
 */
public interface ToolRegistry {

  /** 按名称查找工具，未注册返回 empty. */
  Optional<OryxTool> get(String name);

  /** 列出全部已注册工具. */
  Collection<OryxTool> listAll();
}
