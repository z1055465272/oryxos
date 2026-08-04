package com.oryxos.tool.builtin;

import com.oryxos.core.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 文件内置工具：read_file / write_file / list_dir.
 *
 * <p>每个方法执行第一步先过 {@link Sandbox#enforce} 路径白名单校验，不过就抛异常拦下、真实 IO 不发生（宪法 VI，24 节换 WhitelistSandbox
 * 实现，调用形态不变）.
 */
@Component
public class FileTools {

  private final Sandbox sandbox;

  public FileTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  /** 读文件：过 FILE_READ 白名单校验后读取内容. */
  @Tool(name = "read_file", description = "读取指定文件的内容")
  public ToolResult readFile(String path) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));
    try {
      String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
      return ToolResult.ok(content);
    } catch (IOException e) {
      return ToolResult.fail("读取文件失败: " + e.getMessage(), true);
    }
  }

  /** 写文件：过 FILE_WRITE 白名单校验后写入内容（覆盖已有文件）. */
  @Tool(name = "write_file", description = "把内容写入指定文件（覆盖已有内容）")
  public ToolResult writeFile(String path, String content) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_WRITE, path));
    try {
      Path target = Path.of(path);
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(target, content, StandardCharsets.UTF_8);
      return ToolResult.ok("已写入 " + target);
    } catch (IOException e) {
      return ToolResult.fail("写文件失败: " + e.getMessage(), true);
    }
  }

  /** 列目录：过 FILE_READ 白名单校验后列出目录项. */
  @Tool(name = "list_dir", description = "列出目录下的条目")
  public ToolResult listDir(String path) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));
    try (Stream<Path> entries = Files.list(Path.of(path))) {
      String listing =
          entries
              .sorted(
                  Comparator.comparing(
                      p -> p.getFileName() != null ? p.getFileName().toString() : p.toString()))
              .map(p -> p.getFileName() != null ? p.getFileName().toString() : p.toString())
              .reduce("", (acc, name) -> acc.isEmpty() ? name : acc + "\n" + name);
      return ToolResult.ok(listing.isEmpty() ? "(空目录)" : listing);
    } catch (IOException e) {
      return ToolResult.fail("列目录失败: " + e.getMessage(), true);
    }
  }
}
