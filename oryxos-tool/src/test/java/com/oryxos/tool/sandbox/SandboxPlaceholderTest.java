package com.oryxos.tool.sandbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class SandboxPlaceholderTest {

  @Test
  void noOpEnforceDoesNotThrow() {
    Sandbox sandbox = new NoOpSandbox();
    assertDoesNotThrow(
        () -> sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, "https://example.com")));
    assertDoesNotThrow(() -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, "/tmp/test")));
    assertDoesNotThrow(() -> sandbox.enforce(new SandboxAction(ActionType.SHELL, "ls")));
  }
}
