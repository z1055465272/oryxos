package com.oryxos.core;

/**
 * 工具执行前的全局沙箱守卫契约（宪法 VI：涉外 IO 首行过沙箱校验）.
 *
 * <p>契约放 oryxos-core（依赖倒置），实现由 oryxos-tool 的沙箱模块提供（WhitelistSandbox 包装）。 与 {@link
 * ProviderService}/{@link SessionManager}/{@link MemoryService} 同构——core 不反向依赖下游模块。 {@link
 * ToolExecutor} 在执行任意工具前调用本守卫；返回 null 表示"该工具不在全局沙箱管辖范围"（放行， 后续仍由内置 Tool 自身的首行校验兜底）。
 */
public interface ToolSandboxGuard {

  /**
   * 校验一次工具调用是否在白名单内.
   *
   * @param toolCall 工具调用请求（含工具名与 JSON 参数）
   * @return 校验失败原因（非空则执行应被拦截），通过或不在管辖范围返回 null
   */
  String check(ToolCall toolCall);
}
