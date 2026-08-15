package com.oryxos.tool.sandbox;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.ToolCall;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 全局工具沙箱守卫测试：工具名→target 映射、参数解析、拒绝/放行、未知工具跳过. */
class WhitelistToolSandboxGuardTest {

  @TempDir Path tempDir;

  private WhitelistToolSandboxGuard guard(
      List<String> commands, List<String> paths, List<String> domains) {
    WhitelistSandbox sandbox =
        new WhitelistSandbox(
            new FileSandboxProperties(paths),
            new ShellSandboxProperties(commands),
            new HttpSandboxProperties(domains));
    return new WhitelistToolSandboxGuard(sandbox);
  }

  @Test
  @DisplayName("shell 白名单外命令被全局守卫拒绝")
  void shellCommandOutsideAllowlist_rejected() {
    WhitelistToolSandboxGuard guard = guard(List.of("ls"), List.of(), List.of());

    String violation = guard.check(new ToolCall("c1", "shell", "{\"command\":\"rm -rf /\"}"));

    assertTrue(violation.contains("命令不在白名单内: rm"));
  }

  @Test
  @DisplayName("shell 白名单内命令放行")
  void shellCommandInsideAllowlist_allowed() {
    WhitelistToolSandboxGuard guard = guard(List.of("ls"), List.of(), List.of());

    assertNull(guard.check(new ToolCall("c1", "shell", "{\"command\":\"ls -la\"}")));
  }

  @Test
  @DisplayName("read_file 白名单外路径被全局守卫拒绝")
  void fileReadOutsideAllowlist_rejected() {
    WhitelistToolSandboxGuard guard = guard(List.of(), List.of(tempDir.toString()), List.of());

    String violation = guard.check(new ToolCall("c1", "read_file", "{\"path\":\"/etc/passwd\"}"));

    assertTrue(violation.contains("路径不在白名单内"));
  }

  @Test
  @DisplayName("http_get 形似域名被全局守卫拒绝")
  void httpGetSpoofedDomain_rejected() {
    WhitelistToolSandboxGuard guard = guard(List.of(), List.of(), List.of("*.example.com"));

    String violation =
        guard.check(new ToolCall("c1", "http_get", "{\"url\":\"https://evil-example.com/\"}"));

    assertTrue(violation.contains("域名不在白名单内: evil-example.com"));
  }

  @Test
  @DisplayName("未知工具名不在守卫管辖范围，放行")
  void unknownToolName_skipped() {
    WhitelistToolSandboxGuard guard = guard(List.of(), List.of(), List.of());

    assertNull(guard.check(new ToolCall("c1", "custom_tool", "{}")));
  }

  @Test
  @DisplayName("参数缺目标字段或非法 JSON，按拒绝处理防绕过")
  void malformedArguments_rejected() {
    WhitelistToolSandboxGuard guard = guard(List.of("ls"), List.of(), List.of());

    assertTrue(guard.check(new ToolCall("c1", "shell", "{}")).contains("参数无法解析或缺少目标字段"));
    assertTrue(guard.check(new ToolCall("c1", "shell", "not-json")).contains("参数无法解析或缺少目标字段"));
  }

  @Test
  @DisplayName("write_file 映射 FILE_WRITE，list_dir 映射 FILE_READ")
  void writeFileAndListDir_mapCorrectly() {
    WhitelistToolSandboxGuard guard = guard(List.of(), List.of(tempDir.toString()), List.of());
    String dir = tempDir.toString().replace("\\", "\\\\");
    String file = tempDir.resolve("a.txt").toString().replace("\\", "\\\\");

    assertNull(guard.check(new ToolCall("c1", "write_file", "{\"path\":\"" + file + "\"}")));
    assertNull(guard.check(new ToolCall("c1", "list_dir", "{\"path\":\"" + dir + "\"}")));
  }

  @Test
  @DisplayName("save_memory/recall_memory/notify 不在守卫管辖范围")
  void memoryAndNotifyTools_skipped() {
    WhitelistToolSandboxGuard guard = guard(List.of(), List.of(), List.of());

    assertNull(guard.check(new ToolCall("c1", "save_memory", "{\"content\":\"x\"}")));
    assertNull(guard.check(new ToolCall("c1", "recall_memory", "{\"keyword\":\"x\"}")));
    assertNull(guard.check(new ToolCall("c1", "notify", "{\"content\":\"x\"}")));
  }
}
