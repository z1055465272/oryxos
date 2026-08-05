package com.oryxos.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

/**
 * 轻命令：{@code oryxos init} 初始化一个 OryxOS 工程.
 *
 * <p>创建 {@code .oryxos/} 工作区骨架（TechnicalSolution §8.1）。纯文件操作、不启动 Spring，秒回。 已存在则幂等跳过（不覆盖用户已有内容）。
 */
@Command(name = "init", description = "初始化一个 OryxOS 工程", mixinStandardHelpOptions = true)
public class InitCommand implements Runnable {

  private static final Path WORKSPACE_DIR = Path.of(".oryxos");

  @Override
  public void run() {
    try {
      createWorkspace();
      System.out.println("OryxOS 工程已初始化: " + WORKSPACE_DIR.toAbsolutePath());
    } catch (IOException e) {
      System.err.println("初始化失败: " + e.getMessage());
      System.exit(ExitCode.SOFTWARE);
    }
  }

  /** 创建工作区目录与默认模板；已存在则跳过不覆盖. */
  void createWorkspace() throws IOException {
    Files.createDirectories(WORKSPACE_DIR);
    for (String sub : new String[] {"agents", "skills", "memory", "sessions", "logs"}) {
      Files.createDirectories(WORKSPACE_DIR.resolve(sub));
    }
    writeIfAbsent(
        "memory/MEMORY.md", "# 长期记忆\n\n（Agent 通过 save_memory 写入，不得手动修改）\n\n## 核心记忆\n\n## 归档记忆\n");
    // 默认 Profile：供 ProfileLoader（第 16 节）加载；Agent 目录机制（.oryxos/agents/）第 29 节迁移
    writeIfAbsent("profiles/default.yaml", defaultProfileYaml());
  }

  private String defaultProfileYaml() {
    return """
        name: default
        description: 默认 Agent
        identity:
          agent_name: 小欧
          prompt: 你是一个乐于助人的助手。回答尽量简洁。
        provider:
          name: deepseek
          model: deepseek-chat
          temperature: 0.7
        tools: []
        bootstrap: []
        settings:
          max_iterations: 10
          max_history_turns: 20
        """;
  }

  private void writeIfAbsent(String relativePath, String content) throws IOException {
    Path path = WORKSPACE_DIR.resolve(relativePath);
    if (Files.exists(path)) {
      return;
    }
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }
}
