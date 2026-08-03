package com.oryxos.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;

/**
 * 轻命令：{@code oryxos session list} 列出会话摘要.
 *
 * <p>读 SQLite sessions 表（工作区 .oryxos/oryxos.db）。无表/无库时给提示不崩。轻命令不启动 Spring——直接用 JDBC 读
 * SQLite（sqlite-jdbc 在 classpath）.
 */
@Command(
    name = "session",
    description = "Session 管理",
    mixinStandardHelpOptions = true,
    subcommands = {SessionListCommand.ListCommand.class})
public class SessionListCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("用法: oryxos session list");
  }

  /** {@code oryxos session list} 列出会话. */
  @Command(name = "list", description = "列出会话", mixinStandardHelpOptions = true)
  public static class ListCommand implements Runnable {
    @Override
    public void run() {
      Path db = Path.of(".oryxos/oryxos.db");
      if (!Files.exists(db)) {
        System.out.println("（无会话数据，先运行 oryxos init 并完成一次对话）");
        return;
      }
      String url = "jdbc:sqlite:" + db.toAbsolutePath();
      try (var conn = java.sql.DriverManager.getConnection(url);
          var stmt = conn.createStatement();
          var rs =
              stmt.executeQuery(
                  "SELECT session_id, channel, user_id, profile_name, status"
                      + " FROM sessions ORDER BY last_active_at DESC")) {
        boolean any = false;
        while (rs.next()) {
          any = true;
          System.out.printf(
              "%s | channel=%s user=%s profile=%s status=%s%n",
              rs.getString("session_id"),
              rs.getString("channel"),
              rs.getString("user_id"),
              rs.getString("profile_name"),
              rs.getString("status"));
        }
        if (!any) {
          System.out.println("（sessions 表存在但为空）");
        }
      } catch (Exception e) {
        System.out.println("读取会话失败: " + e.getMessage());
      }
    }
  }
}
