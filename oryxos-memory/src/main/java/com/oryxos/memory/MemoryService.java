package com.oryxos.memory;

/**
 * Memory 三层记忆统一门面。对 ReAct 循环只暴露一个接口，
 * 内部把会话记忆委托给 SessionManager、长期记忆委托给 LongTermMemory。
 * <p>
 * 核心阶段做会话 + 长期两层（MEMORY.md 文件 + save_memory / recall_memory 两个内置 Tool），
 * 情景记忆和向量检索放扩展阶段。{@code recallMemory} 预留向量检索升级空间。
 */
public interface MemoryService {

    /** 加载整个长期记忆（MEMORY.md 内容），注入 system prompt */
    String loadLongTermMemory();

    /** Agent 调用 save_memory：把内容追加到 MEMORY.md */
    void saveMemory(String content);

    /** Agent 调用 recall_memory：按关键词检索 MEMORY.md 返回匹配行 */
    String recallMemory(String keyword);
}
