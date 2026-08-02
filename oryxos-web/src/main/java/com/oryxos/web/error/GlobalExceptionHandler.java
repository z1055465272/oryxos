package com.oryxos.web.error;

import com.oryxos.web.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：异常 → 标准 JSON 错误（errorCode / message / path / timestamp）。 覆盖
 * 400（参数/请求体非法）、405（方法不支持）、500（内部错误）、503（外部依赖不可用）。 404（未匹配路径）与容器兜底错误由 {@link ErrorController} 统一处理。
 * 未列出的异常兜底为 500，避免把堆栈泄露给客户端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException e, HttpServletRequest req) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + " " + f.getDefaultMessage())
            .orElse("请求参数校验失败");
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, req);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(
      HttpMessageNotReadableException e, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求体格式非法", req);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
    return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", e.getMessage(), req);
  }

  /** 外部依赖（LLM / MCP / 存储）不可用时的统一 503。业务代码抛出 ServiceUnavailableException 即可。 */
  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleServiceUnavailable(
      ServiceUnavailableException e, HttpServletRequest req) {
    return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", e.getMessage(), req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
    log.error("unhandled exception on {}: {}", req.getRequestURI(), e.getMessage(), e);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误", req);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String errorCode, String message, HttpServletRequest req) {
    ErrorResponse body =
        new ErrorResponse(errorCode, message, req.getRequestURI(), System.currentTimeMillis());
    return ResponseEntity.status(status).body(body);
  }
}
