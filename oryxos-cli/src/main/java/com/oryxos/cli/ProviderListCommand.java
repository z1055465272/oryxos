package com.oryxos.cli;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine.Command;

/** 轻命令：{@code oryxos provider list} 列出配置中的 Provider. 读配置、不启动 Spring. */
@Command(
    name = "provider",
    description = "Provider 管理",
    mixinStandardHelpOptions = true,
    subcommands = {ProviderListCommand.ListCommand.class})
public class ProviderListCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("用法: oryxos provider list");
  }

  /** {@code oryxos provider list} 列出已配置的 Provider. */
  @Command(name = "list", description = "列出已配置的 Provider", mixinStandardHelpOptions = true)
  public static class ListCommand implements Runnable {
    @Override
    public void run() {
      List<String> names = loadProviderNames();
      if (names.isEmpty()) {
        System.out.println("（未配置 Provider，检查 application.yaml 的 oryxos.providers）");
        return;
      }
      names.forEach(name -> System.out.println("- " + name));
    }
  }

  /** 读 classpath 根的 application.yaml 的 oryxos.providers[].name（环境变量不解析，只列名）. */
  @SuppressWarnings("unchecked")
  static List<String> loadProviderNames() {
    List<String> names = new ArrayList<>();
    try (InputStream in = Files.newInputStream(Path.of("application.yaml"))) {
      Map<String, Object> root = (Map<String, Object>) new Yaml().load(in);
      if (root == null) {
        return names;
      }
      Map<String, Object> oryxos = (Map<String, Object>) root.get("oryxos");
      if (oryxos == null) {
        return names;
      }
      List<Map<String, Object>> providers = (List<Map<String, Object>>) oryxos.get("providers");
      if (providers != null) {
        for (Map<String, Object> provider : providers) {
          Object name = provider.get("name");
          if (name != null) {
            names.add(name.toString());
          }
        }
      }
    } catch (Exception e) {
      // 无配置文件时不报错，返回空列表
    }
    return names;
  }
}
