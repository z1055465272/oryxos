# Feature Specification: CLI 命令行入口 + 会话持久化地基

**Feature Branch**: `018-lesson18-cli`

**Created**: 2026-08-03

**Status**: Draft

**Input**: User description: "第18节需求：CLI 命令行入口——OryxOS 的命令行入口层，薄薄的'门'，消息进出，不干活"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 终端交互式对话 (Priority: P1)

用户在终端输入 `oryxos chat`，进入与默认 Agent 的交互式多轮对话。终端出现 `>` 提示符，用户输入一句话，Agent 返回最终答复，如此一问一答持续，直到用户输入 `/quit` 退出命令。用户也可以 `oryxos chat --profile weather` 指定与某个 Agent 对话。

**Why this priority**: 这是 Provider、ReAct 之后第一个"看得见摸得着"的东西——用户第一次能在终端里真正跟 Agent 说上话，是 Demo 一对话版的主干。CLI 在这里只做"读输入→交引擎→打印结果"，不承担任何 Agent 智能。

**Independent Test**: 会话层自动测试：同一三元组（channel=cli、user、profile）多次获取返回同一个会话、不同三元组返回不同会话；`/quit` 退出、交互循环、命令分流属进程级行为，留在人工清单。

**Acceptance Scenarios**:

1. **Given** 用户在终端执行 `oryxos chat`，**When** 输入一行消息，**Then** 引擎处理后把最终答复打印到终端，回到 `>` 提示符等待下一行。
2. **Given** 用户对话中输入 `/quit`，**When** 循环读到该行，**Then** 跳出循环、命令正常退出。
3. **Given** 用户执行 `oryxos chat --profile weather`，**When** 进入交互，**Then** 使用名为 weather 的 Agent 配置进行对话。

---

### User Story 2 - 命令按轻重分流，轻命令秒回 (Priority: P1)

用户执行 `oryxos init`、`oryxos profile list` 这类不调模型的轻命令，命令立即返回结果，不等待 Spring 上下文启动；`oryxos chat`、`oryxos serve`、`oryxos gateway` 这类要调模型的重命令才启动 Spring 上下文。判断标准：这个命令要不要调模型 / 跑引擎。

**Why this priority**: Spring Boot 在 JDK 21 下启动要 2~4 秒，对 `serve` 常驻服务无所谓，但对"看一眼就退"的命令等 4 秒太难受。这个分流一开始就要定，不然要么全都慢、要么后面改起来伤筋动骨。

**Independent Test**: 命令分流属进程级行为（是否启动 Spring 上下文），写自动化测试成本大于收益，留人工清单：执行 `oryxos profile list` 应秒回，执行 `oryxos chat` 才看到 Spring 启动日志。

**Acceptance Scenarios**:

1. **Given** 用户执行 `oryxos init`，**When** 命令运行，**Then** 立即完成工作区初始化并返回，无 Spring 启动延迟。
2. **Given** 用户执行 `oryxos chat`，**When** 命令运行，**Then** 启动 Spring 上下文（日志出现），随后进入交互。

---

### User Story 3 - 会话持久化：多轮对话历史重启不丢 (Priority: P1)

用户对话的历史被整体持久化到 `sessions` 表（`messages_json` 一列）。同一三元组（channel+user+profile）的对话复用同一个会话，历史自然累积；服务重启后历史仍在，对话能接得上。

**Why this priority**: 会话持久化是后面所有入口（Web、定时）共用的地基，出口径问题最难查（27 节的缝隙③），在这节就钉死。CLI 是第一个真正"用起来" Session 的入口，所以会话持久化归这节负责。

**Independent Test**: 自动化 harness 覆盖——`SessionManagerTest` 断言同一三元组幂等返回同一个会话、任一元素不同则不同会话、id 拼接只发生在会话管理层；`SessionRepositoryTest` 断言手工建表脚本建出的表能存能读、`messages_json` 序列化回读后消息完整、模拟重启（新建数据访问上下文重查）历史还在。

**Acceptance Scenarios**:

1. **Given** 同一三元组已存在会话，**When** 再次以相同三元组获取，**Then** 返回同一个会话（幂等，多轮对话靠它串起来）。
2. **Given** 三元组中 channel/user/profile 任一不同，**When** 获取会话，**Then** 得到不同的会话，互不混淆。
3. **Given** 会话已持久化，**When** 模拟服务重启后按会话标识重查，**Then** 对话历史完整还原。

---

### User Story 4 - 12 个子命令统一可查可跑 (Priority: P2)

用户能跑全部 12 个子命令：`init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`。每个命令都能看 `--help` 帮助信息，参数解析、帮助、报错提示由 Picocli 统一处理。

**Why this priority**: OryxOS 的所有操作都通过子命令来做，12 个命令构成对外操作的完整入口面，是 CLI 作为"门"的职责所在。

**Independent Test**: 12 个命令的 `--help` 属进程级行为，留人工清单：逐个执行 `--help` 确认 Picocli 正常输出。

**Acceptance Scenarios**:

1. **Given** 用户对任一子命令追加 `--help`，**When** 执行，**Then** 输出该命令的帮助信息（Picocli 自带）。
2. **Given** 用户执行 `oryxos status`、`oryxos provider list`、`oryxos tool list`、`oryxos session list`，**When** 命令运行，**Then** 各自输出对应的配置或状态信息。

---

### Edge Cases

- 用户连续输入空行时，会话与引擎如何处理（不崩溃）。
- `/quit` 前后带空格（`trim` 后再判断退出）。
- `chat` 指定了不存在的 profile 时，引擎按既有 Profile 注册表逻辑报错（CLI 不自行判断）。
- 手工建表脚本重复执行时是否幂等（`CREATE TABLE IF NOT EXISTS`）。
- 首次对话（会话历史为空）时序列化与回读是否正常。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 命令行入口——系统提供命令行主入口（main 函数），注册 12 个子命令：`init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`；参数解析、帮助、报错提示统一交给命令行解析库处理，每个子命令一个命令类，互不干扰。

- **FR-002**: 命令按轻重分流——不调模型、不跑引擎的命令（如 `init`、`profile list`）不启动 Spring 上下文、直接走文件操作、秒回；要调模型、跑引擎的命令（如 `chat`、`serve`、`gateway`）才启动 Spring 上下文。判断标准固定为"这个命令要不要调模型 / 跑引擎"。

- **FR-003**: chat 交互——`chat` 命令读标准输入、写标准输出，维护当前会话，每收到一行输入交给统一引擎入口处理并把最终答复打印到终端，直到用户输入 `/quit`（去空白后判断）退出；CLI 不承担任何 Agent 智能（不想、不调模型、不执行工具）。

- **FR-004**: 会话标识只在一处拼接——会话标识由 channel+user+profile 三元组在会话管理层内部唯一生成，CLI 只提供三元组（channel 固定为 `cli`），不在命令层拼接字符串；同一三元组幂等返回同一个会话，任一元素不同则不同会话。

- **FR-005**: 会话持久化——系统提供会话的获取/创建与保存能力：按三元组获取或创建、按会话标识获取、保存；对话历史整体序列化为 JSON 存入单一列，核心阶段不做按条拆表。

- **FR-006**: 会话表手工建表——`sessions` 表用手工建表脚本创建（SQLite 不依赖自动迁移），字段含：会话标识（主键）、关联的 Agent 名、接入渠道、用户标识、JSON 序列化的对话历史、状态（active/archived）、创建时间、最后活跃时间、归档时间（可空）。

- **FR-007**: 重命令启动 Spring 时模块扫描范围显式声明——重命令启动 Spring 上下文时，持久化相关组件（数据访问接口、实体）的扫描范围要显式声明，覆盖 CLI 模块与持久化模块处于不同包的情形，保证启动时能发现数据访问接口（不会出现"发现 0 个仓库接口"）。

### 明确不做（边界）

- 会话历史不做按条拆表——整体序列化进 `messages_json` 一列，拆表留扩展阶段。
- 命令分流（轻命令不起 Spring）与 12 个子命令的 `--help` 属进程级行为，写自动化测试的成本大于收益，留在人工清单。
- 认证 / RBAC——核心阶段假设内网，非本节范围。
- `serve` / `gateway` 的完整实现归 Web Service 节，本节只做命令壳与 Spring 启动接线。

### Key Entities

- **Session（会话）**: 一次对话的元数据与历史——关联的 Agent 名、接入渠道、用户标识、JSON 序列化的对话历史（多轮 LLM 响应与工具结果整体序列化）、状态与时间戳。本节从第 17 节的内存形态升级为可持久化实体。
- **会话管理（SessionManager）**: 会话的获取/创建/保存契约——按三元组幂等获取或创建、按标识获取、保存。第 17 节已定义保存契约，本节补齐完整三个方法并实现持久化。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 同一三元组多次获取返回同一个会话、任一元素不同则不同会话——幂等与隔离由自动化测试断言通过。
- **SC-002**: 会话能存能读，序列化的对话历史回读后消息完整，模拟重启后历史仍在——由自动化测试断言通过。
- **SC-003**: 12 个子命令均注册并可跑，`--help` 正常（人工核验）。
- **SC-004**: 轻命令秒回、重命令才启动 Spring（人工核验）。
- **SC-005**: 重命令启动日志出现"发现 N 个 JPA 仓库接口"且 N > 0（人工核验）。
- **SC-006**: 全部自动化测试通过、构建门禁全绿。

## Assumptions

- 前置依赖：第 17 节交付的 AgentService（`process(Session, String)` 统一编排入口）、SessionManager 契约（`save`）、Profile 注册表、会话内存形态已就位可用。
- 第 17 节已定义 SessionManager 接口（含 `save`），本节在其上补齐 `getOrCreate`/`get` 并落地持久化实现。
- `chat` 的引擎入口、`serve`/`gateway` 的 Web 能力分别依赖第 17 节与后续 Web 节交付，本节只做命令壳与启动接线。
- 三种运行模式（chat/serve/gateway）共享同一份 Profile 配置与同一套会话存储。
- 外部依赖无新增：命令行解析库（Picocli）已在父 POM 锁定。
- 项目使用 JDK 21 + Spring Boot 3.x + SQLite，同步阻塞模型配合虚拟线程；凭证走环境变量，不在代码或配置中硬编码。
- 手工建表脚本与既有审计表脚本同源维护，`sessions` 表结构照技术方案 9.2。
