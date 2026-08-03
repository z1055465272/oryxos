package com.oryxos.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话（内存版）：承载对话历史累积与审计关联。第 18 节升级为 JPA 实体持久化.
 *
 * <p>messages 为可变累积列表——每轮 LLM 响应与工具执行结果都追加，保证事后可审计、下一轮接得上 （课件坑三：不累积就无法审计）。{@link #appendToolResult}
 * 把工具调用与结果落成一条工具结果消息.
 */
public final class Session {

  /** 会话状态. */
  public enum Status {
    ACTIVE,
    ARCHIVED
  }

  /** 会话消息 sealed 层次：三种消息类型不可扩展，供 Provider 无损重建协议层消息. */
  public sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage {}

  /** 用户输入. */
  public record UserMessage(String content) implements Message {}

  /** LLM 响应（含请求的功能调用，无则为空）. */
  public record AssistantMessage(String content, List<ToolCall> toolCalls) implements Message {
    public AssistantMessage {
      toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }
  }

  /** 工具执行结果，引用 tool_call_id 供协议层配对. */
  public record ToolResultMessage(String toolCallId, String toolName, String content)
      implements Message {}

  private final String sessionId;
  private final String profileName;
  private final String channel;
  private final String userId;
  private final List<Message> messages = new ArrayList<>();
  private Status status = Status.ACTIVE;

  /** 构建会话（内存版）. */
  public Session(String sessionId, String profileName, String channel, String userId) {
    this.sessionId = sessionId;
    this.profileName = profileName;
    this.channel = channel;
    this.userId = userId;
  }

  public String id() {
    return sessionId;
  }

  public String profileName() {
    return profileName;
  }

  public String channel() {
    return channel;
  }

  public String userId() {
    return userId;
  }

  public Status status() {
    return status;
  }

  /** 对话历史的只读视图. */
  public List<Message> messages() {
    return List.copyOf(messages);
  }

  public void appendUserMessage(String content) {
    messages.add(new UserMessage(content));
  }

  public void appendAssistant(String content, List<ToolCall> toolCalls) {
    messages.add(new AssistantMessage(content, toolCalls));
  }

  /** 把一次工具调用与执行结果落成工具结果消息追加到历史（成功带结果、失败带原因）. */
  public void appendToolResult(ToolCall call, ToolResult result) {
    String content = result.success() ? result.content() : result.error();
    messages.add(new ToolResultMessage(call.id(), call.name(), content != null ? content : ""));
  }

  public void markArchived() {
    this.status = Status.ARCHIVED;
  }
}
