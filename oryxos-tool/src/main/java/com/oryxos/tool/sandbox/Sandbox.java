package com.oryxos.tool.sandbox;

/**
 * 沙箱安全校验接口.
 *
 * <p>核心阶段唯一实现为 {@link WhitelistSandbox}（应用层白名单校验，劝阻级）。 文件操作、Shell、HTTP 请求都必须在执行前过这一层。
 */
public interface Sandbox {
  void enforce(SandboxAction action);
}
