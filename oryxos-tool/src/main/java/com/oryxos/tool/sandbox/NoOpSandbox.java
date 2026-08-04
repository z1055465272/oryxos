package com.oryxos.tool.sandbox;

import org.springframework.stereotype.Component;

/** No-op 占位实现：默认放行所有操作，23/24 节替换为真实白名单校验. */
@Component
public class NoOpSandbox implements Sandbox {

  @Override
  public void enforce(SandboxAction action) {
    // 核心阶段占位：默认放行，不做任何校验
  }
}
