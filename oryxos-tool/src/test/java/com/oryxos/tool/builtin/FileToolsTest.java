package com.oryxos.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oryxos.core.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 文件工具测试：正常路径在临时目录真实读写；越界路径用 mock Sandbox 命中即抛，断言工具抛异常、真实 IO 不发生. */
class FileToolsTest {

  @TempDir Path tempDir;

  private Sandbox sandbox;
  private FileTools fileTools;

  @BeforeEach
  void setUp() {
    sandbox = mock(Sandbox.class);
    fileTools = new FileTools(sandbox);
  }

  @Test
  @DisplayName("read_file 读取白名单内文件返回成功内容")
  void readFileWithinAllowlistReturnsContent() throws IOException {
    Path file = tempDir.resolve("hello.txt");
    Files.writeString(file, "你好 OryxOS", StandardCharsets.UTF_8);

    ToolResult result = fileTools.readFile(file.toString());

    assertTrue(result.success());
    assertNotNull(result.content());
    assertTrue(result.content().contains("你好 OryxOS"));
  }

  @Test
  @DisplayName("read_file 命中白名单外路径被拦下，抛异常且不读文件")
  void readFileOutsideAllowlistIsBlocked() {
    doThrow(new RuntimeException("sandbox blocked"))
        .when(sandbox)
        .enforce(new SandboxAction(ActionType.FILE_READ, "/etc/passwd"));

    assertThrows(RuntimeException.class, () -> fileTools.readFile("/etc/passwd"));
  }

  @Test
  @DisplayName("read_file 先过校验再读文件，校验调用在真实 IO 之前")
  void readFileEnforceBeforeRead() throws IOException {
    Path file = tempDir.resolve("a.txt");
    Files.writeString(file, "x", StandardCharsets.UTF_8);

    fileTools.readFile(file.toString());

    verify(sandbox).enforce(new SandboxAction(ActionType.FILE_READ, file.toString()));
  }

  @Test
  @DisplayName("write_file 写入后回读内容一致")
  void writeFileRoundTrip() {
    Path file = tempDir.resolve("out.txt");

    ToolResult result = fileTools.writeFile(file.toString(), "写入内容");

    assertTrue(result.success());
    try {
      assertEquals("写入内容", Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new AssertionError("写入文件应可回读", e);
    }
  }

  @Test
  @DisplayName("write_file 先过校验再写文件")
  void writeFileEnforceBeforeWrite() {
    Path file = tempDir.resolve("out.txt");

    fileTools.writeFile(file.toString(), "x");

    verify(sandbox).enforce(new SandboxAction(ActionType.FILE_WRITE, file.toString()));
  }

  @Test
  @DisplayName("list_dir 列目录返回目录项")
  void listDirReturnsEntries() throws IOException {
    Files.writeString(tempDir.resolve("one.txt"), "1", StandardCharsets.UTF_8);
    Files.writeString(tempDir.resolve("two.txt"), "2", StandardCharsets.UTF_8);

    ToolResult result = fileTools.listDir(tempDir.toString());

    assertTrue(result.success());
    assertNotNull(result.content());
    assertTrue(result.content().contains("one.txt"));
    assertTrue(result.content().contains("two.txt"));
  }
}
