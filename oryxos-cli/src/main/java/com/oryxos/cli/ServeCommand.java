package com.oryxos.cli;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 重命令：{@code oryxos serve [--port]} 启动 HTTP API 服务.
 *
 * <p>重命令才启动 Spring 上下文。WebServer 本体（26 节）挂载到同一上下文；本节先起上下文验证"重命令启动 Spring" 与 JPA 扫描范围显式声明（课件坑
 * 4）。main 线程经 {@link Thread#join()} 阻塞保持服务常驻.
 */
@Command(name = "serve", description = "启动 HTTP API 服务", mixinStandardHelpOptions = true)
public class ServeCommand implements Runnable {

  @Option(
      names = "--port",
      defaultValue = "8080",
      description = "HTTP 端口（默认 8080，26 节 WebServer 使用）")
  int port;

  @Override
  public void run() {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(CliSpringBootstrap.class).web(WebApplicationType.NONE).run()) {
      System.out.println("OryxOS serve 已启动（WebServer 本体第 26 节接线），端口=" + port);
      Thread.currentThread().join(); // 保持进程常驻
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
