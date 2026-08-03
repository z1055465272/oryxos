package com.oryxos.core;

/**
 * 会话管理契约：持久化 Session. 第 18 节实现（SQLite sessions 表 + session_id 公式）；本节 AgentService 依赖此接口在编排结束后保存会话.
 */
public interface SessionManager {

  /** 持久化 Session（累积完的历史）. */
  void save(Session session);
}
