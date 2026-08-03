# Research: CLI 命令行入口 + 会话持久化地基

**Feature**: CLI 命令行入口 + 会话持久化地基 | **Date**: 2026-08-03

## 决策记录

### 1. 重命令 Spring 启动器形态（`CliSpringBootstrap`）

**Decision**: 在 oryxos-cli 新建 `CliSpringBootstrap`，`chat`/`serve`/`gateway` 三个重命令共用：显式声明 `@EnableJpaRepositories(basePackages="com.oryxos.storage")` + `@EntityScan(basePackages="com.oryxos.storage")` + `@EnableAutoConfiguration` + `@ComponentScan`（或最小配置类），把 16/17 节的 POJO 引擎（`ReActLoop`/`PromptBuilder`/`ToolExecutor`/`AgentService`/`ProfileRegistry`/`ContextLoader`/`DefaultProviderService`/`SessionManager` 实现）以 `@Bean` 方法装配成 Spring 上下文。

**Rationale**: 课件"第四，重命令启动 Spring 后模块扫描范围容易埋雷"是本节点名坑：`scanBasePackages` 只管组件扫描，**不会**带动自动配置的 `@EnableJpaRepositories`/`@EntityScan` 跟着扫到别的模块——CLI 模块（`com.oryxos.cli`）与 storage 模块（`com.oryxos.storage`）不同 Java 包，不显式声明 basePackages 就得到 "Found 0 JPA repository interfaces"、审计写不进去直接报错退出。同时这是第一个把 16/17 节引擎接成 Spring 上下文的装配点——`chat` 要真跑起 ReAct 循环，必须有完整 bean 图。

**Alternatives considered**:
- 复用 `oryxos-boot` 的 `OryxOSApplication` 启动 → 那是 fat JAR 入口，包路径 `com.oryxos` 能扫到所有模块，但重命令应独立可控地起上下文，且课件明确"重命令才启动 Spring"，`serve`/`gateway` 后续还要往同一上下文挂 WebServer/Channel（否决）
- `new AnnotationConfigApplicationContext(...)` 手动注册 → 能行但把配置散在各命令类里，三个重命令重复（否决）

### 2. SessionManager 契约扩展（跨节契约改造）

**Decision**: `com.oryxos.core.SessionManager` 接口从第 17 节的 `save(Session)` 扩展为三方法：`Session getOrCreate(String channel, String user, String profileName)`、`Optional<Session> get(String sessionId)`、`void save(Session)`。`session_id` 的拼接（channel + user + profile 联合唯一）**只发生在 SessionManager 实现内部**这一处。

**Rationale**: 课件点名"id 的拼接只发生在 SessionManager 内部这一处，所有入口只提供三元组、不自己拼字符串——两处各拼一遍、格式差一个分隔符，同一个人就会出现两条互不相认的历史"（27 节缝隙③）。第 17 节 `AgentService` 只依赖 `save`，且已核实第 17 节没有接口实现类（只有测试 mock），加抽象方法安全。

**Alternatives considered**:
- 接口加默认方法实现拼接 → 拼接逻辑放接口可被测试绕开，违背"只此一处"的意图（否决）
- 新建独立 `SessionIdGenerator` → 又多一个概念，不如收敛在 SessionManager 内部（否决）

### 3. `Session`（core 值对象）与 `SessionEntity`（storage 实体）分离

**Decision**: 不动 `com.oryxos.core.Session`（第 17 节交付的内存值对象，ReActLoop 直接累积消息），在 oryxos-storage 新建 `SessionEntity`（JPA 实体，字段照 TechnicalSolution §9.2：session_id 主键、profile_name、channel、user_id、messages_json、status、created_at、last_active_at、archived_at），`JpaSessionManager` 负责两者转换。`messages_json` 编解码用 Jackson，编解码逻辑收在 `SessionEntity` 静态方法。

**Rationale**: core 保持 Spring-agnostic（目前只依赖 slf4j + snakeyaml），把 JPA 注解染进核心值对象会破坏这一性质；`Session` 字段第 17 节已定且被 ReActLoop/AgentService 广泛使用，不应为持久化改动。Jackson 经 spring-boot-starter-data-jpa 传递引入（本地仓库 jackson-databind 已核实存在）。

**Alternatives considered**:
- 直接给 `core.Session` 加 `@Entity` → 核心值对象耦合 JPA，破坏 Spring-agnostic（否决）
- 序列化用 Jackson `ObjectMapper` 直接序列化 sealed Message → 反序列化需注册多态类型信息，收在 SessionEntity 编解码方法里更内聚（采纳）

### 4. `SessionManagerTest` 测试放位

**Decision**: 课件验收 harness 点名 `SessionManagerTest`（幂等/隔离/id 只一处）与 `SessionRepositoryTest`（建表能存能读/序列化完整/模拟重启）。本设计：
- `oryxos-core/src/test/.../SessionManagerTest.java`：用纯内存 `SessionManager` 测试实现验证契约逻辑（幂等、隔离、id 唯一性），不启动 Spring，秒级；
- `oryxos-cli/src/test/.../SessionManagerTest.java`：对 `JpaSessionManager` 真实实现复核（临时 SQLite 文件、`JdbcTemplate` 或直接 `SessionRepository`，不起全 Spring 上下文），断言幂等/id 唯一性真实落库成立；
- `oryxos-storage/src/test/.../SessionRepositoryTest.java`：验证建表脚本/序列化往返/模拟重启历史仍在。

**Rationale**: 课件 harness 只给两个测试类名，但"幂等/隔离/id 只一处"的契约逻辑（core 纯内存可验）与"持久化真的能存能读"（storage/cli 需真实 SQLite）是两层，分开更清晰、各自秒级。测试方法名译英文、课件原文进 `@DisplayName`。

**Alternatives considered**:
- 只在 core 建一个 `SessionManagerTest` 用内存实现 → 验不了真实持久化（否决）
- 只在 storage 建一个全 `@DataJpaTest` 的 `SessionManagerTest` → 与既有 LlmCall/ToolInvocation RepositoryTest 模式一致但把 core 契约测试搬到 storage（否决，core 测试应留在 core）

### 5. `messages_json` 序列化方案

**Decision**: 用 Jackson `ObjectMapper` 把 `Session.messages()`（sealed Message 三型：UserMessage / AssistantMessage(content, toolCalls) / ToolResultMessage(toolCallId, toolName, content)）序列化为 JSON 文本存 `messages_json` 一列；反序列化按判别字段重建三型消息。编解码方法收在 `SessionEntity` 静态方法（`encodeMessages`/`decodeMessages`）。

**Rationale**: 课件要求"对话历史整体序列化成 JSON 存 messages_json 一列，核心阶段不做按条拆表"。sealed 三型消息结构简单，用手写判别（type 字段）比 Jackson 多态注册（`@JsonTypeInfo`）更可控、无需在 core 值对象上打注解。历史为空（首轮对话）时 `messages_json` 存 `[]`，回读为空列表，不抛异常。

**Alternatives considered**:
- `@JsonTypeInfo` 多态序列化 sealed 类型 → 需在 core 的 Message 上加 Jackson 注解，污染核心（否决）
- 只序列化文本内容、丢消息类型 → 回读无法重建角色/工具配对，审计与续聊会丢信息（否决）

### 6. 轻命令文件操作范围

**Decision**: `InitCommand` 创建 `.oryxos/` 工作区骨架目录（agents/skills/memory/sessions/logs）+ 空 `MEMORY.md` + `AGENT.md`/`SOUL.md`/`USER.md` 占位（或按 TechnicalSolution §8.1 既定结构）；`ProfileListCommand` 列 `.oryxos/agents/`（或 profiles）目录；`StatusCommand` 查配置/工作区状态；`SessionListCommand` 读 sessions 表（轻命令场景：无表/无库时给提示不崩）。

**Rationale**: 课件"轻命令直接走文件操作"。"init 初始化一个 OryxOS 工程"是本节的明确交付物（CLAUDE.md 12 子命令表）。工作区结构照 CLAUDE.md「工作区结构（运行时）」与 TechnicalSolution §8.1。注意 `.oryxos/profiles/` 已在宪法 v1.1.0 取消、改为 `.oryxos/agents/`（第 29 节 Agent 目录机制），但 `ProfileLoader`（16 节）仍按 profiles 目录实现——init 建目录时以 `agents/` 为主、`profiles/` 兼容位由后续节收敛，本节 init 建最小结构不越界。

**Alternatives considered**:
- init 建全部 9 个子目录 + 空表 → 会话/日志等目录 runtime 按需创建更干净，init 只建定义文件（AGENT.md 等）与必要目录（采纳，范围克制）

### 7. `serve`/`gateway` 本节只做壳

**Decision**: `ServeCommand`/`GatewayCommand` 重命令先启动 Spring 上下文（复用 `CliSpringBootstrap`），WebServer/多 Channel 本体分别由 26 节/后续节补——本节命令 `run()` 起上下文后打印"服务启动"占位日志（或按 26 节接线点留 TODO）。

**Rationale**: 课件"serve 启动 Web Service（26 节细讲）、gateway 起守护进程挂多个通道"——本节交付的是命令壳与 Spring 启动接线（FR-007 的坑就在这个壳里），本体后补。起上下文即证明"重命令才启动 Spring"的断言成立。

**Alternatives considered**:
- serve/gateway 本节做成 no-op 不启动 Spring → 丢掉了 FR-007 要验证的"重命令启动 Spring"（否决）

### 8. CLI 用户身份

**Decision**: `chat` 命令的 `user_id` 取当前系统用户名（`System.getProperty("user.name")`，缺省回退 `"local"`），channel 固定 `"cli"`，profile 取 `--profile` 参数（默认 `"default"`）。

**Rationale**: 课件骨架 `sessionManager.getOrCreate("cli", currentUser(), profileName)`——CLI 是单机本地交互，用系统用户名标识用户最自然；channel 固定 `"cli"` 与 Web（`"web"`）/定时（`"scheduler"`）区分（TechnicalSolution §8.5 定时任务会话身份口径一致）。

**Alternatives considered**:
- 固定 `"local"` 用户 → 多用户共享机器时会话串号（否决）
- 让用户传 `--user` 参数 → 违背"CLI 只做入口、不自己拼会话身份"（否决）
