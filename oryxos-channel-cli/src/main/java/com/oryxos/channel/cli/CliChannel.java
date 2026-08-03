package com.oryxos.channel.cli;

import com.oryxos.core.AgentService;
import com.oryxos.core.Session;
import com.oryxos.core.SessionManager;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * CLI Channel：{@code oryxos chat} 命令的交互实现.
 *
 * <p>读 stdin 写 stdout，维护当前 Session，每行输入交给 {@link AgentService#process}，{@code /quit} 退出。
 * 只做"读输入→交引擎→打印结果"，不承担任何 Agent 智能（不想、不调模型、不执行工具）。
 *
 * <p><strong>session_id 拼接只发生在 {@link SessionManager} 内部</strong>——本类只传三元组（channel 固定 {@code
 * "cli"}），不自己拼字符串.
 */
public class CliChannel {

  private final AgentService agentService;
  private final SessionManager sessionManager;

  public CliChannel(AgentService agentService, SessionManager sessionManager) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
  }

  /** 进入交互循环，直到用户输入 {@code /quit}. */
  public void runInteractive(String channel, String user, String profileName) {
    Session session = sessionManager.getOrCreate(channel, user, profileName);
    PrintWriter out =
        new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
    Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
    while (true) {
      out.print("> ");
      out.flush();
      if (!in.hasNextLine()) {
        return; // stdin EOF：直接结束，等价于退出
      }
      String line = in.nextLine();
      if ("/quit".equals(line.trim())) {
        break; // 退出
      }
      String reply = agentService.process(session, line); // 交给引擎
      out.println(reply); // 打印结果
    }
  }
}
