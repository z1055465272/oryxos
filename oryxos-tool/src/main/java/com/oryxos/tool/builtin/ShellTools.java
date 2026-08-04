package com.oryxos.tool.builtin;

import com.oryxos.core.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Shell 内置工具：执行命令，带超时.
 *
 * <p>执行第一步先过 {@link Sandbox#enforce} 命令白名单校验（宪法 VI），校验不过抛异常拦下、真实子进程不启动； 超时用 {@link
 * Process#waitFor(long, TimeUnit)} 兜底，防止命令挂死卡住 Agent.
 */
@Component
public class ShellTools {

  /** 默认命令超时：10 秒，防止模型误调长任务挂死循环. */
  private static final long DEFAULT_TIMEOUT_SECONDS = 10;

  private final Sandbox sandbox;

  public ShellTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  /** 执行 shell 命令：过 SHELL 白名单校验后经系统 shell 执行，带超时. */
  @Tool(name = "shell", description = "执行一条 shell 命令并返回输出（带超时）")
  public ToolResult shell(String command) {
    sandbox.enforce(new SandboxAction(ActionType.SHELL, command));
    try {
      Process process = new ProcessBuilder(shellCommand(command)).redirectErrorStream(true).start();
      boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return ToolResult.fail("命令执行超时（超过 " + DEFAULT_TIMEOUT_SECONDS + " 秒）", true);
      }
      String output = new String(process.getInputStream().readAllBytes());
      if (process.exitValue() != 0) {
        return ToolResult.fail("命令退出码 " + process.exitValue() + ": " + output, true);
      }
      return ToolResult.ok(output);
    } catch (IOException e) {
      return ToolResult.fail("命令启动失败: " + e.getMessage(), true);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ToolResult.fail("命令执行被中断", false);
    }
  }

  /** 按平台路由命令形态：Windows 用 cmd /c，其余用 /bin/sh -c. */
  private static String[] shellCommand(String command) {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win")) {
      return new String[] {"cmd", "/c", command};
    }
    return new String[] {"/bin/sh", "-c", command};
  }
}
