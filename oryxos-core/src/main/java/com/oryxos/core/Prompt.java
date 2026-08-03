package com.oryxos.core;

import java.util.Collections;
import java.util.List;

/**
 * 一次 LLM 调用的输入：system prompt + 多轮消息 + 本次可用的工具列表.
 *
 * <p>第 17 节扩展：新增 {@code systemMessage} 与 {@code messages} 承载 ReAct 多轮完整上下文， 保留 {@code userMessage}
 * 与旧构造器向后兼容——第 16 节单轮场景与既有测试不受影响.
 */
public record Prompt(
    String userMessage,
    List<Session.Message> messages,
    String systemMessage,
    List<OryxTool> availableTools) {

  public Prompt {
    messages = messages != null ? List.copyOf(messages) : Collections.emptyList();
    availableTools = availableTools != null ? List.copyOf(availableTools) : Collections.emptyList();
  }

  /** 第 16 节兼容构造器：单条用户消息 + 工具列表. */
  public Prompt(String userMessage, List<OryxTool> availableTools) {
    this(userMessage, Collections.emptyList(), null, availableTools);
  }

  /** 第 16 节兼容构造器：仅用户消息. */
  public Prompt(String userMessage) {
    this(userMessage, Collections.emptyList(), null, Collections.emptyList());
  }
}
