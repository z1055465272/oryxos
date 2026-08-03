# Implementation Plan: ReAct 循环引擎 + 编排层 + 上下文供给层

**Branch**: `017-lesson17-react-loop` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-react-loop/spec.md`

## Summary

实现 OryxOS 的 ReAct 循环引擎（`ReActLoop`），这是 Agent 最核心的推理-行动调度器。循环每轮通过`PromptBuilder`组装四部分上下文（system prompt + 记忆 + 会话历史 + 工具列表），通过第 16 节的`ProviderService`调一次大模型，再通过`ToolExecutor`执行模型要求的工具调用并回填结果，直到模型不再要求工具或达到最大轮数。上层由`AgentService`统一编排三种触发源（CLI/Web/定时），通过`ProfileContext`（ThreadLocal）传递当前 Agent 配置。`ContextLoader`提供无缓存的实时上下文加载。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.x, Spring AI Alibaba, Spring Data JPA, SQLite, SLF4J/Logback

**Storage**: SQLite（`tool_invocations` 表手工建表脚本）; 文件系统（`.oryxos/` 下 Bootstrap/Agent/Skill 文件，ContextLoader 读取）

**Testing**: JUnit 5 + Mockito; 五个单测类（全 mock，不碰网络/数据库/文件 IO）；`mvn clean verify` 含 P3C/SpotBugs/FindSecBugs/PMD 全绿即通过

**Target Platform**: JDK 21 单二进制 Spring Boot 应用

**Project Type**: Maven 多模块（本节修改 `oryxos-core` + `oryxos-storage`）

**Performance Goals**: 代码课单测秒级跑完; ReActLoop 主循环约 50 行 Java

**Constraints**: 同步阻塞 + Virtual Thread; 禁用 Spring AI 自动 tool 执行; 不碰异步编程模型; 避开 P3C/ASM 解析不了的 Java 18+ 语法

**Scale/Scope**: 核心阶段代码课交付；最大迭代轮数默认 10；历史截断默认 20 轮；本节约 10 个新增/修改 Java 文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 自实现 ReAct Loop | ✅ PASS | 本节核心交付物；主循环手写数十行，不依赖 Spring AI Agent 抽象 |
| II. Spring AI 只用两件事 | ✅ PASS | ProviderService 只做协议转换; ToolExecutor 集中执行，禁用自动 tool 执行 |
| III. Provider 显式映射 | ✅ PASS | 不引入新 Provider，复用第 16 节的显式映射 |
| IV. 一个目录=一个Agent | ✅ PASS | ContextLoader 按 Profile 读取 AGENT.md + Bootstrap + Skill 软连接 |
| V. 审计表 Day One 写入 | ✅ PASS | `tool_invocations` 表本节交付，ToolExecutor 每次执行无论成败都写入 |
| VI. 沙箱白名单 | ⏸️ NOT APPLICABLE | Sandbox 24 节就位；本节 ToolExecutor 留 `sandbox.enforce()` 调用位 |
| VII. 同步执行模型 | ✅ PASS | 全程同步阻塞，Virtual Thread 并发 |
| VIII. Tool 模块三合一 | ✅ PASS | 不修改 oryxos-tool 模块，ToolExecutor 放入 oryxos-core |

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
│   ├── Profile.java               # [已有，本次不改] 全字段 record
│   ├── ProfileRegistry.java       # [已有，本次不改] Profile 内存索引
│   ├── Prompt.java                # [修改] 扩展支持 system prompt + 多轮 messages
│   ├── OryxTool.java              # [已有] Tool 抽象接口
│   ├── ToolResult.java            # [已有] 工具执行结果
│   ├── ReActLoop.java             # [新增] 核心循环引擎
│   ├── PromptBuilder.java         # [新增] 四部分 prompt 组装器
│   ├── ToolExecutor.java          # [新增] 工具集中执行器
│   ├── AgentService.java          # [新增] 统一编排入口
│   ├── ProfileContext.java        # [新增] ThreadLocal 上下文
│   ├── ContextLoader.java         # [新增] 无缓存上下文加载
│   ├── Session.java               # [新增] 会话记录（内存版，第 18 节持久化）
│   ├── ToolRegistry.java          # [新增] Tool 注册表接口（第 20 节实现）
│   └── SessionManager.java        # [新增] Session 管理接口（第 18 节实现）
└── src/test/java/com/oryxos/core/
    ├── ReActLoopTest.java         # [新增] ReActLoop 单元测试
    ├── PromptBuilderTest.java     # [新增] PromptBuilder 单元测试
    ├── ToolExecutorTest.java      # [新增] ToolExecutor 单元测试
    ├── AgentServiceTest.java      # [新增] AgentService 单元测试
    └── ContextLoaderTest.java     # [新增] ContextLoader 单元测试

oryxos-storage/
├── src/main/java/com/oryxos/storage/
│   ├── ToolInvocation.java        # [新增] tool_invocations 实体
│   ├── ToolInvocationRepository.java # [新增] JPA Repository
│   └── package-info.java          # [已有]
└── src/main/resources/db/migration/
    └── V1__init_audit_tables.sql  # [修改] tool_invocations 表已存在（V1 预留），本次确认列完整
```

**Structure Decision**: 核心引擎类全部放 `oryxos-core`（依赖倒置——引擎依赖接口而非实现）。审计实体放 `oryxos-storage`，与 LlmCall 同口径。`ToolRegistry`/`SessionManager` 先在 core 定义接口，后续节在对应模块实现。

## Complexity Tracking

无 Constitution 违规，本节不涉及复杂度豁免。
