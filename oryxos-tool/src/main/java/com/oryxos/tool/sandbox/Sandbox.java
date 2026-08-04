package com.oryxos.tool.sandbox;

/**
 * 沙箱安全校验接口.
 *
 * <p>核心阶段为 NoOp 占位实现（默认放行），23/24 节替换为真实白名单校验。 文件操作、Shell、HTTP 请求都必须在执行前过这一层。
 */
public interface Sandbox {
  void enforce(SandboxAction action);
}
