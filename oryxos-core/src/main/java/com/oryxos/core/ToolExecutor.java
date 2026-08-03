package com.oryxos.core;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具集中执行器. 从 ToolRegistry 查找工具 → 执行 → 写 tool_invocations 审计（成功/失败都写）.
 *
 * <p>执行权只此一处，不得有第二条工具执行路径（宪法 II：Spring AI 自动工具执行已禁用）。 未知工具/业务失败返回失败结果；工具抛异常则先落审计再上抛（不吞，审计与异常同口径）.
 */
public class ToolExecutor {

  private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

  private final ToolRegistry toolRegistry;
  private final ToolInvocationStore toolInvocationStore;

  public ToolExecutor(ToolRegistry toolRegistry, ToolInvocationStore toolInvocationStore) {
    this.toolRegistry = toolRegistry;
    this.toolInvocationStore = toolInvocationStore;
  }

  /**
   * 执行一次工具调用.
   *
   * @param sessionId 会话标识（用于审计关联）
   * @param toolCall LLM 返回的工具调用请求
   * @return 工具执行结果；未知工具/业务失败返回失败结果，工具抛异常则审计后上抛
   */
  public ToolResult execute(String sessionId, ToolCall toolCall) {
    // Sandbox 调用位：第 24 节接线（WhitelistSandbox.enforce）——涉外 IO 首行过沙箱检查，见 TechnicalSolution §6.7。
    enforceSandbox(toolCall);

    var tool = toolRegistry.get(toolCall.name());
    if (tool.isEmpty()) {
      record(sessionId, toolCall, null, false, "unknown tool: " + toolCall.name(), 0);
      return ToolResult.fail("未知工具: " + toolCall.name(), false);
    }

    long startedAt = System.currentTimeMillis();
    try {
      ToolResult result = tool.get().execute(toolCall.arguments());
      long durationMs = System.currentTimeMillis() - startedAt;
      if (result.success()) {
        record(sessionId, toolCall, result.content(), true, null, durationMs);
      } else {
        record(sessionId, toolCall, null, false, result.error(), durationMs);
      }
      return result;
    } catch (RuntimeException e) {
      long durationMs = System.currentTimeMillis() - startedAt;
      log.error("Tool execution failed: tool={}", toolCall.name(), e);
      record(sessionId, toolCall, null, false, e.getMessage(), durationMs);
      throw e;
    }
  }

  /** Sandbox 调用位（第 24 节接线）。沙箱未就位前为 no-op，保留调用形态以便后续接线. */
  private void enforceSandbox(ToolCall toolCall) {
    // TODO(24节): sandbox.enforce(new SandboxAction(SandboxAction.ActionType.X, target)) —— 见
    // TechnicalSolution §6.7。
  }

  /** 写工具审计记录；落库异常只记日志不阻断工具执行（与 llm_calls 审计同口径）. */
  private void record(
      String sessionId,
      ToolCall toolCall,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs) {
    try {
      toolInvocationStore.save(
          new ToolInvocationRecord(
              sessionId,
              toolCall.name(),
              toolCall.arguments(),
              resultJson,
              success,
              errorMessage,
              durationMs,
              LocalDateTime.now()));
    } catch (Exception e) {
      log.error("Failed to write tool_invocations audit record", e);
    }
  }
}
