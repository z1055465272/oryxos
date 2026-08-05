package com.oryxos.core;

import java.util.List;

/**
 * Memory 三层记忆统一门面（契约在 core、实现在 oryxos-memory，同 ProviderService/SessionManager 依赖倒置模式）.
 *
 * <p>对 ReAct 循环只暴露统一记忆读写接口：给 PromptBuilder 的"拼进 Prompt 的记忆内容"、给 MemoryTools
 * 的"记一条"和"按关键词查一下"。内部把会话记忆委托给 {@link SessionManager}、长期记忆委托给 LongTermMemory（MEMORY.md 文件）。核心阶段做会话 +
 * 长期两层，情景记忆和向量检索放扩展阶段.
 */
public interface MemoryService {

  /**
   * 组装拼进 Prompt 的记忆上下文：核心记忆全文 + 归档记忆截断段 + 会话历史摘要.
   *
   * @return 三段拼接文本；无记忆时为空串
   */
  String buildContext(Session session, Profile profile);

  /** 记一条长期记忆。写入核心还是归档由 {@code scope} 显式指定（系统不猜），缺省/非法值按 {@link MemoryScope#ARCHIVAL} 处理. */
  void remember(String content, MemoryScope scope);

  /** 按关键词检索归档记忆，返回匹配行；未命中返回空列表（不抛异常）. */
  List<String> recall(String keyword);
}
