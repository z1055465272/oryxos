package com.oryxos.tool.sandbox;

/** 沙箱校验失败异常：携带可读失败原因（如 "命令不在白名单内: rm"），由 ToolExecutor 既有失败路径写入审计. */
public class SandboxViolationException extends RuntimeException {

  public SandboxViolationException(String message) {
    super(message);
  }
}
