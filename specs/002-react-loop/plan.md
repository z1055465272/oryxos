# Implementation Plan: ReAct 循环引擎 + 编排层 + 上下文供给层

**Branch**: `017-lesson17-react-loop` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-react-loop/spec.md`

## Summary

实现 OryxOS 的 ReAct 循环引擎（`ReActLoop`），Agent 最核心的推理-行动调度器。循环每轮经`PromptBuilder`组装四部分上下文（角色设定+Bootstrap+Skill 正文 → 长期记忆(留空) → 会话历史截断 → 可用工具），经第 16 节 `ProviderService` 调一次大模型，再由`ToolExecutor`集中执行模型要求的工具调用并回填结果，直到模型不再要求工具或达到最大轮数。上层由`AgentService`统一编排三种触发源（CLI/Web/定时），经`ProfileContext`（ThreadLocal）传递当前 Agent 配置。`ContextLoader`提供无缓存的上下文加载（Bootstrap + SKILL.md 正文预载）。

**关键架构修正（相对第 16 节）**：`ProviderService` 接口移入 oryxos-core，`chat` 返回自有 `Response`（`hasToolCalls()/toolCalls()/text()`，与课件第 17 节骨架逐行对齐）；core 不依赖 Spring AI 类型、不产生 core→provider 循环依赖。`Prompt` 向后兼容扩展承载 system prompt + 多轮消息。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.x, Spring AI Alibaba（仅协议转换，BOM 1.0.9 已锁定）, Spring Data JPA, SQLite, SLF4J/Logback

**Storage**: SQLite（`tool_invocations` 表 DDL 已在 V1__init_audit_tables.sql 预留，本节补实体+Repository+Store）; 文件系统（`.oryxos/` 下 Bootstrap/SKILL.md，ContextLoader 读取）

**Testing**: JUnit 5 + Mockito；五个核心单测类 + 一个存储单测类（全 mock / 临时目录，不碰网络）；`mvn clean verify` 含 P3C/SpotBugs/FindSecBugs/PMD 全绿即通过

**Target Platform**: JDK 21 单二进制 Spring Boot 应用

**Project Type**: Maven 多模块（本节修改 `oryxos-core` + `oryxos-storage` + `oryxos-provider`）

**Performance Goals**: 代码课单测秒级跑完; ReActLoop 主循环约 40~60 行 Java

**Constraints**: 同步阻塞 + Virtual Thread; 禁用 Spring AI 自动 tool 执行; 不碰异步编程模型; 避开 P3C/ASM 解析不了的 Java 18+ 语法; core 不依赖 Spring AI 类型

**Scale/Scope**: 核心阶段代码课交付；最大迭代轮数默认 10；历史截断默认 20 条消息；新增约 16 个 Java 文件、修改 5 个既有文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 自实现 ReAct Loop | ✅ PASS | 本节核心交付物；主循环手写数十行，不依赖 Spring AI Agent 抽象 |
| II. Spring AI 只用两件事 | ✅ PASS | ProviderService 只做协议转换; ToolExecutor 集中执行；ReActLoop 用自有 Response 判断工具调用，禁用自动 tool 执行 |
| III. Provider 显式映射 | ✅ PASS | 不新增 Provider；`resolve` 保留在 DefaultProviderService，显式映射表不动 |
| IV. 一个目录=一个Agent | ✅ PASS | ContextLoader 按 Profile 的 bootstrap/skills 字段读取，Skill 正文预载（宪法 v1.2.0 修正） |
| V. 审计表 Day One 写入 | ✅ PASS | `tool_invocations` 表本节交付；ToolExecutor 每次执行无论成败都经 ToolInvocationStore 写入 |
| VI. 沙箱白名单 | ⏸️ NOT APPLICABLE | Sandbox 24 节就位；本节 ToolExecutor 留调用位并注明接线节次 |
| VII. 同步执行模型 | ✅ PASS | 全程同步阻塞，Virtual Thread 并发 |
| VIII. Tool 模块三合一 | ✅ PASS | 不修改 oryxos-tool 模块；ToolExecutor 放 oryxos-core |

**Gate Result**: PASS — 无违规，可进入 Phase 0。

## Project Structure

### Documentation (this feature)

```text
specs/002-react-loop/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
├── contracts/           # Phase 1 输出
└── tasks.md             # Phase 2 输出（/speckit-tasks）
```

### Source Code (repository root)

```text
oryxos-core/
├── src/main/java/com/oryxos/core/
│   ├── Profile.java               # [已有，本次不改]
│   ├── ProfileRegistry.java       # [已有，本次不改]
│   ├── OryxTool.java              # [已有]
│   ├── ToolResult.java            # [已有]
│   ├── Prompt.java                # [修改] 扩展 systemMessage + messages（多轮），保留旧构造器向后兼容
│   ├── ToolCall.java              # [新增] 工具调用值对象（id/name/arguments）
│   ├── Response.java              # [新增] LLM 响应值对象（text/toolCalls/hasToolCalls）
│   ├── ProviderService.java       # [迁移+修改] 接口移入 core，chat 返回 Response（resolve 移到 DefaultProviderService）
│   ├── Session.java               # [新增] 会话（内存版，含消息累积；第 18 节持久化）
│   ├── ToolRegistry.java          # [新增] Tool 注册表接口（第 20 节实现）
│   ├── ToolInvocationStore.java   # [新增] 工具审计存储接口（第 20 节起写 tool_invocations）
│   ├── ToolInvocationRecord.java  # [新增] 工具审计值对象
│   ├── SessionManager.java        # [新增] 会话管理接口（第 18 节实现）
│   ├── ReActLoop.java             # [新增] 核心循环引擎
│   ├── PromptBuilder.java         # [新增] 四部分 prompt 组装器
│   ├── ToolExecutor.java          # [新增] 工具集中执行器 + 审计写入
│   ├── AgentService.java          # [新增] 统一编排入口
│   ├── ProfileContext.java        # [新增] ThreadLocal 上下文
│   └── ContextLoader.java         # [新增] 无缓存上下文加载（Bootstrap + SKILL.md 预载）
└── src/test/java/com/oryxos/core/
    ├── ReActLoopTest.java         # [新增]
    ├── PromptBuilderTest.java     # [新增]
    ├── ToolExecutorTest.java      # [新增]
    ├── AgentServiceTest.java      # [新增]
    └── ContextLoaderTest.java     # [新增]

oryxos-provider/
├── src/main/java/com/oryxos/provider/
│   ├── DefaultProviderService.java# [修改] 实现 core.ProviderService，ChatResponse→Response 转换，消息序列映射
│   ├── ProviderService.java       # [删除] 移入 core
│   ├── ProviderNotFoundException.java # [已有，不改]
│   ├── OryxOsProperties.java      # [已有，不改]
│   └── ToolSchemaAdapter.java     # [已有，不改]
└── src/test/java/com/oryxos/provider/
    ├── ProviderServiceTest.java   # [修改] 适配新返回类型 + 补多轮消息映射测试
    └── ProviderSmokeIT.java       # [修改] 适配 Response 返回

oryxos-storage/
├── src/main/java/com/oryxos/storage/
│   ├── ToolInvocation.java        # [新增] tool_invocations 实体
│   ├── ToolInvocationRepository.java # [新增] JPA Repository
│   ├── JpaToolInvocationStore.java # [新增] 实现 core.ToolInvocationStore（依赖倒置适配）
│   └── package-info.java          # [已有]
└── src/test/
    ├── java/com/oryxos/storage/ToolInvocationRepositoryTest.java  # [新增]
    └── resources/schema.sql       # [修改] 补 tool_invocations 建表
```

**Structure Decision**: 核心引擎与跨模块契约全部放 `oryxos-core`（依赖倒置——引擎依赖接口而非实现，不产生 core→provider/storage 循环）。`ProviderService` 接口移入 core 并返回自有 `Response`（core 保持 Spring-agnostic），`DefaultProviderService`（provider）负责 Spring AI 适配。审计契约 `ToolInvocationStore` + 值对象放 core，JPA 实现放 storage。`ToolRegistry`/`SessionManager` 接口放 core，后续节在对应模块实现。

## Complexity Tracking

无 Constitution 违规，本节不涉及复杂度豁免。跨节改造点（用户裁定接受）：ProviderService 接口迁入 core 改返回类型、Prompt 向后兼容扩展、DefaultProviderService 消息序列适配。
