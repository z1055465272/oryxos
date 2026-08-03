package com.oryxos.core;

/**
 * 三种触发源（CLI/Web/定时）共用的统一编排入口.
 *
 * <p>入口把当前 Agent 的 Profile 放入 {@link ProfileContext}（工具执行时能取到当前 Agent 配置）， 驱动 {@link ReActLoop}
 * 跑完循环、持久化 Session；结束（含异常路径，finally）必须清掉上下文.
 */
public class AgentService {

  private final ProfileRegistry profileRegistry;
  private final ReActLoop reActLoop;
  private final SessionManager sessionManager;

  /** 构建统一编排入口. */
  public AgentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    this.profileRegistry = profileRegistry;
    this.reActLoop = reActLoop;
    this.sessionManager = sessionManager;
  }

  /**
   * 处理一次用户消息.
   *
   * @throws RuntimeException ReActLoop 异常时原样上抛（ProfileContext 在 finally 中保证清理）
   */
  public String process(Session session, String userMessage) {
    Profile profile =
        profileRegistry
            .get(session.profileName())
            .orElseThrow(
                () -> new IllegalArgumentException("未注册的 Profile: " + session.profileName()));
    ProfileContext.set(profile);
    try {
      String reply = reActLoop.run(session, userMessage, profile);
      sessionManager.save(session);
      return reply;
    } finally {
      ProfileContext.clear();
    }
  }
}
