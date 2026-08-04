package com.oryxos.tool.registry;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolResult;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.builtin.ShellTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 内置工具的 {@code @Tool} 注册接线：把标了 {@code @Tool} 注解的 Spring
 * Bean（FileTools/ShellTools/HttpTools/NotifyTools） 经 {@link ToolCallbacks#from} 生成
 * schema（name/description/inputSchema 三件套），包装成 {@code OryxTool} 注册进 ToolRegistry.
 *
 * <p>只用 Spring AI 的 {@code @Tool} schema 生成（宪法 II），执行仍由 ReActLoop + ToolExecutor 走 {@code
 * ToolExecutor.execute}，不引入 Spring AI 自动工具执行。Bean 初始化后自动注册，幂等可重复调用.
 */
@Component
public class BuiltinToolRegistration implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(BuiltinToolRegistration.class);

  private final DefaultToolRegistry registry;
  private final FileTools fileTools;
  private final ShellTools shellTools;
  private final HttpTools httpTools;
  private final NotifyTools notifyTools;

  /** DI constructor：注入注册表与全部内置 {@code @Tool} Bean. */
  @Autowired
  public BuiltinToolRegistration(
      DefaultToolRegistry registry,
      FileTools fileTools,
      ShellTools shellTools,
      HttpTools httpTools,
      NotifyTools notifyTools) {
    this.registry = registry;
    this.fileTools = fileTools;
    this.shellTools = shellTools;
    this.httpTools = httpTools;
    this.notifyTools = notifyTools;
  }

  /** 无参构造：供测试直接调用 {@link #registerAll}（不经过 Spring DI）. */
  public BuiltinToolRegistration() {
    this.registry = null;
    this.fileTools = null;
    this.shellTools = null;
    this.httpTools = null;
    this.notifyTools = null;
  }

  /** Spring 生命周期：Bean 初始化后注册全部内置工具. */
  @Override
  public void afterPropertiesSet() {
    registerAll(registry, fileTools, shellTools, httpTools, notifyTools);
  }

  /** 把全部内置 {@code @Tool} Bean 包装注册进注册表；幂等，可重复调用. */
  public void registerAll(
      DefaultToolRegistry registry,
      FileTools fileTools,
      ShellTools shellTools,
      HttpTools httpTools,
      NotifyTools notifyTools) {
    ToolCallback[] callbacks = ToolCallbacks.from(fileTools, shellTools, httpTools, notifyTools);
    for (ToolCallback callback : callbacks) {
      OryxTool tool = new ToolCallbackOryxTool(callback);
      registry.register(tool);
      log.info("Registered builtin tool: {}", tool.getName());
    }
  }

  /** 把 Spring AI 的 {@code ToolCallback}（内含 @Tool schema）适配成 OryxTool. */
  static class ToolCallbackOryxTool implements OryxTool {

    private final ToolCallback delegate;

    ToolCallbackOryxTool(ToolCallback delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getName() {
      return delegate.getToolDefinition().name();
    }

    @Override
    public String getDescription() {
      return delegate.getToolDefinition().description();
    }

    @Override
    public String getInputSchema() {
      return delegate.getToolDefinition().inputSchema();
    }

    @Override
    public ToolResult execute(String jsonInput) {
      String raw = delegate.call(jsonInput);
      return ToolResult.ok(raw);
    }
  }
}
