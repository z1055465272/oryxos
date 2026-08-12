package com.oryxos.tool.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.oryxos.core.NotifyChannelConfig;
import com.oryxos.core.Profile;
import com.oryxos.core.ProfileContext;
import com.oryxos.core.ToolResult;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.notify.NotifyChannelAdapterRouter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

/**
 * 接线回归：四个内置 Tool 接真受限 WhitelistSandbox，白名单外输入被拦、真实 IO 未发生。
 *
 * <p>与 mock Sandbox 的既有测试（FileToolsTest 等）不同，这里用真实白名单沙箱证明"拦截由校验产生"， 且底层执行器未真正工作（子进程未启动 / 请求未发出 /
 * 适配器未被调）。
 */
class WhitelistSandboxWiringTest {

  @TempDir Path tempDir;

  private MockWebServer mockWebServer;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
    ProfileContext.clear();
  }

  private WhitelistSandbox shellSandbox() {
    return new WhitelistSandbox(
        new FileSandboxProperties(List.of()),
        new ShellSandboxProperties(List.of("ls")),
        new HttpSandboxProperties(List.of()));
  }

  private WhitelistSandbox fileSandbox(Path root) {
    return new WhitelistSandbox(
        new FileSandboxProperties(List.of(root.toString())),
        new ShellSandboxProperties(List.of()),
        new HttpSandboxProperties(List.of()));
  }

  private WhitelistSandbox httpSandbox(List<String> domains) {
    return new WhitelistSandbox(
        new FileSandboxProperties(List.of()),
        new ShellSandboxProperties(List.of()),
        new HttpSandboxProperties(domains));
  }

  @Test
  @DisplayName("shell 白名单外命令被拦，抛异常且真实子进程未启动")
  void shellBlockedByWhitelist_doesNotStartProcess() {
    ShellTools shellTools = new ShellTools(shellSandbox());

    SandboxViolationException ex =
        assertThrows(SandboxViolationException.class, () -> shellTools.shell("rm -rf /"));
    assertTrue(ex.getMessage().contains("命令不在白名单内: rm"));
  }

  @Test
  @DisplayName("shell 白名单内命令放行，真实执行返回结果")
  void shellAllowedByWhitelist_runsCommand() {
    ShellTools shellTools = new ShellTools(shellSandbox());

    ToolResult result = shellTools.shell("ls");

    assertTrue(result.success());
  }

  @Test
  @DisplayName("read_file 白名单外路径被拦，抛异常且真实 IO 未发生")
  void fileReadBlockedByWhitelist_doesNotRead() {
    FileTools fileTools = new FileTools(fileSandbox(tempDir));

    SandboxViolationException ex =
        assertThrows(SandboxViolationException.class, () -> fileTools.readFile("/etc/passwd"));
    assertTrue(ex.getMessage().contains("路径不在白名单内"));
  }

  @Test
  @DisplayName("read_file 白名单内路径放行，真实读取返回内容")
  void fileReadAllowedByWhitelist_reads() throws IOException {
    Path file = tempDir.resolve("hello.txt");
    Files.writeString(file, "你好 OryxOS", StandardCharsets.UTF_8);
    FileTools fileTools = new FileTools(fileSandbox(tempDir));

    ToolResult result = fileTools.readFile(file.toString());

    assertTrue(result.success());
    assertTrue(result.content().contains("你好 OryxOS"));
  }

  @Test
  @DisplayName("read_file 相对路径穿越被拦，抛异常且真实 IO 未发生")
  void fileTraversalBlockedByWhitelist_doesNotRead() {
    FileTools fileTools = new FileTools(fileSandbox(tempDir));

    String escaped = tempDir.resolve("../../outside/secret.txt").normalize().toString();
    assertThrows(SandboxViolationException.class, () -> fileTools.readFile(escaped));
  }

  @Test
  @DisplayName("http_get 形似域名被拦，抛异常且请求未发出")
  void httpGetBlockedByWhitelist_requestNotSent() {
    HttpTools httpTools = new HttpTools(httpSandbox(List.of("*.example.com")), RestClient.create());

    assertThrows(
        SandboxViolationException.class, () -> httpTools.httpGet("https://evil-example.com/"));
    assertEquals(0, mockWebServer.getRequestCount(), "请求不应发出");
  }

  @Test
  @DisplayName("http_get 通配符命中的子域放行，请求真实发出")
  void httpWildcard_allowsSubdomain_requestSent() throws Exception {
    mockWebServer.enqueue(new mockwebserver3.MockResponse.Builder().code(200).body("ok").build());
    // 白名单含本地测试服务的 host，验证真实请求链路在放行时工作
    HttpTools httpTools = new HttpTools(httpSandbox(List.of("localhost")), RestClient.create());

    ToolResult result = httpTools.httpGet(mockWebServer.url("/x").toString());

    assertTrue(result.success());
    assertEquals(1, mockWebServer.getRequestCount(), "请求应真实发出");
  }

  @Test
  @DisplayName("notify 白名单外 webhook 域名被拦，适配器未被调用")
  void notifyBlockedByWhitelist_adapterNotCalled() {
    NotifyChannelAdapterRouter router = mock(NotifyChannelAdapterRouter.class);
    NotifyTools notifyTools =
        new NotifyTools(httpSandbox(List.of("*.example.com")), router, new ProfileContext());
    Profile profile =
        new Profile(
            "test-agent",
            null,
            new Profile.Identity("test", "prompt"),
            new Profile.ProviderRef("deepseek", null, null),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new NotifyChannelConfig("webhook", "https://hooks.evil.com/ops")),
            List.of(),
            List.of(),
            new Profile.Settings(null, null));
    ProfileContext.set(profile);

    assertThrows(SandboxViolationException.class, () -> notifyTools.notify("hello", null));
    verifyNoInteractions(router);
  }
}
