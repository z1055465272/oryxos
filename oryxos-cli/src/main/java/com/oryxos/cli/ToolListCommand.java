package com.oryxos.cli;

import picocli.CommandLine.Command;

/**
 * 轻命令：{@code oryxos tool list} 列出可用 Tool.
 *
 * <p>第 20 节 ToolRegistry 在 oryxos-tool 落地后，这里改从注册表读真实清单；本节先列出内置 Tool 的静态清单并注明.
 */
@Command(
    name = "tool",
    description = "Tool 管理",
    mixinStandardHelpOptions = true,
    subcommands = {ToolListCommand.ListCommand.class})
public class ToolListCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("用法: oryxos tool list");
  }

  /** {@code oryxos tool list} 列出可用 Tool. */
  @Command(name = "list", description = "列出可用 Tool", mixinStandardHelpOptions = true)
  public static class ListCommand implements Runnable {
    @Override
    public void run() {
      System.out.println("（内置 Tool 清单，第 20 节 ToolRegistry 落地后读取真实注册表）");
      String[] tools = {
        "read_file",
        "write_file",
        "list_dir",
        "shell",
        "http_get",
        "http_post",
        "save_memory",
        "recall_memory",
        "notify"
      };
      for (String tool : tools) {
        System.out.println("- " + tool);
      }
    }
  }
}
