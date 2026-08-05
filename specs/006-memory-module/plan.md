# Implementation Plan: Memory 模块（第 22 节）

**Branch**: `022-lesson22-memory` | **Date**: 2026-08-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-memory-module/spec.md`

## Summary

交付 Memory 能力：一个 `MemoryService` 门面对 ReAct 循环只暴露统一记忆读写接口（内部把会话记忆委托给 `SessionManager`、长期记忆委托给 `LongTermMemory`），外加 `save_memory` / `recall_memory` 两个内置 Tool 让 Agent 自己读写长期记忆。长期记忆持久化到 `.oryxos/memory/MEMORY.md`，按 `## 核心记忆` / `## 归档记忆` 两个二级标题分区；核心区永远完整不截断，归档区超 4000 字截断；每次组装 Prompt 重新读文件、不缓存。集成点：`PromptBuilder`（17 节交付）组装时调 `MemoryService` 把长期记忆拼进 system prompt。

## Technical Context

**Language/Version**: Java 21（virtual thread 并发，本项目固定）

**Primary Dependencies**: Spring Boot 3.x、Spring AI 1.0.9（`spring-ai-model` 的 `@Tool` 注解 schema 生成，宪法 II——只用 schema 生成、禁用自动 tool 执行）、SLF4J。文件 IO 用 JDK 21 NIO（`Files.readString/writeString`），无需新第三方依赖。

**Storage**: 长期记忆 → `.oryxos/memory/MEMORY.md` Markdown 文件（手工双区块约定）；会话记忆复用第 18 节 SQLite `SessionManager`（本节不改表、不加表）。

**Testing**: JUnit 5 + AssertJ（`spring-boot-starter-test`）。`@TempDir` 临时目录测文件 IO，全单测。集成冒烟打 `@Tag("integration")` CI 跳过。

**Target Platform**: 本地 CLI / 服务器（`oryxos-chat` / `serve` / `gateway` 共用 `CliSpringBootstrap` 组合根）

**Project Type**: Java 多模块 Maven 单体

**Performance Goals**: 长期记忆每次组装 Prompt 读一个小文件，性能可接受；无缓存（契约一）

**Constraints**: 核心模块 `oryxos-core` 不依赖任何下游模块；禁止模块间循环依赖；`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）

**Scale/Scope**: 单文件长期记忆（核心 + 归档两区块，归档阈值 4000 字），单机

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 |
|------|------|
| I. 自实现 ReAct Loop | 不触碰 `ReActLoop` 主循环；只在其调用的 `PromptBuilder` 里加记忆注入。通过 |
| II. Spring AI 只用两件事 | `MemoryTools` 用 `@Tool` 注解生成 schema；执行仍由 `ToolExecutor` 走既有 `@Tool` Bean → `ToolCallbacks` → `OryxTool` 路径，不引入自动执行。通过 |
| III. Provider 显式映射 | 不涉及。通过 |
| IV. 目录=Agent / Skill 加载 | 不涉及（Memory 不动 ContextLoader）。通过 |
| V. 审计 Day One | MemoryTools 走 `ToolExecutor.execute` → 自动落 `tool_invocations` 审计（执行权只此一处）。通过 |
| VI. Sandbox | `save_memory`/`recall_memory` 是纯本地文件读写（白名单内 `.oryxos/memory/`），不涉外 IO；不引入 Sandbox.enforce（课件未列为 24 节改造点之外的接线点）。通过 |
| VII. 同步执行 | 全部同步阻塞，无 Reactor/CompletableFuture。通过 |
| VIII. Tool 模块三合一 | 不拆 Tool 模块。通过 |

**声明两个本节的强制设计决策**（宪法 v1.1.0"新建/改名必须在 plan 里声明理由"）：

1. **`MemoryService` 接口 + `MemoryScope` 枚举放 `oryxos-core`，实现放 `oryxos-memory`**。理由：集成点要求 `PromptBuilder`（core）组装时调 `MemoryService`，而 core 不能依赖 memory（禁止循环依赖）。这强制契约接口进 core——与既有 `ProviderService`（core 接口 / provider 实现）、`SessionManager`（core 接口 / storage 实现）、`ToolInvocationStore`（core 接口 / storage 实现）完全同构。课件"本节交付物"写"MemoryService 接口 + 实现"，未限定模块；skill 落位表"全部→oryxos-memory"是粗略映射，本决策是对其的强制修正。`com.oryxos.memory.MemoryService` 的 init 脚手架桩（非前序交付物，三个方法签名与课件不符）将被删除，替换为 core 接口 + memory 实现。
2. **`PromptBuilder` 构造函数新增 `MemoryService` 参数、`buildSystemMessage` 改收 `Session`**。这是当节课件明确列的"集成点"（改造点），授权修改 17 节交付的 `PromptBuilder`。`PromptBuilderTest`（17 节）同步更新传 mock。

**复杂性说明**：`MemoryTools`（memory 模块）注册进 `DefaultToolRegistry`（tool 模块）——memory 与 tool 之间无依赖通路，无法在任一模块内部完成。解决方案：在组合根 `CliSpringBootstrap`（cli 模块，同时可见 tool 与 memory）里用 `ToolCallbacks.from(memoryTools)` 生成 schema、以本地小适配器包装成 `OryxTool` 注册进 `DefaultToolRegistry`，与 `BuiltinToolRegistration` 既有模式同构（见 `oryxos-tool/pom.xml` 用 `spring-ai-model` 的 `@Tool` 机制）。不修改 `BuiltinToolRegistration`（20 节交付）的现有 `registerAll` 签名。

## Project Structure

### Documentation (this feature)

```text
specs/006-memory-module/
├── plan.md              # This file
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
├── contracts/           # Phase 1 输出
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成）
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── MemoryService.java        # 新增：统一记忆门面接口（buildContext/remember/recall）
├── MemoryScope.java          # 新增：CORE / ARCHIVAL 枚举
└── PromptBuilder.java        # 修改：构造器 + MemoryService；buildMemorySection 接真实记忆

oryxos-memory/src/main/java/com/oryxos/memory/
├── DefaultMemoryService.java # 新增：MemoryService 实现（LongTermMemory + buildContext 组装）
├── LongTermMemory.java       # 新增：MEMORY.md 读写（append/load/recallByKeyword/truncateIfNeeded）
├── MemoryTools.java          # 新增：@Tool save_memory / recall_memory
└── package-info.java         # 修改：更新模块说明（删除旧 MemoryService 描述）

oryxos-memory/src/test/java/com/oryxos/memory/
├── LongTermMemoryTest.java   # 新增：坑一~坑四回归
├── MemoryToolsTest.java      # 新增：scope 缺省 / 未命中不抛
└── MemoryServiceTest.java    # 新增：buildContext 组合

oryxos-core/src/test/java/com/oryxos/core/
└── PromptBuilderTest.java    # 修改：构造器传 mock；新增记忆注入断言

oryxos-cli/src/main/java/com/oryxos/cli/
├── CliSpringBootstrap.java   # 修改：@Bean 装配 LongTermMemory/DefaultMemoryService/MemoryTools + 注册；@ComponentScan 加 com.oryxos.memory
└── InitCommand.java          # 修改：MEMORY.md 模板改为两区块约定（本节交付物"文件"项）
```

**Structure Decision**: 沿用既有依赖倒置结构（契约接口在 core、实现在下游模块）。内存模块 `oryxos-memory` 只依赖 `oryxos-core`；组合根 `oryxos-cli` 新增对 `oryxos-memory` 的依赖以完成装配与工具注册。

## Complexity Tracking

> 无宪法违规需要 justify。上述两个决策均在 Constitution Check 中声明了理由，不属于违规。

## Phase 0 / Phase 1 artifacts

见 `research.md`、`data-model.md`、`contracts/`、`quickstart.md`。
