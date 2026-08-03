package com.oryxos.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 每轮 LLM 调用的 Prompt 组装器.
 *
 * <p>四部分按顺序：①system prompt（角色设定 + ContextLoader 的 Bootstrap + Skill 正文 + 当前日期时间） ②长期记忆（第 22 节
 * Memory 模块接入前留空）③会话历史（最近 maxHistoryTurns 条，默认 20，超出截断） ④当前可用工具列表（经 ToolRegistry 从 Profile.tools
 * 名称解析）。
 */
public class PromptBuilder {

  /** 历史截断默认条数（Profile.settings.maxHistoryTurns 未配置时）. */
  public static final int DEFAULT_MAX_HISTORY_TURNS = 20;

  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ContextLoader contextLoader;
  private final ToolRegistry toolRegistry;

  public PromptBuilder(ContextLoader contextLoader, ToolRegistry toolRegistry) {
    this.contextLoader = contextLoader;
    this.toolRegistry = toolRegistry;
  }

  /** 组装本轮 Prompt. */
  public Prompt build(Session session, Profile profile) {
    int maxHistoryTurns =
        profile.settings() != null && profile.settings().maxHistoryTurns() != null
            ? profile.settings().maxHistoryTurns()
            : DEFAULT_MAX_HISTORY_TURNS;
    List<Session.Message> history = truncateHistory(session.messages(), maxHistoryTurns);
    return new Prompt(
        findCurrentUserMessage(history),
        history,
        buildSystemMessage(profile),
        resolveTools(profile));
  }

  private String buildSystemMessage(Profile profile) {
    StringBuilder sb = new StringBuilder();
    if (profile.identity() != null
        && profile.identity().prompt() != null
        && !profile.identity().prompt().isBlank()) {
      sb.append(profile.identity().prompt()).append("\n\n");
    }
    sb.append(contextLoader.loadSystemPrompt(profile));
    String memory = buildMemorySection(profile);
    if (!memory.isBlank()) {
      sb.append("\n\n").append(memory);
    }
    // 末尾附当前日期时间——模型自己不知道今天几号，定时场景的"今天"全靠这一行。
    sb.append("\n\n当前时间：").append(LocalDateTime.now().format(DATE_TIME_FORMAT));
    return sb.toString();
  }

  /** 第二部分：长期记忆占位。第 22 节 Memory 模块接入；未就位时留空（spec FR-002）. */
  private String buildMemorySection(Profile profile) {
    return "";
  }

  /** 只留最近 N 条消息（核心阶段"简单办法"，课件默认 20）；N<=0 时历史为空，由 userMessage 字段兜底. */
  private List<Session.Message> truncateHistory(
      List<Session.Message> messages, int maxHistoryTurns) {
    if (maxHistoryTurns <= 0) {
      return List.of();
    }
    if (messages.size() <= maxHistoryTurns) {
      return messages;
    }
    return messages.subList(messages.size() - maxHistoryTurns, messages.size());
  }

  private List<OryxTool> resolveTools(Profile profile) {
    List<OryxTool> tools = new ArrayList<>();
    for (String toolName : profile.tools()) {
      toolRegistry.get(toolName).ifPresent(tools::add);
    }
    return tools;
  }

  /** 取最近一条用户消息内容（当前轮），供 Provider 单轮回退路径使用. */
  private String findCurrentUserMessage(List<Session.Message> history) {
    for (int i = history.size() - 1; i >= 0; i--) {
      if (history.get(i) instanceof Session.UserMessage userMessage) {
        return userMessage.content();
      }
    }
    return "";
  }
}
