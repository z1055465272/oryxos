# Contracts: Memory 模块（第 22 节）

**Branch**: `022-lesson22-memory` | **Date**: 2026-08-05 | **Spec**: [spec.md](../spec.md)

## Contract 1: MemoryService 门面契约（接口在 core，实现在 memory）

```text
// oryxos-core 新增
enum MemoryScope { CORE, ARCHIVAL }

interface MemoryService {
    String buildContext(Session session, Profile profile);  // 给 PromptBuilder：核心记忆 + 归档截断 + 会话历史
    void remember(String content, MemoryScope scope);       // 给 MemoryTools.save_memory
    List<String> recall(String keyword);                    // 给 MemoryTools.recall_memory
}

// oryxos-memory 新增实现
class DefaultMemoryService implements MemoryService {
    // 持有 LongTermMemory
}
```

**行为契约**：
- `buildContext` 返回核心记忆全文 + 归档记忆截断段 + 会话历史摘要的三段拼接文本；归档区不整体注入（超阈值只保留最近 4000 字）。
- `remember(content, scope)`：`scope` 缺省为 `ARCHIVAL`；`CORE` 写核心区（永不截断）、`ARCHIVAL` 写归档区。
- `recall(keyword)`：只在归档区做包含匹配；命中返回匹配行，未命中返回空列表（不抛异常，由 Tool 层转"没有找到相关记忆"）。

## Contract 2: LongTermMemory 契约（oryxos-memory）

```text
class LongTermMemory {
    void append(String content, MemoryScope scope);       // 按 scope 追加到对应区块
    String load();                                        // 核心区完整 + 归档区截断；每次重新读文件不缓存
    List<String> recallByKeyword(String keyword);         // 只搜归档区，包含匹配
    static String truncateIfNeeded(String archiveSection); // 归档段 >4000 字截取最近 4000 字
}
```

**行为契约**（对应课件四坑）：
- 坑一（不缓存）：`append` 后同进程内下一次 `load`/`recallByKeyword` 立即可见。
- 坑二（截断只裁归档）：`truncateIfNeeded` 只接收归档段文本，核心区物理上不可能被裁；归档超阈值时最早内容被裁、最近内容保留。
- 坑三（scope 路由）：`append(CORE)` 落 `## 核心记忆` 区块、`append(ARCHIVAL)` 落 `## 归档记忆` 区块。
- 坑四（朴素检索）：`recallByKeyword` 只搜归档区、`String.contains` 行匹配。

## Contract 3: MemoryTools 内置 Tool（oryxos-memory，`@Tool` 注册）

```text
@Component
class MemoryTools {
    @Tool(name = "save_memory", description = "记住一件值得长期记住的事")
    ToolResult saveMemory(String content, String scope);

    @Tool(name = "recall_memory", description = "按关键词检索长期记忆")
    ToolResult recallMemory(String keyword);
}
```

**行为契约**：
- `scope` 缺省/非法按 `ARCHIVAL` 处理（课件坑三：写入靠参数不靠猜）；返回成功结果。
- `recall_memory` 未命中返回内容为"没有找到相关记忆"的结果，不抛异常。
- 经组合根 `CliSpringBootstrap` 用 `ToolCallbacks.from(memoryTools)` 生成 schema 注册进 `DefaultToolRegistry`，`ToolExecutor` 执行时自动落 `tool_invocations` 审计。

## Contract 4: PromptBuilder 集成点（17 节改造点）

```text
// 构造器新增 MemoryService 参数
class PromptBuilder {
    PromptBuilder(ContextLoader contextLoader, ToolRegistry toolRegistry, MemoryService memoryService);
}
```

**行为契约**：`buildSystemMessage` 在角色设定 + Bootstrap/Skill 之后追加 `memoryService.buildContext(session, profile)` 返回的记忆文本（非空时）。`PromptBuilderTest` 构造器传 mock。
