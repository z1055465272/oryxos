# Contracts: ReAct 循环引擎

**Feature**: ReAct 循环引擎 | **Date**: 2026-08-03

## 公共接口契约

### 1. ProviderService（自 oryxos-provider 迁入 oryxos-core）

```java
package com.oryxos.core;

/**
 * LLM Provider 统一抽象，对 ReAct 循环屏蔽不同 LLM 厂商差异。
 * 契约放 core（依赖倒置），实现由 oryxos-provider 的 DefaultProviderService 提供。
 */
public interface ProviderService {

  /**
   * 发起一次 LLM 调用，按 Profile 的 provider name 路由到正确的 ChatModel，
   * 调用成功/失败都写 llm_calls 审计表。
   *
   * @param sessionId 会话标识，用于审计记录关联
   * @param profile   Agent 配置（provider 选择、model、temperature 等）
   * @param prompt    本轮完整上下文（system prompt + 多轮消息 + 可用工具）
   * @return OryxOS 自有响应（text + toolCalls），不暴露 Spring AI 类型
   */
  Response chat(String sessionId, Profile profile, Prompt prompt);
}
```

### 2. Response / ToolCall（oryxos-core 值对象）

```java
package com.oryxos.core;

import java.util.List;

/** LLM 响应：文本 + 请求的功能调用列表. */
public record Response(String text, List<ToolCall> toolCalls) {
  public boolean hasToolCalls() {
    return toolCalls != null && !toolCalls.isEmpty();
  }
}

/** 工具调用请求值对象（Session 与 Response 共用）. */
public record ToolCall(String id, String name, String arguments) {}
```

### 3. Session（oryxos-core 内存版，第 18 节持久化）

```java
package com.oryxos.core;

import java.util.List;

public final class Session {
  public enum Status { ACTIVE, ARCHIVED }

  /** 会话消息 sealed 层次. */
  public sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage {}

  /** 用户输入. */
  public record UserMessage(String content) implements Message {}

  /** LLM 响应（含功能调用请求）. */
  public record AssistantMessage(String content, List<ToolCall> toolCalls) implements Message {}

  /** 工具执行结果（引用 tool_call_id 供协议层配对）. */
  public record ToolResultMessage(String toolCallId, String toolName, String content)
      implements Message {}

  public Session(String sessionId, String profileName, String channel, String userId);

  public String id();
  public String profileName();
  public String channel();
  public String userId();
  public Status status();

  /** 对话历史的只读视图. */
  public List<Message> messages();

  public void appendUserMessage(String content);
  public void appendAssistant(String content, List<ToolCall> toolCalls);
  public void appendToolResult(ToolCall call, ToolResult result);
  public void markArchived();
}
```

### 4. ReActLoop（oryxos-core）

```java
package com.oryxos.core;

/**
 * Agent 的核心循环引擎。
 * 输入 Session + 用户消息 + Profile，驱动 ReAct 循环，返回最终响应。
 * 主循环手写数十行，不使用 Spring AI Agent 抽象。
 */
public class ReActLoop {

  public ReActLoop(ProviderService providerService,
                   PromptBuilder promptBuilder,
                   ToolExecutor toolExecutor);

  /**
   * @param session      当前会话（含对话历史）
   * @param userMessage  用户新消息
   * @param profile      Agent 配置（含 maxIterations、maxHistoryTurns）
   * @return 最终响应文本；达到最大轮数时返回含"达到最大轮数"的停止提示
   */
  public String run(Session session, String userMessage, Profile profile);
}
```

### 5. PromptBuilder（oryxos-core）

```java
package com.oryxos.core;

/**
 * 每轮 LLM 调用的 Prompt 组装器。
 * 四部分：①system prompt（角色设定 + ContextLoader 的 Bootstrap + Skill 正文 + 当前日期时间）
 * ②长期记忆（第 22 节接入，未就位留空）③会话历史（最近 maxHistoryTurns 条截断，默认 20）
 * ④当前可用工具列表（经 ToolRegistry 从 Profile.tools 名称解析）。
 */
public class PromptBuilder {

  public PromptBuilder(ContextLoader contextLoader, ToolRegistry toolRegistry);

  public Prompt build(Session session, Profile profile);
}
```

### 6. ToolExecutor（oryxos-core）

```java
package com.oryxos.core;

/**
 * 工具集中执行器。从 ToolRegistry 查找工具 → 执行 → 写 tool_invocations 审计（成功/失败都写）。
 * 执行权只此一处，不得有第二条工具执行路径。
 */
public class ToolExecutor {

  public ToolExecutor(ToolRegistry toolRegistry, ToolInvocationStore toolInvocationStore);

  /**
   * @param sessionId 会话标识（用于审计关联）
   * @param toolCall  LLM 返回的工具调用请求
   * @return 工具执行结果；未知工具/业务失败返回失败结果，工具抛异常则审计后上抛
   */
  public ToolResult execute(String sessionId, ToolCall toolCall);
}
```

### 7. AgentService（oryxos-core）

```java
package com.oryxos.core;

/**
 * 三种触发源（CLI/Web/定时）的统一编排入口。
 * 负责设置/清理 ProfileContext → 驱动 ReActLoop → 持久化 Session。
 */
public class AgentService {

  public AgentService(ProfileRegistry profileRegistry,
                      ReActLoop reActLoop,
                      SessionManager sessionManager);

  /**
   * @param session      当前会话
   * @param userMessage  用户消息
   * @return Agent 最终响应
   * @throws RuntimeException ReActLoop 异常时原样上抛（ProfileContext 在 finally 中保证清理）
   */
  public String process(Session session, String userMessage);
}
```

### 8. ProfileContext（oryxos-core）

```java
package com.oryxos.core;

/**
 * ThreadLocal 持有的当前 Profile，虚拟线程下每请求独立。
 * AgentService 入口 set、出口 clear（finally 保证）。
 */
public final class ProfileContext {
  private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

  public static void set(Profile profile);
  public static Profile current();
  public static void clear();
}
```

### 9. ContextLoader（oryxos-core）

```java
package com.oryxos.core;

import java.nio.file.Path;

/**
 * 上下文加载器：按 Profile 的 bootstrap/skills 字段读取 Bootstrap 与 SKILL.md 正文。
 * 无缓存——每次调用都重新读文件；Bootstrap 缺失 WARN、Skill 引用缺失报错。
 */
public class ContextLoader {

  public ContextLoader(Path workspaceDir);

  /**
   * @param profile Agent Profile（取 bootstrap、skills 字段）
   * @return Bootstrap + SKILL.md 正文拼接文本（角色设定由 PromptBuilder 从 identity.prompt 拼入）
   */
  public String loadSystemPrompt(Profile profile);
}
```

### 10. ToolRegistry（接口定义，第 20 节实现 — oryxos-core）

```java
package com.oryxos.core;

import java.util.Collection;
import java.util.Optional;

public interface ToolRegistry {
  Optional<OryxTool> get(String name);
  Collection<OryxTool> listAll();
}
```

### 11. ToolInvocationStore + ToolInvocationRecord（契约 — oryxos-core，第 20 节起写库）

```java
package com.oryxos.core;

/** 工具审计存储契约（依赖倒置）：core 定义、storage JPA 实现. */
public interface ToolInvocationStore {
  void save(ToolInvocationRecord record);
}

/** 工具审计值对象. */
public record ToolInvocationRecord(
    String sessionId,
    String toolName,
    String inputJson,
    String resultJson,
    boolean success,
    String errorMessage,
    long durationMs,
    java.time.LocalDateTime createdAt) {}
```

### 12. SessionManager（接口定义，第 18 节实现 — oryxos-core）

```java
package com.oryxos.core;

public interface SessionManager {
  void save(Session session);
}
```
