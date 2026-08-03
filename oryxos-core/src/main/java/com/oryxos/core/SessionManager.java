package com.oryxos.core;

import java.util.Optional;

/**
 * 会话管理契约：按三元组幂等获取/创建、按标识查询、持久化 Session.
 *
 * <p><strong>session_id 拼接只发生在实现内部这一处</strong>——所有入口（CLI 传 {@code "cli"}、Web 传 {@code "web"}、定时传
 * {@code "scheduler"}）只提供 channel+user+profile 三元组，不自己拼字符串。两处各拼一遍、格式差一个分隔符，同一个人就会出现两条互不相认的历史 （27
 * 节缝隙③）。第 18 节在 oryxos-storage 提供 JPA 实现 {@code JpaSessionManager}.
 */
public interface SessionManager {

  /** 按三元组幂等获取或创建会话. 同一三元组返回同一个 Session（多轮对话靠它串起来）；任一元素不同则不同会话. */
  Session getOrCreate(String channel, String user, String profileName);

  /** 按会话标识查询，未找到返回 {@code Optional.empty()}. */
  Optional<Session> get(String sessionId);

  /** 持久化 Session（累积完的历史）. */
  void save(Session session);
}
