package com.oryxos.provider;

import org.springframework.ai.chat.model.ChatModel;

/**
 * LLM Provider 统一抽象。对 ReAct 循环屏蔽不同 LLM 厂商的差异。
 *
 * <p>核心阶段基于 Spring AI 的 {@link ChatModel}（OpenAI 协议覆盖 DeepSeek / Kimi 等）。 多 Provider 并存时必须维护显式的
 * provider name → ChatModel 映射，<b>不靠容器类型扫描</b> （否则 Bean 类型相同会有歧义）。
 */
public interface ProviderService {

  /** 按 provider name 解析对应的 ChatModel，不存在时抛异常 */
  ChatModel resolve(String providerName);
}
