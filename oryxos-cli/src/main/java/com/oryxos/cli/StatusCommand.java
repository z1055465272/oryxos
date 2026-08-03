package com.oryxos.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;

/** 轻命令：{@code oryxos status} 查看配置和运行状态. 纯文件操作、不启动 Spring. */
@Command(name = "status", description = "查看配置和运行状态", mixinStandardHelpOptions = true)
public class StatusCommand implements Runnable {

  private static final Path WORKSPACE_DIR = Path.of(".oryxos");

  @Override
  public void run() {
    if (!Files.exists(WORKSPACE_DIR)) {
      System.out.println("OryxOS 工作区不存在，先运行: oryxos init");
      return;
    }
    System.out.println("工作区: " + WORKSPACE_DIR.toAbsolutePath());
    System.out.println("数据库: " + (Files.exists(WORKSPACE_DIR.resolve("oryxos.db")) ? "存在" : "未创建"));
    System.out.println("Profile 目录: " + describeDir(WORKSPACE_DIR.resolve("profiles")));
    System.out.println("Agent 目录: " + describeDir(WORKSPACE_DIR.resolve("agents")));
  }

  private String describeDir(Path dir) {
    if (!Files.isDirectory(dir)) {
      return "不存在";
    }
    try (var files = Files.list(dir)) {
      long count = files.count();
      return "存在（" + count + " 项）";
    } catch (Exception e) {
      return "读取失败";
    }
  }
}
