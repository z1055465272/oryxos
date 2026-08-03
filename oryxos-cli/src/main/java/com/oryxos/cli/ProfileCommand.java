package com.oryxos.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * 轻命令组：{@code oryxos profile list/create/show/delete}.
 *
 * <p>纯文件操作、不启动 Spring。Profile 以 YAML 文件存于 {@code .oryxos/profiles/}（第 16 节 ProfileLoader 读取目录；
 * Agent 目录机制 {@code .oryxos/agents/} 第 29 节迁移）.
 */
@Command(
    name = "profile",
    description = "Profile（Agent）管理",
    mixinStandardHelpOptions = true,
    subcommands = {
      ProfileCommand.List.class,
      ProfileCommand.Create.class,
      ProfileCommand.Show.class,
      ProfileCommand.Delete.class,
    })
public class ProfileCommand implements Runnable {

  static final Path PROFILES_DIR = Path.of(".oryxos/profiles");

  @Override
  public void run() {
    System.out.println("用法: oryxos profile list | create <name> | show <name> | delete <name>");
  }

  /** {@code oryxos profile list} 列出所有 Profile. */
  @Command(name = "list", description = "列出所有 Profile", mixinStandardHelpOptions = true)
  public static class List implements Runnable {
    @Override
    public void run() {
      if (!Files.isDirectory(PROFILES_DIR)) {
        System.out.println("（无 Profile，先运行 oryxos init）");
        return;
      }
      try (var files = Files.list(PROFILES_DIR)) {
        files
            .filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".yaml"))
            .map(p -> p.getFileName().toString().replaceFirst("\\.ya?ml$", ""))
            .sorted()
            .forEach(name -> System.out.println("- " + name));
      } catch (IOException e) {
        System.out.println("读取失败: " + e.getMessage());
      }
    }
  }

  /** {@code oryxos profile create <name>} 创建一个 Profile. */
  @Command(name = "create", description = "创建一个 Profile", mixinStandardHelpOptions = true)
  public static class Create implements Runnable {
    @Parameters(index = "0", description = "Profile 名")
    String name;

    @Override
    public void run() {
      try {
        Files.createDirectories(PROFILES_DIR);
        Path file = PROFILES_DIR.resolve(name + ".yaml");
        if (Files.exists(file)) {
          System.out.println("Profile 已存在: " + name);
          return;
        }
        Files.writeString(file, template(name), StandardCharsets.UTF_8);
        System.out.println("已创建 Profile: " + name);
      } catch (IOException e) {
        System.err.println("创建失败: " + e.getMessage());
      }
    }

    private String template(String profileName) {
      return """
          name: %s
          description: %s
          identity:
            agent_name: %s
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
          """
          .formatted(profileName, profileName, profileName);
    }
  }

  /** {@code oryxos profile show <name>} 查看一个 Profile. */
  @Command(name = "show", description = "查看一个 Profile", mixinStandardHelpOptions = true)
  public static class Show implements Runnable {
    @Parameters(index = "0", description = "Profile 名")
    String name;

    @Override
    public void run() {
      Path file = PROFILES_DIR.resolve(name + ".yaml");
      if (!Files.exists(file)) {
        System.out.println("Profile 不存在: " + name);
        return;
      }
      try {
        Files.readAllLines(file, StandardCharsets.UTF_8).forEach(System.out::println);
      } catch (IOException e) {
        System.err.println("读取失败: " + e.getMessage());
      }
    }
  }

  /** {@code oryxos profile delete <name>} 删除一个 Profile. */
  @Command(name = "delete", description = "删除一个 Profile", mixinStandardHelpOptions = true)
  public static class Delete implements Runnable {
    @Parameters(index = "0", description = "Profile 名")
    String name;

    @Override
    public void run() {
      Path file = PROFILES_DIR.resolve(name + ".yaml");
      if (!Files.exists(file)) {
        System.out.println("Profile 不存在: " + name);
        return;
      }
      try {
        Files.delete(file);
        System.out.println("已删除 Profile: " + name);
      } catch (IOException e) {
        System.err.println("删除失败: " + e.getMessage());
      }
    }
  }
}
