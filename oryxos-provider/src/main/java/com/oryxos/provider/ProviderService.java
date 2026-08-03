package com.oryxos.provider;

import com.oryxos.core.Profile;
import com.oryxos.core.Prompt;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * LLM Provider 统一抽象。对 ReAct 循环屏蔽不同 LLM 厂商的差异。
 *
 * <p>核心阶段基于 Spring AI 的 {@link ChatModel}（OpenAI 协议覆盖 DeepSeek / Kimi 等）。多 Provider 并存时必须维护显式的
 * provider name → ChatModel 映射，<b>不靠容器类型扫描</b>（否则 Bean 类型相同会有歧义）.
 */
public interface ProviderService {

  /** 按 provider name 解析对应的 ChatModel，不存在时抛异常. */
  ChatModel resolve(String providerName);

  /**
   * 发起一次 LLM 调用，按 Profile 的 provider name 路由到正确的 ChatModel，调用成功/失败都写 llm_calls 审计表.
   *
   * @param sessionId 会话标识，用于审计记录关联
   * @param profile Agent 配置（provider 选择、model、temperature 等）
   * @param prompt 用户消息与可用工具列表
   * @return LLM 原始响应（含可能的 tool call 请求，由上层 ReActLoop 处理）
   */
  ChatResponse chat(String sessionId, Profile profile, Prompt prompt);
}
