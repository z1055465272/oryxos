# Implementation Plan: Agent Provider

**Branch**: `016-lesson16-agent-provider` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-agent-provider/spec.md`

## Summary

实现 OryxOS 的第一块核心能力——Provider 层。核心交付：Profile 解析加载基础设施（`Profile` record、`ProfileLoader`、`ProfileRegistry`）、Provider 显式路由与调用（`ProviderService` 扩展为完整实现，含 `chat` 方法）、工具 Schema 适配器（OryxTool → Spring AI 格式翻译）、LLM 审计持久化（`LlmCall` 实体 + Repository + 手工建表 DDL）。不包含 fallback/hedge racing/熔断/成本看板。

## Technical Context

**Language/Version**: Java 21 (MUST), Spring Boot 3.5.16
**Primary Dependencies**: Spring AI 1.0.9 (`spring-ai-model`, `spring-ai-openai`), Spring AI Alibaba 1.1.2.3, SnakeYAML (Spring Boot 内置), SQLite + Spring Data JPA + Hibernate Community Dialects
**Storage**: SQLite via `oryxos-storage` 模块, 手工 DDL 建表脚本
**Testing**: JUnit 5 + Mockito (Spring Boot 内置), assertJ; `@Tag("integration")` 冒烟测试
**Target Platform**: JDK 21 Linux/Windows 服务器, Spring Boot fat JAR
**Project Type**: Maven 多模块单体应用 (9 modules)
**Performance Goals**: LLM 调用延迟由外部 Provider 决定, 本地开销可忽略 (<1ms 路由+审计)
**Constraints**: 同步阻塞模型 + Virtual Thread; 不得使用 Spring AI 自动 tool 执行; 不得按类型扫描 Bean 区分 Provider; Spotless/Checkstyle/P3C-PMD 门禁必须通过
**Scale/Scope**: 核心阶段 2~3 个 Provider 并存; Profile 数量 < 100; `llm_calls` 表只写入

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | 原则 | 合规状态 | 说明 |
|---|------|---------|------|
| I | 自实现 ReAct Loop | ✅ 不相关 | Provider 不涉及 ReAct 循环实现 |
| II | Spring AI 只用两件事 | ✅ PASS | `ProviderService` 只做协议转换 + schema 生成; `chat` 方法显式关闭自动 tool 执行 |
| III | Provider 必须显式映射 | ✅ PASS | `Map<String, ChatModel>` 显式映射, 不靠类型扫描 |
| IV | 一个目录 = 一个 Agent | ✅ 不相关 | 本节 Profile 从 `.oryxos/profiles/` 加载（旧结构），29 节切到 Agent 目录 |
| V | 审计表 Day One 写入 | ✅ PASS | `LlmCall` 实体 + Repository + 手工 DDL, 成功/失败都落库 |
| VI | 沙箱白名单 | ✅ 不相关 | 本节不涉及文件/Shell/HTTP 操作 |
| VII | 同步执行模型 | ✅ PASS | `chat` 方法全程同步, 无 Reactor/CompletableFuture |
| VIII | Tool 模块三合一 | ✅ 不相关 | 本节不改 `oryxos-tool` |

**合规结论**: 所有相关原则 PASS, 无违规需要 justify。

## Project Structure

### Documentation (this feature)

```text
specs/001-agent-provider/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (internal interfaces)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── OryxTool.java              # [已存在] Tool 抽象接口
├── ToolResult.java            # [已存在] 工具执行结果 record
├── Profile.java               # [新增] Agent 运行配置 record, 承载全部字段
├── ProfileLoader.java         # [新增] 扫描 .oryxos/profiles/ 下 YAML, 逐个解析+校验
└── ProfileRegistry.java       # [新增] 内存索引 Map<String, Profile>, 按 name 查找

oryxos-core/src/test/java/com/oryxos/core/
└── ProfileLoaderTest.java     # [新增] 合法解析/不存在provider报错/坏文件不阻断/ENV占位

oryxos-provider/src/main/java/com/oryxos/provider/
├── ProviderService.java       # [改造] 接口扩展: 保留 resolve(), 新增 chat(sessionId, Profile, Prompt)
├── DefaultProviderService.java # [新增] chat 实现: 显式映射路由 + 审计落库 + 关自动执行
├── ToolSchemaAdapter.java     # [新增] OryxTool → Spring AI ToolDefinition 翻译
└── ProviderNotFoundException.java # [新增] provider name 找不到时抛的异常

oryxos-provider/src/test/java/com/oryxos/provider/
├── ProviderServiceTest.java   # [新增] 路由不串台/未知名抛异常/成功失败审计/关自动执行
└── ToolSchemaAdapterTest.java # [新增] schema 字段对齐/只翻译不执行

oryxos-storage/src/main/java/com/oryxos/storage/
├── LlmCall.java               # [新增] JPA 实体: sessionId/provider/model/tokens/duration/success/errorMessage
├── LlmCallRepository.java     # [新增] Spring Data JPA Repository
└── src/main/resources/
    └── schema.sql             # [新增] 手工 DDL: llm_calls 表 (含 success/error_message 列)

oryxos-storage/src/test/java/com/oryxos/storage/
└── LlmCallRepositoryTest.java # [新增] 手工建表能存能读; success/error_message 列存在

oryxos-provider/src/test/java/com/oryxos/provider/
└── ProviderSmokeIT.java       # [新增] @Tag("integration"), 真调模型, 验证链路
```

**Structure Decision**: 严格按课件模块落位表: Profile 三件套 → oryxos-core（核心抽象层）；ProviderService/适配器 → oryxos-provider（依赖 core）；LlmCall/Repository → oryxos-storage（依赖 core）。跨模块依赖方向: provider → core, storage → core, 无循环。

## Complexity Tracking

> 无宪章违规，本节不涉及复杂度 justify。

## Tooling Note

当前环境中 Maven 版本为 3.3.9，项目插件要求 >= 3.6.3。`mvn verify` 和 `mvn dependency:tree` 需要用 Maven 3.6.3+ 执行。建议安装 Maven 3.9+ 或添加 Maven Wrapper (`mvnw`)。
