package com.oryxos.cli;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;

/**
 * 重命令：{@code oryxos gateway} 守护进程模式（同时挂多个 Channel）.
 *
 * <p>重命令才启动 Spring 上下文。多 Channel 守护后续节补；本节先起上下文验证"重命令启动 Spring". main 线程经 {@link Thread#join()}
 * 阻塞保持进程常驻.
 */
@Command(name = "gateway", description = "守护进程模式（多 Channel）", mixinStandardHelpOptions = true)
public class GatewayCommand implements Runnable {

  @Override
  public void run() {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(CliSpringBootstrap.class).web(WebApplicationType.NONE).run()) {
      System.out.println("OryxOS gateway 守护进程已启动（多 Channel 后续节接线）");
      Thread.currentThread().join(); // 保持进程常驻
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
