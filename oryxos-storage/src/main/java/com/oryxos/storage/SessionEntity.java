package com.oryxos.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.Session;
import com.oryxos.core.Session.Message;
import com.oryxos.core.Session.Status;
import com.oryxos.core.ToolCall;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * sessions 表 JPA 实体（字段照 TechnicalSolution §9.2）.
 *
 * <p>对话历史整体 JSON 序列化存 {@code messages_json} 一列（核心阶段不按条拆表）。编解码用 {@code encodeMessages}/{@code
 * decodeMessages} 静态方法，带 {@code type} 判别字段重建 sealed 三型消息；编解码逻辑收在本类，引擎值对象 {@code Session} 保持
 * Spring-agnostic（无 JPA 注解）.
 */
@Entity
@Table(name = "sessions")
public class SessionEntity {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Id
  @Column(name = "session_id", nullable = false, length = 255)
  private String sessionId;

  @Column(name = "profile_name", nullable = false, length = 255)
  private String profileName;

  @Column(name = "channel", nullable = false, length = 100)
  private String channel;

  @Column(name = "user_id", nullable = false, length = 255)
  private String userId;

  @Column(name = "messages_json", nullable = false)
  private String messagesJson;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "last_active_at", nullable = false)
  private LocalDateTime lastActiveAt;

  @Column(name = "archived_at")
  private LocalDateTime archivedAt;

  public SessionEntity() {}

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getMessagesJson() {
    return messagesJson;
  }

  public void setMessagesJson(String messagesJson) {
    this.messagesJson = messagesJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getLastActiveAt() {
    return lastActiveAt;
  }

  public void setLastActiveAt(LocalDateTime lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }

  public LocalDateTime getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(LocalDateTime archivedAt) {
    this.archivedAt = archivedAt;
  }

  /** 把引擎会话值对象转成持久化实体（messages 编码进 messages_json）. */
  public static SessionEntity fromSession(Session session, String messagesJson) {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId(session.id());
    entity.setProfileName(session.profileName());
    entity.setChannel(session.channel());
    entity.setUserId(session.userId());
    entity.setMessagesJson(messagesJson);
    entity.setStatus(session.status().name().toLowerCase());
    return entity;
  }

  /** 把持久化实体还原为引擎会话值对象（messages_json 解码回消息列表）. */
  public static Session toSession(SessionEntity entity) {
    List<Message> messages = decodeMessages(entity.getMessagesJson());
    Status status =
        entity.getStatus() == null
            ? Status.ACTIVE
            : Status.valueOf(entity.getStatus().toUpperCase());
    return Session.restore(
        entity.getSessionId(),
        entity.getProfileName(),
        entity.getChannel(),
        entity.getUserId(),
        status,
        messages);
  }

  /** 编码消息列表为 JSON 文本；空列表存 {@code []}. */
  public static String encodeMessages(List<Message> messages) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Message message : messages) {
      if (message instanceof Session.UserMessage userMessage) {
        rows.add(Map.of("type", "user", "content", userMessage.content()));
      } else if (message instanceof Session.AssistantMessage assistantMessage) {
        List<Map<String, String>> toolCalls =
            assistantMessage.toolCalls() == null
                ? List.of()
                : assistantMessage.toolCalls().stream()
                    .map(
                        call ->
                            Map.of(
                                "id", call.id(),
                                "name", call.name(),
                                "arguments", call.arguments() != null ? call.arguments() : ""))
                    .toList();
        rows.add(
            Map.of(
                "type",
                "assistant",
                "content",
                assistantMessage.content(),
                "toolCalls",
                toolCalls));
      } else if (message instanceof Session.ToolResultMessage toolResult) {
        rows.add(
            Map.of(
                "type",
                "tool_result",
                "toolCallId",
                toolResult.toolCallId(),
                "toolName",
                toolResult.toolName(),
                "content",
                toolResult.content()));
      }
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(rows);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encode session messages to JSON", e);
    }
  }

  /** 解码 JSON 文本为消息列表；空/非法输入返回空列表（首轮对话兜底）. */
  public static List<Message> decodeMessages(String json) {
    List<Message> messages = new ArrayList<>();
    if (json == null || json.isBlank()) {
      return messages;
    }
    List<Map<String, Object>> rows;
    try {
      rows = OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decode session messages from JSON", e);
    }
    for (Map<String, Object> row : rows) {
      String type = (String) row.get("type");
      if ("user".equals(type)) {
        messages.add(new Session.UserMessage(str(row.get("content"))));
      } else if ("assistant".equals(type)) {
        List<ToolCall> calls = new ArrayList<>();
        Object rawCalls = row.get("toolCalls");
        if (rawCalls instanceof List<?> list) {
          for (Object rawCall : list) {
            if (rawCall instanceof Map<?, ?> callMap) {
              calls.add(
                  new ToolCall(
                      str(callMap.get("id")),
                      str(callMap.get("name")),
                      str(callMap.get("arguments"))));
            }
          }
        }
        messages.add(new Session.AssistantMessage(str(row.get("content")), calls));
      } else if ("tool_result".equals(type)) {
        messages.add(
            new Session.ToolResultMessage(
                str(row.get("toolCallId")), str(row.get("toolName")), str(row.get("content"))));
      }
    }
    return messages;
  }

  private static String str(Object value) {
    return value != null ? value.toString() : "";
  }
}
