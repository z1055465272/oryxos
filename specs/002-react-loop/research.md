# Research: ReAct 循环引擎 + 编排层 + 上下文供给层

**Feature**: ReAct 循环引擎 | **Date**: 2026-08-03

## 决策记录

### 1. ProviderService 契约入 core + 自有 Response（关键架构修正）

**Decision**: 把 `ProviderService` 接口从 oryxos-provider 迁入 oryxos-core，`chat` 返回自有 `Response`（`text()` / `hasToolCalls()` / `toolCalls()`）+ `ToolCall` 值对象；`resolve(String)` 移到 `DefaultProviderService` 具体类。provider 模块的 `DefaultProviderService` 负责 Spring AI `ChatResponse` → core `Response` 的转换。

**Rationale**: ReActLoop 归 oryxos-core，它调 LLM 不能反向依赖 provider（模块循环依赖）；core 也不应引入 spring-ai-model（保持 Spring-agnostic）。课件第 17 节骨架正是 `Response resp = providerService.chat(...)`、`resp.hasToolCalls()/toolCalls()/text()`——自有 Response 使实现与课件逐行对齐，且 ReActLoopTest 直接用 core Response 构造响应、无需 mock Spring AI 类型。用户裁定采纳本方案。

**Alternatives considered**:
- ProviderService 移入 core 但保持 `ChatResponse` 返回 → core 需依赖 spring-ai-model，核心抽象耦合框架类型（否决）
- core 新增 `LlmGateway` 桥接接口，ProviderService 原样保留 → 不碰第 16 节但引入第二个 LLM 抽象，耦合未消除（否决）

### 2. Prompt 模型扩展（向后兼容）

**Decision**: 扩展现有 `Prompt` record：新增 `systemMessage` + `messages`（`List<Session.Message>`），保留 `userMessage` + `availableTools` 与旧构造器（`Prompt(String)`、`Prompt(String, List<OryxTool>)`）向后兼容。

**Rationale**: 第 16 节 `Prompt` 仅支持单轮。ReAct 多轮必须传递完整 system prompt 与多轮消息（含 assistant tool_calls 与 tool 结果，模型靠消息角色与 tool_call_id 配对续写）。扩展而非新建类型，保持 `ProviderService.chat` 参数类型不变；旧构造器保证第 16 节测试不破。

**Alternatives considered**:
- 直接使用 Spring AI `Message` 列表 → core 耦合 Spring AI（否决）
- 新建独立 `ReActContext` 类型 → 与 Prompt 功能重叠（否决）

### 3. Session 形态（内存版）

**Decision**: 本节在 oryxos-core 定义 `Session`（可变类）：`sessionId`、`profileName`、`channel`、`userId`、`List<Message>`、`status`；消息模型用 sealed `Message`（`UserMessage` / `AssistantMessage(content, toolCalls)` / `ToolResultMessage(toolCallId, toolName, content)`），`ToolCall(id, name, arguments)` 独立值对象。第 18 节升级持久化。

**Rationale**: ReActLoop 需要会话承载对话历史与审计关联；第 18 节才做 SQLite 持久化，本节以内存版跑通循环。sealed 消息模型让 ProviderService 能无损重建 Spring AI 消息（角色/工具调用配对）。

**Alternatives considered**:
- 直接在 storage 建 JPA 实体 → 越界，Session 是核心概念（否决）
- `Map<String,Object>` 模拟 → 类型不安全（否决）

### 4. 前向接口定义（ToolRegistry / SessionManager / ToolInvocationStore）

**Decision**: 本节在 oryxos-core 定义 `ToolRegistry`（`get`/`listAll`）、`SessionManager`（`save`）、`ToolInvocationStore`（`save`）+ `ToolInvocationRecord` 值对象三个接口/值对象，实现在后续节/本模块填充。

**Rationale**: 依赖倒置——引擎层只依赖接口。ToolExecutor 从"工具表"查找工具（第 20 节实现）；AgentService 持久化 Session（第 18 节实现）；工具审计写入 tool_invocations（第 20 节内置 Tool 起，本节先建契约 + storage JPA 实现）。

**Alternatives considered**:
- 在引擎类内部直接依赖具体实现 → 测试无法 mock、接线时改动大（否决）

### 5. ProviderService.chat 消息序列适配

**Decision**: `DefaultProviderService.chat` 把 `Prompt` 的 `systemMessage` + `messages` 映射成 Spring AI 消息序列（`SystemMessage` / `UserMessage` / `AssistantMessage(toolCalls)` / `ToolResponseMessage`），再调 `ChatModel.call`；`messages` 为空时回退单条 `UserMessage(userMessage)`（第 16 节兼容路径）。

**Rationale**: 第 16 节实现只发单条 UserMessage，ReAct 多轮会丢上下文。映射规则：`Session.UserMessage→UserMessage`、`AssistantMessage→AssistantMessage(含 ToolCall)`、`ToolResultMessage→ToolResponseMessage(id/name/responseData)`，保证工具调用链在协议层配对。仍走 `OpenAiChatOptions.internalToolExecutionEnabled(false)` 关闭自动执行。

### 6. ContextLoader 文件读取策略（Skill 正文预载，用户裁定）

**Decision**: ContextLoader 按 Profile 的 `bootstrap` 字段读 `<workspace>/<name>`（缺失 WARN 跳过），按 `skills` 字段读 `<workspace>/skills/<name>/SKILL.md` **并预载正文拼入 system prompt**（缺失报错）；每次调用重新读文件、无缓存、无 WatchService。

**Rationale**: 用户裁定"按课件来"（宪法原则四修正为 v1.2.0）：核心阶段 Skill 正文预载，附件参考/脚本仍按需经 read_file/shell 取。无缓存保证"改完立即生效"，与课件 harness（改文件下次 build 立即读到新内容）一致。

**Alternatives considered**:
- 只注入 Skill 元数据（name/description/路径）不预载正文 → 宪法原 v1.1.0 方案，用户裁定推迟到第 29 节软连接落地时启用（否决）

### 7. ReActLoop 多工具调用执行策略

**Decision**: LLM 一次响应中返回多个工具调用时，按顺序逐个执行（非并行），每个结果分别回填 Session。

**Rationale**: 课件"有几样先别做"明确列出"工具并行调用"不做。顺序执行简化错误处理，Virtual Thread 下串行开销可接受。

### 8. tool_invocations 建表脚本

**Decision**: 复用 V1__init_audit_tables.sql 已有的 `tool_invocations` 表 DDL（列：id/session_id/tool_name/input_json/result_json/success/error_message/duration_ms/created_at），本节补实体 + Repository + Store 实现；测试 schema.sql 同步补表。

**Rationale**: 第 16 节建表脚本已预留 tool_invocations 表，列的规格与技术方案 §9.2 一致，无需新建迁移脚本。

### 9. PromptBuilder 的 Memory 注入占位

**Decision**: PromptBuilder 不引用 `MemoryService`（core 不依赖 oryxos-memory 模块，且 `com.oryxos.memory.MemoryService` 已存在于 memory 模块，避免重复接口），记忆部分（第二部分）用私有占位方法留空，注明第 22 节接入。

**Rationale**: 核心阶段记忆模块未就位，FR-002 允许"未就位时留空"；不新建 core 版 MemoryService 避免与既有 `com.oryxos.memory.MemoryService` 概念重复冲突。

**Alternatives considered**:
- 在 core 定义 `MemoryService` 接口 → 与 memory 模块既有同名接口重复（否决）

### 10. 工具执行失败的处理口径

**Decision**: ToolExecutor 对工具执行分两类：工具返回 `ToolResult.fail`（业务失败）→ 写审计 success=false + errorMessage，返回该结果不抛；工具 `execute` 抛 RuntimeException → 写审计 success=false + 异常信息，**再上抛**（不吞）。

**Rationale**: 课件 ToolExecutorTest 验收"失败也写 success=false 带原因，异常不吞"。审计在异常路径也先落库再上抛，与第 16 节 Provider 的失败审计同口径。

### 11. 未知工具名处理

**Decision**: ToolRegistry 查不到工具名时，写审计 success=false + "unknown tool"，返回 `ToolResult.fail` 而非抛异常。

**Rationale**: spec 边界"工具执行器收到不存在的工具名称时返回失败结果而非抛异常"——模型幻觉调了不存在的工具，要让循环能以失败结果继续而不是整个崩掉。
