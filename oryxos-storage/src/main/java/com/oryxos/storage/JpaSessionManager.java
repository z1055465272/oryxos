package com.oryxos.storage;

import com.oryxos.core.Session;
import com.oryxos.core.Session.Status;
import com.oryxos.core.SessionManager;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SessionManager 的 JPA 实现（依赖倒置：契约在 core、实现在 storage，同 §8.5 ScheduledTaskStore 模式）.
 *
 * <p><strong>session_id 拼接只在本类这一处</strong>——channel + user + profile 用 {@code :} 分隔联合唯一。所有入口只传三元组、
 * 不自己拼字符串；格式差一个分隔符，同一个人就会出现两条互不相认的历史（27 节缝隙③）.
 */
public class JpaSessionManager implements SessionManager {

  private static final String ID_SEPARATOR = ":";

  private final SessionRepository repository;

  public JpaSessionManager(SessionRepository repository) {
    this.repository = repository;
  }

  @Override
  public Session getOrCreate(String channel, String user, String profileName) {
    String sessionId = buildSessionId(channel, user, profileName);
    Optional<SessionEntity> existing = repository.findById(sessionId);
    if (existing.isPresent()) {
      return SessionEntity.toSession(existing.get());
    }
    Session created = new Session(sessionId, profileName, channel, user);
    save(created);
    return created;
  }

  @Override
  public Optional<Session> get(String sessionId) {
    return repository.findById(sessionId).map(SessionEntity::toSession);
  }

  @Override
  public void save(Session session) {
    SessionEntity entity =
        SessionEntity.fromSession(session, SessionEntity.encodeMessages(session.messages()));
    LocalDateTime now = LocalDateTime.now();
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(now);
    }
    if (session.status() == Status.ARCHIVED && entity.getArchivedAt() == null) {
      entity.setArchivedAt(now);
    }
    entity.setLastActiveAt(now);
    repository.save(entity);
  }

  /** session_id 唯一拼接（channel + ":" + user + ":" + profileName）. */
  static String buildSessionId(String channel, String user, String profileName) {
    return channel + ID_SEPARATOR + user + ID_SEPARATOR + profileName;
  }
}
