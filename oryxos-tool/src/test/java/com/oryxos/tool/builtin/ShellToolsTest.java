package com.oryxos.tool.builtin;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Shell 工具测试：白名单内命令真实执行并返回输出；越界用 mock Sandbox 命中即抛，断言工具抛异常、真实子进程不启动.
 *
 * <p>用平台无关命令（Windows/Linux 均有）保证单测在两种环境可跑.
 */
class ShellToolsTest {

  private Sandbox sandbox;
  private ShellTools shellTools;

  @BeforeEach
  void setUp() {
    sandbox = mock(Sandbox.class);
    shellTools = new ShellTools(sandbox);
  }

  @Test
  @DisplayName("shell 执行白名单内命令返回输出")
  void shellRunsAllowlistedCommand() {
    ToolResult result = shellTools.shell("java -version");

    assertTrue(result.success());
    assertNotNull(result.content());
    assertTrue(result.content().length() > 0, "命令输出不应为空");
  }

  @Test
  @DisplayName("shell 命中白名单外命令被拦下，抛异常且不真正执行")
  void shellOutsideAllowlistIsBlocked() {
    doThrow(new RuntimeException("sandbox blocked"))
        .when(sandbox)
        .enforce(new SandboxAction(ActionType.SHELL, "rm -rf /"));

    assertThrows(RuntimeException.class, () -> shellTools.shell("rm -rf /"));
  }

  @Test
  @DisplayName("shell 先过校验再执行命令")
  void shellEnforceBeforeExec() {
    shellTools.shell("java -version");

    verify(sandbox).enforce(new SandboxAction(ActionType.SHELL, "java -version"));
  }
}
