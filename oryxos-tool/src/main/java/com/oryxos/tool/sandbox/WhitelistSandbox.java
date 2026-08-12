package com.oryxos.tool.sandbox;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 核心阶段唯一的沙箱实现：应用层白名单校验.
 *
 * <p>劝阻级防线，防模型犯傻，不承诺防蓄意攻击。按 {@link ActionType} 路由到三个私有校验方法；三个校验方法均为
 * private——它们是实现内部的组织自由，不是接口契约的一部分（接口不被第一档实现带偏）。文件路径在 {@code normalize} 之上叠 {@code toRealPath}
 * 真实路径校验（宪法 VI）：已存在目标解析真实路径、不存在目标沿父目录链找 最近存在祖先解析，防符号链接逃逸。
 */
@Component
public class WhitelistSandbox implements Sandbox {

  private final List<Path> allowedRoots;
  private final Set<String> allowedCommands;
  private final List<String> allowedDomainPatterns;

  /** 注入三块白名单配置，预解析路径根、命令集合与域名模式. */
  public WhitelistSandbox(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    this.allowedRoots =
        fileProps.allowedPaths().stream()
            .map(Path::of)
            .map(Path::normalize)
            .map(WhitelistSandbox::resolveRoot)
            .toList();
    this.allowedCommands = Set.copyOf(shellProps.allowedCommands());
    this.allowedDomainPatterns = List.copyOf(httpProps.allowedDomains());
  }

  /** 白名单根也解析为真实路径（若存在），与 {@code toRealPath} 后的目标保持一致，避免软链根误拦合法路径. */
  private static Path resolveRoot(Path root) {
    try {
      return Files.exists(root) ? root.toRealPath() : root;
    } catch (IOException e) {
      return root;
    }
  }

  @Override
  public void enforce(SandboxAction action) {
    if (action.type() == ActionType.FILE_READ || action.type() == ActionType.FILE_WRITE) {
      checkFilePath(action.target());
    } else if (action.type() == ActionType.SHELL) {
      checkShellCommand(action.target());
    } else if (action.type() == ActionType.HTTP_REQUEST) {
      checkHttpUrl(action.target());
    }
  }

  private void checkFilePath(String rawPath) {
    Path target = Path.of(rawPath).normalize().toAbsolutePath();
    Path resolved = resolveRealPath(target);
    boolean allowed = allowedRoots.stream().anyMatch(resolved::startsWith);
    if (!allowed) {
      throw new SandboxViolationException("路径不在白名单内: " + rawPath);
    }
  }

  /**
   * 解析出用于白名单比对的真实路径.
   *
   * <p>目标存在直接 {@code toRealPath()}，不存在则沿父目录链向上找最近存在的祖先解析（防新建路径经软链目录 逃逸到白名单外）。解析失败按目标本身处理。
   */
  private Path resolveRealPath(Path target) {
    try {
      Path current = target;
      while (current != null && !Files.exists(current)) {
        current = current.getParent();
      }
      if (current == null) {
        return target;
      }
      Path realParent = current.toRealPath();
      Path suffix = current.relativize(target);
      return suffix.toString().isEmpty() ? realParent : realParent.resolve(suffix);
    } catch (IOException e) {
      return target;
    }
  }

  private void checkShellCommand(String command) {
    String firstToken = command.trim().split("\\s+")[0];
    if (!allowedCommands.contains(firstToken)) {
      throw new SandboxViolationException("命令不在白名单内: " + firstToken);
    }
  }

  private void checkHttpUrl(String url) {
    String host = URI.create(url).getHost();
    boolean allowed =
        allowedDomainPatterns.stream().anyMatch(pattern -> matchesDomain(host, pattern));
    if (!allowed) {
      throw new SandboxViolationException("域名不在白名单内: " + host);
    }
  }

  /** 通配符匹配：`*.` 前缀带点号边界（evil-example.com 不命中 example.com），否则精确匹配. */
  private boolean matchesDomain(String host, String pattern) {
    if (pattern.startsWith("*.")) {
      return host.endsWith(pattern.substring(1));
    }
    return host.equals(pattern);
  }
}
