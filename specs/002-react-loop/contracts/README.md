# Contracts: ReAct 循环引擎

**Feature**: ReAct 循环引擎 | **Date**: 2026-08-03

## 公共接口契约

### 1. ReActLoop

```java
package com.oryxos.core;

/**
 * Agent 的核心循环引擎。
 * 输入 Session + 用户消息 + Profile，驱动 ReAct 循环，返回最终响应。
 */
public class ReActLoop {

    /**
     * @param providerService LLM Provider（第 16 节交付）
     * @param promptBuilder Prompt 组装器
     * @param toolExecutor 工具执行器
     */
    public ReActLoop(ProviderService providerService,
                     PromptBuilder promptBuilder,
                     ToolExecutor toolExecutor);

    /**
     * 运行 ReAct 循环。
     *
     * @param session 当前会话（含对话历史）
     * @param userMessage 用户新消息
     * @param profile Agent 配置（含 maxIterations、maxHistoryTurns 等）
     * @return 最终响应文本
     */
    public String run(Session session, String userMessage, Profile profile);
}
```

### 2. PromptBuilder

```java
package com.oryxos.core;

/**
 * 每轮 LLM 调用的 Prompt 组装器。
 * 按四部分顺序拼接：system prompt → 长期记忆 → 会话历史 → 工具列表。
 */
public class PromptBuilder {

    /**
     * @param contextLoader 上下文加载器（Bootstrap + AGENT.md + Skill）
     * @param memoryService 长期记忆服务（第 22 节就位前可为 null）
     */
    public PromptBuilder(ContextLoader contextLoader, MemoryService memoryService);

    /**
     * 组装本轮 Prompt。
     *
     * @param session 当前会话（取对话历史）
     * @param profile Agent 配置（取 tools 子集、maxHistoryTurns）
     * @return 组装好的 Prompt（含 system prompt + 历史 + 工具）
     */
    public Prompt build(Session session, Profile profile);
}
```

### 3. ToolExecutor

```java
package com.oryxos.core;

/**
 * 工具集中执行器。从 ToolRegistry 查找工具 → Sandbox 检查 → 执行 → 写审计。
 */
public class ToolExecutor {

    /**
     * @param toolRegistry 工具注册表
     * @param toolInvocationRepository 审计 Repository
     * @param sandbox Sandbox 检查器（第 24 节就位前可为 null，检查跳过）
     */
    public ToolExecutor(ToolRegistry toolRegistry,
                        ToolInvocationRepository toolInvocationRepository,
                        Sandbox sandbox);

    /**
     * 执行一次工具调用。
     *
     * @param sessionId 会话标识（用于审计关联）
     * @param toolCall LLM 返回的工具调用请求
     * @return 工具执行结果
     */
    public ToolResult execute(String sessionId, ToolCallRequest toolCall);
}
```

### 4. AgentService

```java
package com.oryxos.core;

/**
 * 三种触发源（CLI/Web/定时）的统一编排入口。
 * 负责设置/清理 ProfileContext → 驱动 ReActLoop → 持久化 Session。
 */
public class AgentService {

    /**
     * @param profileRegistry Profile 注册表
     * @param reActLoop ReAct 循环
     * @param sessionManager Session 管理器
     */
    public AgentService(ProfileRegistry profileRegistry,
                        ReActLoop reActLoop,
                        SessionManager sessionManager);

    /**
     * 处理一次用户消息。三种触发源最终都走此入口。
     *
     * @param session 当前会话
     * @param userMessage 用户消息
     * @return Agent 最终响应
     * @throws RuntimeException ReActLoop 异常时原样上抛（ProfileContext 在 finally 中保证清理）
     */
    public String process(Session session, String userMessage);
}
```

### 5. ProfileContext

```java
package com.oryxos.core;

/**
 * ThreadLocal 持有的当前 Profile，虚拟线程下每请求独立。
 * AgentService 入口 set、出口 clear（finally 保证）。
 */
public final class ProfileContext {
    private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

    /** 设置当前线程的 Profile。仅 AgentService 调用。 */
    public static void set(Profile profile);

    /** 获取当前 Profile。工具/服务可在执行期间调用。 */
    public static Profile current();

    /** 清除当前 Profile。AgentService finally 块中调用。 */
    public static void clear();
}
```

### 6. ContextLoader

```java
package com.oryxos.core;

import java.nio.file.Path;

/**
 * 上下文加载器：读取 Bootstrap + AGENT.md 正文 + Skill 元数据。
 * 无缓存——每次调用都重新读文件。
 */
public class ContextLoader {

    /**
     * @param workspaceDir .oryxos/ 工作区根路径
     */
    public ContextLoader(Path workspaceDir);

    /**
     * 加载当前 Profile 的完整上下文。
     *
     * @param profile Agent Profile（取 bootstrap、skills 字段）
     * @return 拼接好的 system prompt 文本（Bootstrap + AGENT.md 正文 + Skill 元数据 + 当前日期时间）
     */
    public String loadSystemPrompt(Profile profile);
}
```

### 7. ToolRegistry（接口定义，第 20 节实现）

```java
package com.oryxos.core;

import java.util.Collection;
import java.util.Optional;

public interface ToolRegistry {
    /** 按名称查找工具。 */
    Optional<OryxTool> get(String name);

    /** 列出全部已注册工具。 */
    Collection<OryxTool> listAll();
}
```

### 8. SessionManager（接口定义，第 18 节实现）

```java
package com.oryxos.core;

import java.util.Optional;

public interface SessionManager {
    /** 持久化 Session。 */
    void save(Session session);

    /** 按 ID 查找 Session。 */
    Optional<Session> findById(String sessionId);
}
```

### 9. MemoryService（接口定义，第 22 节实现）

```java
package com.oryxos.core;

/**
 * 记忆统一门面。第 22 节完整实现，本节只定义接口。
 */
public interface MemoryService {
    /** 加载完整记忆上下文（注入 PromptBuilder 第二部分）。 */
    String loadMemoryContext(String profileName);
}
```

### 10. Sandbox（接口定义，第 24 节实现）

```java
package com.oryxos.core;

/**
 * Sandbox 接口。第 24 节完整实现，本节只定义接口。
 */
public interface Sandbox {
    void enforce(SandboxAction action);
}
```
