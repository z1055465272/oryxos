package com.oryxos.web.info;

import com.oryxos.web.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运行信息。核心阶段只暴露应用名与 Java 版本；Provider / 模块状态在能力一落地后补充. */
@Tag(name = "info", description = "运行信息")
@RestController
@RequestMapping("/api/v1/info")
public class InfoController {

  /** 运行信息端点：应用名、Java 运行时版本与虚拟线程支持状态. */
  @Operation(summary = "运行信息", description = "应用名、Java 运行时版本与虚拟线程支持状态")
  @GetMapping
  public ApiResponse<Map<String, Object>> info() {
    return ApiResponse.ok(
        Map.of(
            "application", "oryxos",
            "javaVersion", System.getProperty("java.version"),
            "virtualThreadsEnabled", isVirtualThreadsEnabled()));
  }

  private boolean isVirtualThreadsEnabled() {
    return Thread.currentThread().isVirtual();
  }
}
