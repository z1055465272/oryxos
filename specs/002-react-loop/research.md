# Research: ReAct 循环引擎 + 编排层 + 上下文供给层

**Feature**: ReAct 循环引擎 | **Date**: 2026-08-03

## 决策记录

### 1. Prompt 模型扩展策略

**Decision**: 扩展现有 `Prompt` record 以支持 system prompt 和会话历史，而非新建类型。

**Rationale**: 第 16 节创建的 `Prompt(String userMessage, List<OryxTool> availableTools)` 仅支持单轮对话。ReActLoop 需要传递完整的 system prompt 和多轮消息历史。扩展 Prompt 而非新建类型，可保持 ProviderService.chat() 签名不变——PromptBuilder 将全部上下文封装进 Prompt，ProviderService 解析后构造 Spring AI 原生 Prompt。

**Alternatives considered**:
- 直接使用 `List<org.springframework.ai.chat.messages.Message>` 替代 Prompt → 违反"不向外部暴露 Spring AI 类型"的设计约束
- 新建 `ReActContext` 专门类型 → 增加不必要的概念，与已有 Prompt 功能重叠

### 2. Session 形态

**Decision**: 本节在 oryxos-core 定义 `Session` 为 record（内存版），含 `sessionId`、`profileName`、`messages`（对话历史列表）、`status`。第 18 节将其升级为 JPA 实体。

**Rationale**: ReActLoop 需要 Session 承载对话历史和审计关联。第 18 节才做 SQLite 持久化，本节先以内存版 Session 跑通循环。定义在 oryxos-core 保证各模块可见。

**Alternatives considered**:
- 在 oryxos-storage 直接建 JPA 实体 → 越界，Session 是核心概念不属于存储层
- 用 `Map<String, Object>` 模拟 Session → 类型不安全，测试难写

### 3. 前向接口定义（ToolRegistry / SessionManager）

**Decision**: 本节在 oryxos-core 定义 `ToolRegistry` 和 `SessionManager` 接口，提供本节需要的签名（`ToolRegistry.get(name)`、`SessionManager.save(session)`）。实现在后续节填充。

**Rationale**: 依赖倒置——引擎层只依赖接口，不依赖具体模块。ToolExecutor 需要从"工具表"查找工具，但这个"表"由第 20 节的 ToolRegistry 实现提供；AgentService 需要持久化 Session，但这个功能由第 18 节的 SessionManager 实现提供。

**Alternatives considered**:
- 在 ToolExecutor/AgentService 内部 mock → 测试代码和产品代码混在一起，后续节接线时改动量大
- 延迟到后续节一起交付 → 本节测试和验收无法独立完成

### 4. ProviderService.chat() 签名

**Decision**: 保持 `chat(String sessionId, Profile profile, Prompt prompt)` 签名不变。Prompt 扩展后携带完整上下文（system prompt + messages + tools），ProviderService 内部解析组装。

**Rationale**: 第 16 节已定签名（H1 保真）。PromptBuilder.build() 的输出作为 ProviderService 的输入，签名无需改变。

### 5. ContextLoader 文件读取策略

**Decision**: 直接用 `java.nio.file.Files.readString()`，无缓存层、无 WatchService。每次 PromptBuilder.build() 都重新读取。

**Rationale**: 课件明确"每次重新读、不缓存"。核心阶段文件量小（几 KB），性能可接受。扩展阶段可加 WatchService 做事件驱动的重载。

### 6. ReActLoop 多工具调用执行策略

**Decision**: LLM 一次响应中返回多个工具调用时，按顺序逐个执行（非并行），每个结果分别回填。

**Rationale**: 课件"有几样先别做"明确列出"工具并行调用"不做。顺序执行简化错误处理，Virtual Thread 下串行开销可接受。

### 7. tool_invocations 建表脚本

**Decision**: V1__init_audit_tables.sql 已含 `tool_invocations` 表 DDL，本次确认列完整即可，无需新建脚本。

**Rationale**: 第 16 节建表脚本预留了 `tool_invocations` 表（V1），列的规格与技术方案 §9.2 一致：`id`、`session_id`、`tool_name`、`input_json`、`result_json`、`success`、`error_message`、`duration_ms`、`created_at`。本节只补充实体+Repository，不新增迁移脚本。

### 8. PromptBuilder 的 Memory 注入占位

**Decision**: PromptBuilder 保留 `MemoryService` 字段和调用点，当 `MemoryService` 为 null 时跳过第二部分（长期记忆）。

**Rationale**: Memory 模块第 22 节交付，但 PromptBuilder 的架构需要为它预留位置。测试中 mock MemoryService 或留 null。
