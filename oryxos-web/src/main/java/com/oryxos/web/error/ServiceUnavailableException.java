package com.oryxos.web.error;

/** 外部依赖不可用（LLM / MCP / 存储），由 GlobalExceptionHandler 映射为 HTTP 503。 */
public class ServiceUnavailableException extends RuntimeException {

  public ServiceUnavailableException(String message) {
    super(message);
  }

  public ServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
