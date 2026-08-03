package com.oryxos.core;

/**
 * LLM Provider 统一抽象，对 ReAct 循环屏蔽不同 LLM 厂商差异.
 *
 * <p>契约放 oryxos-core（依赖倒置），实现由 oryxos-provider 的 {@code DefaultProviderService} 提供。 第 17 节迁移：chat
 * 返回自有 {@link Response}，core 不依赖 Spring AI 类型.
 */
public interface ProviderService {

  /**
   * 发起一次 LLM 调用，按 Profile 的 provider name 路由到正确的 ChatModel， 调用成功/失败都写 llm_calls 审计表.
   *
   * @param sessionId 会话标识，用于审计记录关联
   * @param profile Agent 配置（provider 选择、model、temperature 等）
   * @param prompt 本轮完整上下文（system prompt + 多轮消息 + 可用工具）
   * @return OryxOS 自有响应（text + toolCalls），含可能的工具调用请求，由上层 ReActLoop 处理
   */
  Response chat(String sessionId, Profile profile, Prompt prompt);
}
