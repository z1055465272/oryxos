package com.oryxos.memory;

import com.oryxos.core.MemoryScope;
import com.oryxos.core.MemoryService;
import com.oryxos.core.ToolResult;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 把长期记忆暴露给 Agent 的两个内置 Tool：save_memory / recall_memory.
 *
 * <p>{@code @Tool} 注解让 Spring AI 生成 schema（宪法 II：只用 schema 生成、不做自动 tool 执行），执行仍由 ToolExecutor 走既有
 * {@code @Tool} Bean 包装路径。scope 参数是坑三的解法——是不是核心记忆由 Agent 自己判断，工具层不猜.
 */
@Component
public class MemoryTools {

  private final MemoryService memoryService;

  public MemoryTools(MemoryService memoryService) {
    this.memoryService = memoryService;
  }

  /** 记住一条值得长期保留的事；scope 缺失/非法按归档处理（系统不猜核心）. */
  @Tool(name = "save_memory", description = "记住一件值得长期记住的事")
  public ToolResult saveMemory(
      @ToolParam(description = "要记住的内容") String content,
      @ToolParam(description = "core 或 archival，不确定就填 archival") String scope) {
    memoryService.remember(content, resolveScope(scope));
    return ToolResult.ok("已记住");
  }

  /** 按关键词检索归档记忆；未命中返回提示，不抛异常. */
  @Tool(name = "recall_memory", description = "按关键词检索长期记忆")
  public ToolResult recallMemory(@ToolParam(description = "检索关键词") String keyword) {
    List<String> hits = memoryService.recall(keyword);
    if (hits.isEmpty()) {
      return ToolResult.ok("没有找到相关记忆");
    }
    return ToolResult.ok(String.join("\n", hits));
  }

  /** scope 归一：null/空白/非法值一律按 ARCHIVAL（课件坑三：写入靠参数不靠猜）. */
  private static MemoryScope resolveScope(String scope) {
    if (scope == null || scope.isBlank()) {
      return MemoryScope.ARCHIVAL;
    }
    try {
      return MemoryScope.valueOf(scope.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return MemoryScope.ARCHIVAL;
    }
  }
}
