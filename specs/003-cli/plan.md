# Implementation Plan: CLI 命令行入口 + 会话持久化地基

**Branch**: `018-lesson18-cli` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-cli/spec.md`

## Summary

实现 OryxOS 的命令行入口（`OryxOsCli`，整个程序的 `main`），注册 12 个 Picocli 子命令，按"要不要调模型 / 跑引擎"分轻（`init`、`profile list` 等直接文件操作不启动 Spring）重（`chat`、`serve`、`gateway` 启动 Spring 上下文）两档。`chat` 命令由 `CliChannel` 实现：读 stdin 写 stdout，维护当前 Session，每行交 `AgentService.process`，`/quit` 退出——CLI 是薄薄的"门"，只管消息进出、不干活。

**会话持久化地基（本节一并交付）**：`SessionManager` 接口在 oryxos-core 补齐三个方法（`getOrCreate(channel,user,profileName)` / `get(sessionId)` / `save(session)`），`session_id` 拼接只发生在 `SessionManager` 内部这一处；`Session` 实体 + `SessionRepository` + `JpaSessionManager` 落 oryxos-storage；`sessions` 表手工建表脚本（V2 增量）。对话历史整体 JSON 序列化存 `messages_json` 一列。

**关键架构事实**：ReActLoop/PromptBuilder/ToolExecutor/AgentService 等引擎类是普通 POJO（构造器注入，非 Spring Bean），`ToolRegistry` 尚无实现类，`ChatModel` 无工厂 Bean。`chat` 重命令是本项目第一批启动 Spring 上下文的入口——这正是课件"第四，重命令启动 Spring 后模块扫描范围容易埋雷"讲的场景：CLI 模块与 storage 模块不同 Java 包，必须显式声明 `@EnableJpaRepositories(basePackages=...)`/`@EntityScan(basePackages=...)`，否则启动即报 "Found 0 JPA repository interfaces"、审计写不进去直接报错退出。引擎 bean 装配（含显式 Provider 映射）由重命令共用的启动器以 `@Bean` 方法声明——这是 `chat` 能真正跑起引擎所必需的接线，且天然满足宪法 III（显式 `Map<String, ChatModel>`，不靠类型扫描）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.x, Spring Data JPA, SQLite, Picocli 4.7.7（父 POM 锁定，本地仓库已核实）, SLF4J/Logback, Jackson（**显式声明** jackson-databind，版本走 Boot BOM 2.21.4；实测不随 spring-boot-starter-data-jpa 传递，本地仓库已核实）。**实现补遗**：`oryxos.providers` 配置段（deepseek + `${DEEPSEEK_API_KEY}` 占位）补进 `oryxos-boot` 的 application.yaml——第 16 节只交付了配置类+校验、没有实际配置值，chat 重命令装配 ChatModel 映射需要它（用户已确认选项 A）。boot 的 application.yaml 同时启用 `spring.sql.init`（mode: always）在重命令启动时幂等执行 V1/V2 建表脚本。

**Storage**: SQLite（`sessions` 表 DDL 新增 V2 增量脚本）；对话历史 JSON 序列化存 `messages_json` 一列；不依赖 `hibernate.ddl-auto=update`

**Testing**: JUnit 5 + Mockito + AssertJ；`SessionManagerTest`（幂等/隔离/id 只一处，@DataJpaTest 验 JpaSessionManager 真实实现）+ `SessionRepositoryTest`（建表能存能读/messages_json 回读完整/模拟重启历史仍在）都在 oryxos-storage；单测默认跑，集成冒烟打 `@Tag("integration")` CI 跳过；`mvn clean verify` 含 P3C/SpotBugs/FindSecBugs/PMD 全绿即通过

**Target Platform**: JDK 21 单二进制 Spring Boot 应用（fat JAR mainClass 已是 `com.oryxos.cli.OryxOscCli`）

**Project Type**: Maven 多模块（本节修改 `oryxos-core` + `oryxos-storage` + `oryxos-cli` + `oryxos-channel-cli`）

**Performance Goals**: 轻命令秒回（不启动 Spring）；重命令 2~4 秒启动可接受；单测秒级跑完

**Constraints**: 同步阻塞 + Virtual Thread；禁用 Spring AI 自动 tool 执行；`session_id` 拼接只在 SessionManager 一处；避开 P3C/ASM 解析不了的 Java 18+ 语法；凭证走环境变量占位（`${ENV_VAR}`）；SQLite 手工建表不依赖 ddl-auto 迁移

**Scale/Scope**: 核心阶段代码课交付；新增约 12 个 Java 文件、修改 5 个既有文件（含 1 个既有契约接口扩展）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 自实现 ReAct Loop | ✅ PASS | 本节不改 ReActLoop；CLI 只调 AgentService，不引入任何自动执行路径 |
| II. Spring AI 只用两件事 | ✅ PASS | 重命令 Spring 上下文只做依赖装配与 JPA；LLM 调用仍走 DefaultProviderService（自动 tool 执行已关） |
| III. Provider 显式映射 | ✅ PASS | 引擎 bean 装配用 `Map<String, ChatModel>` 显式映射（照 ProviderSmokeIT 的 OpenAiChatModel 构建形态），不靠类型扫描 |
| IV. 一个目录=一个Agent | ✅ PASS | 不触碰 ContextLoader/Profile 语义；CLI 只透传 profileName |
| V. 审计表 Day One 写入 | ✅ PASS | sessions 表本节交付；审计写入路径（ToolExecutor/DefaultProviderService）不动 |
| VI. 沙箱白名单 | ⏸️ NOT APPLICABLE | Sandbox 24 节就位；本节不新增涉外 IO 工具 |
| VII. 同步执行模型 | ✅ PASS | chat 全程同步阻塞；SessionManager 无异步 |
| VIII. Tool 模块三合一 | ✅ PASS | 不触碰 oryxos-tool；无新模块 |

**Gate Result**: PASS — 无违规，可进入 Phase 0。

## Project Structure

### Documentation (this feature)

```text
specs/003-cli/
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
│   ├── SessionManager.java   # [修改] 接口从 save(session) 扩展为 getOrCreate(channel,user,profileName)/get(sessionId)/save(session)
│   └── package-info.java     # [已有]

oryxos-storage/
├── src/main/java/com/oryxos/storage/
│   ├── SessionEntity.java    # [新增] sessions 表 JPA 实体（字段照 TechnicalSolution §9.2，含 messages_json 编解码）
│   ├── SessionRepository.java# [新增] JPA Repository
│   ├── JpaSessionManager.java# [新增] 实现 core.SessionManager（依赖倒置：契约在 core、实现在 storage）
│   └── package-info.java     # [已有]
├── src/main/resources/db/migration/
│   └── V2__create_sessions_table.sql  # [新增] sessions 建表（CREATE TABLE IF NOT EXISTS，幂等）
└── src/test/
    ├── java/com/oryxos/storage/
    │   ├── SessionManagerTest.java    # [新增] 幂等/隔离/id 只一处（@DataJpaTest 验 JpaSessionManager 真实实现）
    │   └── SessionRepositoryTest.java # [新增] 建表能存能读/messages_json 完整/模拟重启
    └── resources/
        ├── application.yaml  # [修改] 会话测试数据源 + schema 位置
        └── schema.sql        # [修改] 补 sessions 建表（test scope）

oryxos-channel-cli/
├── src/main/java/com/oryxos/channel/cli/
│   ├── CliChannel.java       # [新增] chat 交互实现（读 stdin 写 stdout，/quit 退出）
│   └── package-info.java     # [已有]

oryxos-cli/
├── src/main/java/com/oryxos/cli/
│   ├── OryxOscCli.java       # [修改] @Command 挂 12 个子命令 + addSubcommand 注册
│   ├── ChatCommand.java      # [新增] 重命令：启动 Spring → 拿 AgentService/SessionManager → 跑 CliChannel
│   ├── ServeCommand.java     # [新增] 重命令：启动 Spring（26 节补 WebServer 本体，本节先起上下文）
│   ├── GatewayCommand.java   # [新增] 重命令：启动 Spring（多 Channel 守护，后续节补）
│   ├── InitCommand.java      # [新增] 轻命令：初始化 .oryxos/ 工作区
│   ├── StatusCommand.java    # [新增] 查配置与运行状态
│   ├── ProfileCommand.java   # [新增] 轻命令组：profile list/create/show/delete
│   ├── ProviderListCommand.java # [新增] 轻命令：列 provider
│   ├── ToolListCommand.java  # [新增] 轻命令：列 tool
│   ├── SessionListCommand.java# [新增] 轻命令：列 session
│   └── CliSpringBootstrap.java# [新增] 重命令共用的 Spring 启动器：显式 @EnableJpaRepositories/@EntityScan basePackages + 引擎 bean 装配
```

> **测试放位说明**：课件验收 harness 点名 `SessionManagerTest` 与 `SessionRepositoryTest` 两个类，两个都放 oryxos-storage——`SessionManager` 的唯一生产实现 `JpaSessionManager` 与 SQLite 测试设施（@DataJpaTest + schema.sql）都在 storage，@DataJpaTest 直接验真实实现（幂等/隔离/id 只一处），与既有 LlmCallRepositoryTest/ToolInvocationRepositoryTest 完全同模式，避免"在 core 测一个测试替身替身"的假绿。`SessionRepositoryTest` 验建表/序列化/模拟重启。不重复放位。

**Structure Decision**:
- **依赖倒置**（宪法 + TechnicalSolution §8.5 同款模式）：`SessionManager` 契约放 oryxos-core（本节扩展为三方法），JPA 实现 `JpaSessionManager` 放 oryxos-storage，core 不依赖 storage。与既有 `ToolInvocationStore`/`JpaToolInvocationStore`、`SessionManager` 接口（第 17 节已建）完全同构。
- **`Session`（core 值对象）与 `SessionEntity`（storage 实体）分离**：`com.oryxos.core.Session` 是引擎的内存值对象（第 17 节交付，ReActLoop 直接累积消息），保持纯 Java 无 JPA 注解、字段不动（仅补向后兼容的 `restore` 静态工厂供存储层还原）；storage 的 `SessionEntity` 承载持久化字段与 `messages_json` 序列化，`JpaSessionManager` 负责两者转换。避免把 JPA 注解染进核心值对象（core 目前保持 Spring-agnostic）。
- **`messages_json` 序列化**：用 Jackson（**显式声明** jackson-databind 依赖，Boot BOM 管理版本——实测不随 spring-boot-starter-data-jpa 传递）把 `Session.messages()` 序列化为 JSON 文本；反序列化按 sealed `Session.Message` 判别（user/assistant/toolResult 三型）。编解码方法收在 `SessionEntity` 静态方法，测试直接断言往返一致。
- **重命令 Spring 启动器统一放 oryxos-cli**（`CliSpringBootstrap`）：`chat`/`serve`/`gateway` 三个重命令共用，集中处理"显式声明 JPA 扫描范围"这一个坑，并集中声明引擎 bean（ReActLoop/PromptBuilder/ToolExecutor/AgentService/ProfileRegistry/ContextLoader/DefaultProviderService + 显式 `Map<String, ChatModel>`）。`JpaSessionManager`/`JpaToolInvocationStore` 直接作 `SessionManager`/`ToolInvocationStore` bean（实现即契约，**不加冗余包装 bean**——避免 `NoUniqueBeanDefinitionException`）。
- **`init` 生成默认 Profile + boot 补 provider 配置**：`InitCommand` 建 `.oryxos/` 工作区骨架 + `profiles/default.yaml`（deepseek provider 引用），兼容第 16 节 `ProfileLoader` 读取目录（`.oryxos/agents/` 迁移归第 29 节）。`oryxos.providers` 配置段（deepseek + `${DEEPSEEK_API_KEY}` 占位）补进 boot 的 application.yaml——第 16 节只交付了配置类+校验、没有实际配置值，chat 装配 ChatModel 映射需要它（用户已确认选项 A）；boot 同时启用 `spring.sql.init` 在重命令启动时幂等执行 V1/V2 建表脚本。
- **12 子命令全部 `mixinStandardHelpOptions=true`**：课件人工验收"12 子命令 --help 正常"要求每个子命令都支持 `--help`（只挂在父命令不会传播到子命令）。
- **`session_id` 拼接 `channel:user:profile`**：`JpaSessionManager.buildSessionId` 用 `:` 分隔（包内可见供测试断言），只在 `JpaSessionManager` 一处。实测 `chat` 产生 `cli:Administrator:default` 会话并落库。

## Complexity Tracking

无 Constitution 违规，本节不涉及复杂度豁免。跨节改造点（第 18 节课件明确交付物）：`SessionManager` 接口由 `save(session)` 扩展为三方法（`getOrCreate`/`get`/`save`）。第 17 节 `AgentService` 只依赖 `save`，且已核实第 17 节没有接口实现类（只有测试 mock），扩展安全。
