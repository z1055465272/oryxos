package com.oryxos.cli;

import com.oryxos.channel.cli.CliChannel;
import com.oryxos.core.AgentService;
import com.oryxos.core.SessionManager;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 重命令：{@code oryxos chat [--profile <name>]} 在终端里和 Agent 交互式对话.
 *
 * <p>重命令才启动 Spring 上下文（课件第二/第四点）；CLI 只做入口——读 stdin 交引擎、打印结果， session_id 拼接由 SessionManager
 * 内部完成，这里只传三元组（channel 固定 {@code "cli"}）.
 */
@Command(name = "chat", description = "在终端里和 Agent 交互式对话", mixinStandardHelpOptions = true)
public class ChatCommand implements Runnable {

  @Option(names = "--profile", defaultValue = "default", description = "使用的 Profile 名（默认 default）")
  String profileName;

  @Override
  public void run() {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(CliSpringBootstrap.class).web(WebApplicationType.NONE).run()) {
      AgentService agentService = context.getBean(AgentService.class);
      SessionManager sessionManager = context.getBean(SessionManager.class);
      new CliChannel(agentService, sessionManager)
          .runInteractive("cli", currentUser(), profileName);
    }
  }

  /** CLI 用户身份：取当前系统用户名，缺省回退 local. */
  private String currentUser() {
    String userName = System.getProperty("user.name");
    return userName != null && !userName.isBlank() ? userName : "local";
  }
}
