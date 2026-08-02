package com.oryxos.web.error;

import com.oryxos.web.api.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Spring Boot 错误转发兜底：把容器默认的 /error 页面统一换成标准 JSON。 覆盖 404（未匹配路径，静态资源与 Swagger UI 不受影响）与其它容器级错误，
 * 让所有错误响应都是统一的 {@link ErrorResponse} 结构。
 */
@Controller
public class OryxErrorController implements ErrorController {

  @RequestMapping("/error")
  public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
    Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status =
        statusAttr instanceof Integer
            ? (Integer) statusAttr
            : HttpStatus.INTERNAL_SERVER_ERROR.value();
    String path = String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

    String errorCode;
    String message;
    if (status == HttpStatus.NOT_FOUND.value()) {
      errorCode = "NOT_FOUND";
      message = "路径不存在: " + path;
    } else {
      errorCode = "HTTP_" + status;
      message = "请求处理失败";
    }
    ErrorResponse body = new ErrorResponse(errorCode, message, path, System.currentTimeMillis());
    return ResponseEntity.status(status).body(body);
  }
}
