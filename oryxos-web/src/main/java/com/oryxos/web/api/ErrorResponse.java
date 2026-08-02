package com.oryxos.web.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一错误响应体。全局异常处理器把它作为 HTTP body 返回。
 *
 * @param errorCode 稳定错误码（与 HTTP 状态无关，前端据此做分支）
 * @param message 人类可读的错误信息
 * @param path 出错请求的路径
 * @param timestamp 服务端时间戳（epoch millis）
 */
@Schema(description = "统一错误响应体")
public record ErrorResponse(String errorCode, String message, String path, long timestamp) {}
