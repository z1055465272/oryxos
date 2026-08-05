package com.oryxos.memory;

import com.oryxos.core.MemoryScope;
import com.oryxos.core.MemoryService;
import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import java.util.List;

/**
 * {@link MemoryService} 的默认实现（契约在 core、实现在 memory，依赖倒置）.
 *
 * <p>把长期记忆委托给 {@link LongTermMemory}（MEMORY.md 文件）。{@code buildContext} 组装"核心记忆全文 + 归档记忆截断段 +
 * 会话历史摘要" 三段文本给 PromptBuilder；{@code remember}/{@code recall} 供 MemoryTools 调用。写入归类缺省按归档处理（系统不猜核心）.
 */
public class DefaultMemoryService implements MemoryService {

  private final LongTermMemory longTermMemory;

  public DefaultMemoryService(LongTermMemory longTermMemory) {
    this.longTermMemory = longTermMemory;
  }

  @Override
  public String buildContext(Session session, Profile profile) {
    String longTerm = longTermMemory.load();
    String history = formatHistory(session);
    if (longTerm.isEmpty() && history.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (!longTerm.isEmpty()) {
      sb.append("长期记忆：\n").append(longTerm);
    }
    if (!history.isEmpty()) {
      if (sb.length() > 0) {
        sb.append("\n\n");
      }
      sb.append("会话历史：\n").append(history);
    }
    return sb.toString();
  }

  @Override
  public void remember(String content, MemoryScope scope) {
    longTermMemory.append(content, scope != null ? scope : MemoryScope.ARCHIVAL);
  }

  @Override
  public List<String> recall(String keyword) {
    return longTermMemory.recallByKeyword(keyword);
  }

  /** 把会话历史渲染成可读的多行文本；空会话返回空串. */
  private static String formatHistory(Session session) {
    List<String> lines =
        session.messages().stream().map(DefaultMemoryService::formatMessage).toList();
    return String.join("\n", lines);
  }

  private static String formatMessage(Session.Message message) {
    if (message instanceof Session.UserMessage userMessage) {
      return "用户: " + userMessage.content();
    }
    if (message instanceof Session.AssistantMessage assistantMessage) {
      return "助手: " + assistantMessage.content();
    }
    if (message instanceof Session.ToolResultMessage toolMessage) {
      return "工具(" + toolMessage.toolName() + "): " + toolMessage.content();
    }
    return "";
  }
}
