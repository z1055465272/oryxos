package com.oryxos.web.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OryxOS 统一响应体。所有 REST 端点返回 {@code ApiResponse<T>}.
 *
 * @param code 业务码，0 表示成功
 * @param message 提示信息
 * @param data 业务数据
 * @param timestamp 服务端时间戳（epoch millis）
 */
@Schema(description = "统一响应体")
public record ApiResponse<T>(int code, String message, T data, long timestamp) {

  public static final int CODE_OK = 0;

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(CODE_OK, "ok", data, System.currentTimeMillis());
  }

  public static <T> ApiResponse<T> ok() {
    return ok(null);
  }

  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<>(code, message, null, System.currentTimeMillis());
  }
}
