package com.oryxos.core;

import java.util.List;

/**
 * LLM 响应的自有值对象，屏蔽 Spring AI 类型——core 保持 Spring-agnostic.
 *
 * @param text 模型文本回复
 * @param toolCalls 模型请求的功能调用（无则为空列表）
 */
public record Response(String text, List<ToolCall> toolCalls) {

  public Response {
    toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
  }

  /** 是否请求了工具调用——ReAct 循环据此决定是否执行工具后进入下一轮. */
  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }
}
