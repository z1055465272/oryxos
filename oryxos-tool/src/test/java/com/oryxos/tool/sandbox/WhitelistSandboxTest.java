package com.oryxos.tool.sandbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 白名单沙箱测试：三类校验"允许+拒绝"成对 + 路径穿越 + 符号链接逃逸 + 通配符域名边界 + 空配置=全拒绝. */
class WhitelistSandboxTest {

  @TempDir Path tempDir;

  private WhitelistSandbox sandbox(
      List<String> paths, List<String> commands, List<String> domains) {
    return new WhitelistSandbox(
        new FileSandboxProperties(paths),
        new ShellSandboxProperties(commands),
        new HttpSandboxProperties(domains));
  }

  // ===== 文件路径组 =====

  @Test
  @DisplayName("白名单内路径放行")
  void allowlistedPathIsAllowed() {
    WhitelistSandbox sandbox = sandbox(List.of(tempDir.toString()), List.of(), List.of());

    assertDoesNotThrow(
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.FILE_READ, tempDir.resolve("a.txt").toString())));
  }

  @Test
  @DisplayName("白名单外路径被拦")
  void pathOutsideAllowlistIsRejected() {
    WhitelistSandbox sandbox = sandbox(List.of(tempDir.toString()), List.of(), List.of());

    SandboxViolationException ex =
        assertThrows(
            SandboxViolationException.class,
            () -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, "/etc/passwd")));
    assertTrue(ex.getMessage().contains("路径不在白名单内"));
  }

  @Test
  @DisplayName("相对路径穿越必须被拦（normalize 回归）")
  void traversalOutsideAllowlistIsBlocked() {
    WhitelistSandbox sandbox = sandbox(List.of(tempDir.toString()), List.of(), List.of());

    assertThrows(
        SandboxViolationException.class,
        () ->
            sandbox.enforce(
                new SandboxAction(
                    ActionType.FILE_READ,
                    tempDir.resolve("../../outside/secret.txt").normalize().toString())));
  }

  @Test
  @DisplayName("符号链接指向白名单外真实文件被拦（toRealPath 回归）")
  void symlinkEscapingAllowlistIsBlocked() throws Exception {
    Path outsideDir = Files.createTempDirectory("oryxos-outside");
    try {
      Path secret = Files.createFile(outsideDir.resolve("secret.txt"));
      Path link = tempDir.resolve("innocent-link");
      Files.createSymbolicLink(link, secret);

      WhitelistSandbox sandbox = sandbox(List.of(tempDir.toString()), List.of(), List.of());

      assertThrows(
          SandboxViolationException.class,
          () -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, link.toString())));
    } finally {
      deleteRecursively(outsideDir);
    }
  }

  private static void deleteRecursively(Path root) throws Exception {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (java.io.IOException ignored) {
                  // 清理失败不影响断言结果
                }
              });
    }
  }

  // ===== Shell 命令组 =====

  @Test
  @DisplayName("白名单内命令放行")
  void allowlistedCommandIsAllowed() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of("ls"), List.of());

    assertDoesNotThrow(() -> sandbox.enforce(new SandboxAction(ActionType.SHELL, "ls -la")));
  }

  @Test
  @DisplayName("白名单外命令被拦")
  void commandOutsideAllowlistIsRejected() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of("ls"), List.of());

    SandboxViolationException ex =
        assertThrows(
            SandboxViolationException.class,
            () -> sandbox.enforce(new SandboxAction(ActionType.SHELL, "rm -rf /")));
    assertTrue(ex.getMessage().contains("rm"));
  }

  @Test
  @DisplayName("命令首 token 带前导空格、大小写变体的处理")
  void firstTokenTrimmedAndCaseHandled() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of("ls"), List.of());

    assertDoesNotThrow(() -> sandbox.enforce(new SandboxAction(ActionType.SHELL, "  ls -la")));
    // 大小写敏感：白名单 "ls" 不命中 "LS"
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.SHELL, "LS -la")));
  }

  // ===== HTTP 域名组 =====

  @Test
  @DisplayName("精确匹配域名放行")
  void exactDomainMatches() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of(), List.of("example.com"));

    assertDoesNotThrow(
        () -> sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, "https://example.com/x")));
  }

  @Test
  @DisplayName("白名单外域名被拦")
  void domainOutsideAllowlistIsRejected() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of(), List.of("example.com"));

    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, "https://other.com/x")));
  }

  @Test
  @DisplayName("通配符域名不能被形似域名绕过（endsWith 必须带点号边界）")
  void wildcardDomainHitsSubdomain_notSpoofed() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of(), List.of("*.example.com"));

    assertDoesNotThrow(
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://api.example.com/x")));
    // evil-example.com 以 "example.com" 结尾但不是子域——带点号边界后必须被拦
    assertThrows(
        SandboxViolationException.class,
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://evil-example.com/x")));
  }

  // ===== 空配置组 =====

  @Test
  @DisplayName("白名单配置为空 = 什么都不允许，不是不校验")
  void emptyAllowlistDeniesEverything() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of(), List.of());

    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, "/tmp/a")));
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.SHELL, "ls")));
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, "https://example.com")));
  }
}
