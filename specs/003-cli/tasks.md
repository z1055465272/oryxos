# Tasks: CLI 命令行入口 + 会话持久化地基（第 18 节）

**Input**: Design documents from `specs/003-cli/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/README.md

**Tests**: 课件"验收 harness"明确要求两个测试类（harness 先行，测试先于/伴随实现）；两个测试类都放 oryxos-storage（`SessionManager` 的唯一生产实现 `JpaSessionManager` 与 SQLite 测试设施都在 storage，@DataJpaTest 直接验真实实现，与既有 LlmCallRepositoryTest/ToolInvocationRepositoryTest 同模式）。关键回归测试"同一三元组_历次getOrCreate都是同一个Session"断言逐条保真，方法名译英文、课件原文进 `@DisplayName`。

**Organization**: 按用户故事分组，每阶段独立可测。会话持久化是 chat 与 Web/定时共用的地基，实现放 Foundational、验证放 US3；chat（US1）依赖持久化地基。

## 格式: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 归属用户故事（US1-US4）
- 描述含确切文件路径

## Path Conventions

Maven 多模块项目，模块路径：
- `oryxos-core/src/main/java/com/oryxos/core/`
- `oryxos-storage/src/main/java/com/oryxos/storage/`
- `oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/`
- `oryxos-cli/src/main/java/com/oryxos/cli/`
- `oryxos-storage/src/main/resources/db/migration/`

测试路径 parallel:
- `oryxos-core/src/test/java/com/oryxos/core/`
- `oryxos-storage/src/test/java/com/oryxos/storage/`
- `oryxos-cli/src/test/java/com/oryxos/cli/`

---

## Phase 1: Setup（共享基建）

**Purpose**: 确认依赖可用（H3），准备本节基础设施

- [X] T001 运行 `mvn dependency:resolve -pl oryxos-cli,oryxos-storage`（或本地 mvn 路径）核实 Picocli 4.7.7 / SQLite / Jackson（**实测修正：Jackson 不随 spring-boot-starter-data-jpa 传递，需在 oryxos-storage/pom.xml 显式声明 jackson-databind，版本走 Boot BOM 2.21.4**）均在本地依赖可解析（H3 完成，记录结论到 research.md）
- [X] T002 确认本节无新 Maven 模块；依赖方向约束：oryxos-core 不得 import oryxos-storage 具体类（SessionEntity/SessionRepository/JpaSessionManager 均不可被 core 引用）

---

## Phase 2: Foundational（阻塞性前置——会话持久化实现）

**Purpose**: 会话持久化是 US1（chat）与后续所有入口（Web/定时）共用的地基，**未完成前任何 US 不能开工**

### SessionManager 契约扩展（oryxos-core）

- [X] T003 扩展 `oryxos-core/src/main/java/com/oryxos/core/SessionManager.java`：接口从 `save(Session)` 扩展为三方法——`Session getOrCreate(String channel, String user, String profileName)`、`Optional<Session> get(String sessionId)`、`void save(Session)`；javadoc 注明"session_id 拼接只发生在实现内部这一处，入口只提供三元组"

### sessions 表建表脚本（oryxos-storage）

- [X] T004 新增 `oryxos-storage/src/main/resources/db/migration/V2__create_sessions_table.sql`：`CREATE TABLE IF NOT EXISTS sessions`（session_id VARCHAR PK / profile_name / channel / user_id / messages_json TEXT / status VARCHAR DEFAULT 'active' / created_at / last_active_at / archived_at 可空），幂等；表结构照 data-model.md 与 TechnicalSolution §9.2

### JPA 持久化实现（oryxos-storage）

- [X] T005 [P] 创建 `oryxos-storage/src/main/java/com/oryxos/storage/SessionEntity.java`：JPA `@Entity` `@Table(name="sessions")`，字段照 data-model.md（sessionId 主键、profileName、channel、userId、messagesJson、status、createdAt、lastActiveAt、archivedAt）；**编解码静态方法** `encodeMessages(List<Session.Message>)` / `decodeMessages(String)` 用 Jackson `ObjectMapper` 序列化/反序列化 sealed 三型消息（user/assistant/toolResult 判别字段），历史为空存 `[]`、回读空列表不抛异常；附 `fromSession`/`toSession` 转换 + `Session.restore` 静态工厂（core 值对象字段不动）
- [X] T006 [P] 创建 `oryxos-storage/src/main/java/com/oryxos/storage/SessionRepository.java`：`interface SessionRepository extends JpaRepository<SessionEntity, String>`
- [X] T007 创建 `oryxos-storage/src/main/java/com/oryxos/storage/JpaSessionManager.java`：实现 `com.oryxos.core.SessionManager`；构造 `(SessionRepository)`；`getOrCreate(channel,user,profileName)` 幂等——session_id 用固定分隔符拼接（`channel + ":" + user + ":" + profileName`），`findById` 命中返回、未命中新建 `Session`（ACTIVE、空消息）并持久化；`get(sessionId)` 查库转 `Session`（`messagesJson` 解码回 `messages`）；`save(session)` 把消息编码进 `messagesJson` 落库并更新 `lastActiveAt`；`Session`↔`SessionEntity` 转换收在此类；`buildSessionId` 包内可见供测试断言

**Checkpoint**: 持久化地基完成——US1/US3/US4 可在此基础上开工

---

## Phase 3: User Story 3 - 会话持久化验证（Priority: P1）

**Goal**: 会话幂等、隔离、持久化由自动化 harness 钉死（27 节缝隙③在这节就堵上）

**Independent Test**: 两个测试类（课件点名）都在 oryxos-storage，@DataJpaTest 直连 SQLite 验 `JpaSessionManager` 真实实现 + `SessionRepository`，各自秒级跑绿

### Tests for User Story 3 ⚠️（harness 先行）

- [X] T008 [P] [US3] 创建 `oryxos-storage/src/test/java/com/oryxos/storage/SessionManagerTest.java`：`@DataJpaTest` + `@AutoConfigureTestDatabase(Replace.NONE)` + `@EnableJpaRepositories(basePackageClasses=SessionRepository.class)` + `@EntityScan(basePackageClasses=SessionEntity.class)`，注入 `SessionRepository` 构造真实 `JpaSessionManager`；**关键回归测试**方法名译英文（`getOrCreate_sameTriple_returnsSameSession`）、课件原文"同一三元组_历次getOrCreate都是同一个Session"进 `@DisplayName`，断言逐条保真：
  - 同一三元组两次 `getOrCreate("cli","wang","default")` 返回的 `id()` 相同（幂等，多轮对话靠它串起来）
  - 三元组任一不同则不同会话：channel 不同（`"web"` vs `"cli"`）、user 不同、profileName 不同各自 `assertNotEquals(id())`
  - session_id 拼接只此一处：断言 `JpaSessionManager` 产出的 id 格式与三元组一致（`cli:wang:default`）
- [X] T009 [P] [US3] 创建 `oryxos-storage/src/test/java/com/oryxos/storage/SessionRepositoryTest.java`：`@DataJpaTest` + `@AutoConfigureTestDatabase(Replace.NONE)` + `@EnableJpaRepositories` + `@EntityScan`，测试：
  - 手工建表脚本建出的 sessions 表能存能读（存 SessionEntity 回查字段一致）
  - `messages_json` 序列化回读后消息完整（encodeMessages 存 5 条三型消息，decodeMessages 回读逐条断言）
  - 模拟"重启"——同一 SQLite 文件重建上下文重查同一主键历史还在
  - 同步补 `oryxos-storage/src/test/resources/schema.sql` 的 sessions 建表

### Implementation for User Story 3

- [X] T010 [US3] 若 T008-T009 暴露出 SessionManager 契约/实现缺陷，修至全绿（实现错修实现；测试错停下报告）——4+4 全绿，无需修复

**Checkpoint**: 会话幂等/隔离/持久化在 harness 下全绿

---

## Phase 4: User Story 1 - 终端交互式对话（Priority: P1）🎯 MVP

**Goal**: 用户在终端 `oryxos chat [--profile <name>]` 与 Agent 交互式多轮对话，`/quit` 退出；CLI 只做"读输入→交引擎→打印结果"

**Independent Test**: chat 交互循环与 /quit 退出属进程级行为留人工清单；本节可自动验证的是 CliChannel 骨架（读 stdin 写 stdout 的循环形态）——交互本体靠人工跑通

### Implementation for User Story 1

- [X] T011 [P] [US1] 创建 `oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/CliChannel.java`：构造 `(AgentService, SessionManager)`；`runInteractive(String channel, String user, String profileName)`——`sessionManager.getOrCreate(channel, user, profileName)` 拿/建 Session，`while(true)` 读 stdin 每行：`System.out.print("> ")` 提示 → `line.trim()` 为 `"/quit"` 则 break 退出 → 否则 `String reply = agentService.process(session, line)` → `System.out.println(reply)`；**session_id 拼接只发生在 SessionManager 内部**，本类只传三元组（channel 固定 `"cli"`、user 取系统用户名 `System.getProperty("user.name")` 缺省 `"local"`）；stdin EOF 时直接返回
- [X] T012 创建 `oryxos-cli/src/main/java/com/oryxos/cli/CliSpringBootstrap.java`：重命令共用 Spring 启动器——`@Configuration` + `@EnableAutoConfiguration` + `@EnableJpaRepositories(basePackages="com.oryxos.storage")` + `@EntityScan(basePackages="com.oryxos.storage")` + `@EnableConfigurationProperties(OryxOsProperties.class)`（课件坑：CLI 与 storage 不同包，不显式声明会 "Found 0 JPA repository interfaces"）；`@Bean` 装配引擎：`JpaSessionManager`/`JpaToolInvocationStore`（实现即契约，无冗余包装 bean）、空 `ToolRegistry`（第 20 节替换）、`ProfileLoader`（读 `.oryxos/profiles/`，provider 名校验集来自 `OryxOsProperties`）→ 注册进 `ProfileRegistry`、`ContextLoader(Path.of(".oryxos"))`、`PromptBuilder`、`ToolExecutor`、`ReActLoop`、`AgentService`、`Map<String, ChatModel>` 显式映射（`OpenAiChatModel.builder()` 照 ProviderSmokeIT 形态，apiKey 从 `${ENV}` 占位解析，**不落明文**）+ `DefaultProviderService`
- [X] T013 [P] [US1] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/ChatCommand.java`：`@Command(name="chat", description="在终端里和 Agent 交互式对话", mixinStandardHelpOptions=true)` + `@Option(names="--profile", defaultValue="default")`；`run()`——`new SpringApplicationBuilder(CliSpringBootstrap.class).web(WebApplicationType.NONE).run()` 拿 `ApplicationContext` → 取 `AgentService` + `SessionManager` → `new CliChannel(...).runInteractive("cli", currentUser(), profileName)`

**Checkpoint**: `chat` 重命令能启动 Spring（"Found N JPA repository interfaces" N>0）并进入交互

---

## Phase 5: User Story 2 - 命令按轻重分流（Priority: P1）

**Goal**: 不调模型的轻命令（init、profile list 等）不启动 Spring、直接文件操作秒回；要调模型的重命令（chat/serve/gateway）才启动 Spring

**Independent Test**: 命令分流属进程级行为留人工清单（`init` 秒回无 Spring 日志、`chat` 有 Spring 日志）；本节可自动验证的是 init 的工作区骨架创建

### Implementation for User Story 2

- [X] T014 [P] [US2] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/InitCommand.java`：`@Command(name="init", description="初始化一个 OryxOS 工程", mixinStandardHelpOptions=true)`；`run()`——创建 `.oryxos/` 工作区骨架（`agents/`、`skills/`、`memory/`、`sessions/`、`logs/` 目录 + `memory/MEMORY.md` + `profiles/default.yaml` 默认 Profile，兼容第 16 节 ProfileLoader 读取目录），已存在则幂等跳过；纯文件操作、**不启动 Spring**；`createWorkspace`/`defaultProfileYaml` 包内可见供测试
- [X] T015 [P] [US2] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/ProfileCommand.java`：`@Command(name="profile", subcommands=list/create/show/delete)`；内嵌四个子命令类——`list` 列 `.oryxos/profiles/` 下的 YAML；`create/show/delete` 对对应 YAML 做增查删；全部轻命令直接文件操作、**不启动 Spring**

**Checkpoint**: `init`/`profile list` 秒回，不启动 Spring

---

## Phase 6: User Story 4 - 12 个子命令统一可查可跑（Priority: P2）

**Goal**: 12 个子命令全部注册可跑、`--help` 正常；`OryxOsCli` 成为完整 main 入口

**Independent Test**: 12 子命令 `--help` 属进程级行为留人工清单；本节可自动验证的是命令类存在且 `@Command` 注解正确（编译/门禁保证）

### Implementation for User Story 4

- [X] T016 [P] [US4] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/StatusCommand.java`：`@Command(name="status", ...)`；轻命令，读 `.oryxos/` 工作区与关键配置文件，打印存在性/状态摘要
- [X] T017 [P] [US4] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/ServeCommand.java`：`@Command(name="serve")` + `@Option(--port)`；重命令——启动 Spring 上下文（复用 `CliSpringBootstrap`），打印占位日志 + `Thread.join()` 保持常驻（WebServer 本体 26 节补）
- [X] T018 [P] [US4] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/GatewayCommand.java`：`@Command(name="gateway")`；重命令——启动 Spring 上下文 + `Thread.join()` 保持常驻（多 Channel 守护后续节补）
- [X] T019 [P] [US4] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/ProviderListCommand.java`：`@Command(name="provider")` 下 `list`；轻命令，读 classpath 根 application.yaml 的 `oryxos.providers` 列 provider 名（SnakeYAML，不启动 Spring）
- [X] T020 [P] [US4] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/ToolListCommand.java`：`@Command(name="tool")` 下 `list`；轻命令，列内置 Tool 静态清单并注明第 20 节读真实注册表
- [X] T021 [P] [US4] 创建 `oryxos-cli/src/main/java/com/oryxos/cli/SessionListCommand.java`：`@Command(name="session")` 下 `list`；轻命令，JDBC 读 SQLite sessions 表列会话摘要；无表/无库时给提示不崩
- [X] T022 修改 `oryxos-cli/src/main/java/com/oryxos/cli/OryxOscCli.java`：`@Command` 增加 `subcommands={...}` 注册 9 组（init/status/chat/serve/gateway/profile/provider/tool/session，profile 含 4 个子命令、provider/tool/session 各含 list），合计 12 个子命令；保留裸命令打印版本行为；所有子命令 `mixinStandardHelpOptions=true` 支持 `--help`

**Checkpoint**: 12 子命令全部注册；`--help` 可跑（人工核验）

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 跨节一致性、门禁全绿、交付物核对

- [X] T023 运行 `mvn clean verify`（用本地 mvn 路径）全量门禁——含 Spotless/Checkstyle/P3C-PMD 静态检查；修复静态检查告警（**避开 P3C/ASM 解析不了的 Java 18+ 语法形态**，如增强 switch 的 default `->` 写法）；确保不删断言、不 `@Disabled`、不放宽阈值——**全绿：BUILD SUCCESS，9 模块 Spotless/Checkstyle 0 违规，51 测试 0 失败**（P3C 的 PMDException 是 PMD 6.55 对 Java 21 record/sealed 的既有解析警告，不 fail build）
- [X] T024 确认前序节全部测试回归绿（`mvn test` 全模块）；H4 六条全局不变量自查：①涉外 IO 首行过 Sandbox（ToolExecutor 调用位已留 24 节接线，本节无新增涉外工具）②LLM 成败落 llm_calls、工具成败落 tool_invocations（chat 实测：llm_calls 失败路径写入 success=0 + error_message）③grep 无明文 key（api-key 走 `${DEEPSEEK_API_KEY}` 占位）④session_id 只在 SessionManager 拼接（grep 确认 CliChannel/命令类无拼接，只有 getOrCreate 传三元组）⑤无 Reactor/CompletableFuture/自建线程池 ⑥无 Spring AI 自动工具执行路径（`internalToolExecutionEnabled(false)` 显式关闭，无 ChatClient 路径）
- [X] T025 对照课件"本节交付物"逐项存在性核对：`OryxOsCli` + 12 子命令类 + `CliChannel` + `Session` 实体 + `SessionRepository` + `SessionManager` + `sessions` 表（V2 脚本）；`SessionManagerTest`/`SessionRepositoryTest` 存在且非空；轻命令不启动 Spring、重命令显式 `@EnableJpaRepositories`/`@EntityScan` basePackages 的约定落实——**chat 实测日志 "Found 3 JPA repository interfaces"**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup；**BLOCKS 所有 US**（chat 需要 JpaSessionManager）
- **US3 (Phase 3)**: 依赖 Foundational；验证持久化地基
- **US1 (Phase 4)**: 依赖 Foundational（T013 的 CliSpringBootstrap 需要 JpaSessionManager）+ US3 的契约稳定
- **US2 (Phase 5)**: 依赖 Setup；轻命令不依赖持久化，可与 US1/US3 并行
- **US4 (Phase 6)**: 依赖 Foundational（session list 需要 sessions 表）+ US1 的 CliSpringBootstrap（serve/gateway 复用）
- **Polish (Phase 7)**: 依赖所有 US 完成

### User Story Dependencies

- **US1 (P1)**: 依赖 US3 的持久化地基；CLI 是第一个"用起来" Session 的入口
- **US2 (P1)**: 独立，仅依赖 Setup
- **US3 (P1)**: 依赖 Foundational（验证其实现）
- **US4 (P2)**: 依赖 US1（serve/gateway 复用 CliSpringBootstrap）+ Foundational（session list）

### Within Each User Story

- 测试先于/伴随实现（harness 先行），实现后立即跑该模块测试，红了当场修
- 测试方法名必须是英文（驼峰或 snake_case），课件中文名进 `@DisplayName`

### Parallel Opportunities

- T008/T009（两个测试类，同模块不同文件）可并行
- T015/T016（init/profile）、T017-T022（其余命令）互不依赖可并行
- T005/T006（SessionEntity/SessionRepository，不同文件）可并行

---

## Implementation Strategy

### MVP First

1. Phase 1: Setup → Phase 2: Foundational（会话持久化）
2. Phase 3: US3 验证（harness 绿 → 地基可信）
3. Phase 4: US1 chat（MVP——终端能跟 Agent 对话）
4. **STOP and VALIDATE**: `oryxos chat` 人工跑通一次多轮对话（Demo 一对话版）
5. Phase 5-6: US2/US4 补齐全部命令
6. Phase 7: 门禁全绿 + 交付物核对

### Incremental Delivery

- 每个 Phase 独立可测；US1 完成即可演示 Demo 一对话版
- US2/US4 的命令补齐不影响已交付的 chat

---

## Notes

- **关键回归点**：课件写出的 `同一三元组_历次getOrCreate都是同一个Session` 测试必须原样落地——断言逻辑逐条保真，方法名译英文，课件原文进 `@DisplayName`
- **session_id 只一处**：拼接只在 `JpaSessionManager`（生产实现），CLI/CliChannel 只传三元组；H4 不变量④ grep 兜底
- **`Session`（core 值对象）不改**：第 17 节字段不动；持久化经 storage 的 `SessionEntity` 编解码转换
- **`serve`/`gateway` 本节只做壳**：起 Spring 上下文即完成 FR-007 验证，WebServer 本体 26 节补
- **[P] 任务 = 不同文件、无依赖**
