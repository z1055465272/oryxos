package com.oryxos.core;

import java.util.Collections;
import java.util.List;

/**
 * 一次 LLM 调用的输入：用户消息 + 本次可用的工具列表.
 *
 * <p>工具列表可选：纯对话不传工具，ReAct Loop 推理轮次才带工具 schema.
 */
public record Prompt(String userMessage, List<OryxTool> availableTools) {

  /** 防御性拷贝. */
  public Prompt {
    availableTools =
        availableTools != null ? List.copyOf(availableTools) : Collections.emptyList();
  }

  public Prompt(String userMessage) {
    this(userMessage, Collections.emptyList());
  }
}
