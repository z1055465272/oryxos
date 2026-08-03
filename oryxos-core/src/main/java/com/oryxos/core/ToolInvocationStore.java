package com.oryxos.core;

/**
 * 工具审计存储契约（依赖倒置）：core 定义、oryxos-storage 的 JPA 实现落地 tool_invocations 表. 每次工具执行无论成败都写入（宪法 V：审计表 Day
 * One 写入）.
 */
public interface ToolInvocationStore {

  /** 记录一次工具调用的审计. */
  void save(ToolInvocationRecord record);
}
