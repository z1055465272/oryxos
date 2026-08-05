# Research: Memory 模块（第 22 节）

**Branch**: `022-lesson22-memory` | **Date**: 2026-08-05 | **Spec**: [spec.md](spec.md)

## 待解未知项与决策

本节的 Technical Context 无 NEEDS CLARIFICATION 标记（全部技术决策在 plan.md 的 Constitution Check 与 Summary 中落定）。以下记录 Phase 0 对三个关键技术问题的调研结论。

## Decision 1: `MemoryService` 接口放 core，实现放 memory

**Decision**: `MemoryService` 接口 + `MemoryScope` 枚举放 `oryxos-core`（`com.oryxos.core`），`DefaultMemoryService` + `LongTermMemory` + `MemoryTools` 放 `oryxos-memory`。

**Rationale**: 集成点要求 `PromptBuilder`（core）组装时调 `MemoryService`。core 不依赖任何下游模块（禁止循环依赖），故契约接口必须进 core。这与既有依赖倒置模式完全同构：`ProviderService`（core 接口 / provider 实现）、`SessionManager`（core 接口 / storage 实现）、`ToolInvocationStore`（core 接口 / storage 实现）。

**Alternatives considered**:
- MemoryService 接口放 memory 模块 → 需要 core 依赖 memory，制造循环依赖。否决。
- 不引入接口、PromptBuilder 直接调 LongTermMemory → 违反"接口墙"（课件二·第一点：PromptBuilder 全程只认 MemoryService 一个接口，不直接碰 MEMORY.md）。否决。
- 通过 Optional/Supplier 注入 memory 内容 → 复杂化且丢失类型安全。否决。

**Evidence**: `docs/TechnicalSolution.md` §5.1 "对 ReAct 循环只暴露一个 `MemoryService` 接口"；代码库 `com.oryxos.core.ProviderService` / `com.oryxos.provider.DefaultProviderService` 同构。

## Decision 2: `MemoryTools` 经组合根注册进 `DefaultToolRegistry`

**Decision**: 在 `CliSpringBootstrap`（cli 模块，组合根，同时可见 tool 与 memory）里用 `ToolCallbacks.from(memoryTools)` 生成 `@Tool` schema，以本地小适配器包装成 `OryxTool` 注册进 `DefaultToolRegistry`。`@ComponentScan` 增加 `com.oryxos.memory`。

**Rationale**: memory 与 tool 模块之间无依赖通路（memory→core、tool→core，二者互不依赖）。工具注册必须在同时依赖两者的组合根完成。`ToolCallbacks.from(...)` + 适配器包装成 `OryxTool` 与 `BuiltinToolRegistration.ToolCallbackOryxTool`（20 节交付）机制同构，不修改 20 节已交付的 `registerAll` 签名。

**Alternatives considered**:
- memory 模块加依赖 tool 模块 → 新增模块间依赖，且 tool 被 memory 反向引用语义混乱。否决。
- 复用 `BuiltinToolRegistration.registerAll` → 需 tool→memory 依赖。否决。
- 把 `ToolCallbackOryxTool` 公开复用 → 修改 20 节交付类。否决（保持前序交付物不动）。

**Evidence**: `oryxos-tool/pom.xml` 用 `spring-ai-model`；`CliSpringBootstrap` 是 chat/serve/gateway 共用组合根，已手装配 `PromptBuilder`/`ToolExecutor` 等 POJO 引擎。

## Decision 3: `MemoryService.buildContext` 的组装形态与 PromptBuilder 集成

**Decision**: `String buildContext(Session session, Profile profile)` 返回"核心记忆全文 + 归档记忆截断段 + 会话历史摘要"三段拼接文本；`PromptBuilder.buildSystemMessage` 追加该文本。归档区经 `LongTermMemory.load()` 天然截断，故"归档区不整体注入"由 `truncateIfNeeded` 保证（超阈值只保留最近 4000 字）。

**Rationale**: 课件接口示意为 `buildContext(Session)`，但代码库 `PromptBuilder.build(Session, Profile)` 需要 Profile（拿不到 workspace 路径会错）。H1 已定字面量优先 → 双参签名是代码库既有形态的适配。记忆内容拼入 system prompt（课件："把返回的内容拼进 system prompt 里"）。

**Alternatives considered**:
- 严格单参 `buildContext(Session)` → 实现拿不到 workspace 目录，LongTermMemory 路径无法解析。否决。
- 记忆内容不进 system、另作 messages 前缀 → 偏离课件"拼进 system prompt"的明确要求。否决。

**Evidence**: 课件第三·集成点；代码库 `PromptBuilder.buildSystemMessage(Profile)`（本节改为收 Session+Profile）。
