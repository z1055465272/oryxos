# 契约：`ReActLoop` / `PromptBuilder` / `ToolExecutor` / `AgentService`（能力二）

**关联实体**: [Session](../data-model.md#session)、[Message](../data-model.md#message)、[Profile](../data-model.md#profile)

**来源**: 技术方案 §4.1 / §4.2，宪法原则一、二、五、七

---

## `ReActLoop`（自实现主循环，宪法原则一）

```java
class ReActLoop {
    AssistantMessage run(Session session, Profile profile, String userText);
}
```

**算法**（技术方案 §4.1，核心逻辑约数十行 Java，不依赖 Spring AI Agent 抽象）：

1. 用户消息追加到 Session 对话历史
2. `PromptBuilder` 组装 Prompt（本周：system prompt（Profile 描述 + 当前日期）+ 对话历史（`max_history_turns` 截断）+ Tool 列表）
3. `ProviderService.call(profile, prompt)` 调 LLM（写 `LlmCallRecorder`）
4. **无 tool call** → 返回最终 `ASSISTANT_TEXT`
5. **有 tool call** → `ToolExecutor.execute(...)`（写 `ToolInvocationRecorder`），结果作为 `TOOL_RESULT` 追加对话历史
6. 回到步骤 2 继续；达到 `max_iterations`（默认 10）强制收敛，返回基于已有信息的回答

**关键约束**：
- **同步阻塞**（宪法原则七）：全程无 Reactor/CompletableFuture，虚拟线程处理并发
- **禁用 Spring AI 自动 tool 执行**（宪法原则二）：`ChatResponse.getToolCalls()` 手动取 tool call，`ToolExecutor` 手动执行
- **一次响应多个 tool call 按顺序执行**（技术方案 §4.3：不做并行）

## `PromptBuilder`

```java
class PromptBuilder {
    Prompt build(Profile profile, Session session);
}
```

**组装顺序**（本周子集）：
1. **system prompt**：Profile 描述（最小 AGENT.md frontmatter 派生）+ 当前日期时间行
2. **对话历史**：`messages` 按 `max_history_turns` 截断保留最近
3. **Tool 列表**：`ToolRegistry.forProfile(profile)` 转 Function Calling 格式（`FunctionCallingAdapter`）

Memory 注入（能力三）与 Bootstrap（`ContextLoader`）属后续周次。

## `ToolExecutor`

```java
class ToolExecutor {
    ToolResult execute(Profile profile, ToolCall tc);
}
```

1. `ToolRegistry.get(tc.name)` 找 Tool
2. **Sandbox 检查**（在 Tool 内部 `execute` 开头调用 `Sandbox.enforce`）
3. 执行 Tool，返回 `ToolResult`
4. 写 `ToolInvocationRecorder`（成功/失败都写）
5. 失败按 `retryable` 策略返回错误信息给 LLM

## `AgentService`（统一入口）

```java
class AgentService {
    String process(Session session, String userText);
}
```

一次处理的编排者：
1. 取 `ProfileRegistry.get(session.profileName)`（本周 CLI 场景单一 Profile）
2. `ReActLoop.run(session, profile, userText)` 跑完整循环
3. 返回最终响应文本

## 审计记录（宪法原则五）

- 每次 LLM 调用 → `LlmCallRecorder`（provider/model/tokens/duration）
- 每次 Tool 调用 → `ToolInvocationRecorder`（tool/input/result/success/duration）
- 本周内存实现，第四周 SQLite；接口定义在 core，实现可替换
