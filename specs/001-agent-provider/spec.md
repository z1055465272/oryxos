# Feature Specification: Agent Provider

**Feature Branch**: `016-lesson16-agent-provider`

**Created**: 2026-08-03

**Status**: Draft

**Input**: User description: "第16节需求：Agent Provider —— 为 OryxOS 对接大模型（LLM）提供统一的前台层"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Provider 路由与调用 (Priority: P1)

作为 ReAct 循环，我需要通过 Provider 层向大模型发送请求并获取响应，这样我不用关心底层用的是哪家模型、每家协议有什么差异。我只需要传入 Profile（指定用哪个 provider、什么 model）和 Prompt（要发送的内容），Provider 负责挑对模型、发起调用、把原始响应返回给我。调用无论成功还是失败，都必须留下审计记录。

**Why this priority**: Provider 是 ReAct 循环运转的前提——没有 LLM 调用能力，Agent 大脑无法运作。这是系统的最基础能力。

**Independent Test**: Mock 两个 ChatModel，通过 Provider 按 Profile 分别调用，验证路由准确、响应返回、审计落库。

**Acceptance Scenarios**:

1. **Given** 系统配置了两个 Provider（deepseek 和 kimi），**When** 上层用指定 kimi 的 Profile 调用 chat 方法，**Then** 只有 kimi 的 ChatModel 被调用，deepseek 未被触碰。
2. **Given** 上层传入的 Profile 引用了一个不存在的 provider 名称，**When** 调用 chat 方法，**Then** 系统抛出明确异常（不静默选错模型）。
3. **Given** 一次 LLM 调用成功返回，**When** 调用结束，**Then** llm_calls 表多一条记录，success=true，包含 provider 名、model 名、token 用量、耗时。
4. **Given** 一次 LLM 调用失败（网络超时），**When** 异常被捕获，**Then** llm_calls 表多一条记录，success=false，error_message 记录失败原因，同时异常继续上抛给调用方。

---

### User Story 2 - 工具 Schema 翻译 (Priority: P2)

作为 ReAct 循环，我需要把当前可用的 OryxTool 列表翻译成模型能理解的 Function Calling 格式，随 LLM 请求一起发送。Provider 只做翻译（生成 schema 描述），不执行工具。

**Why this priority**: 工具调用是 ReAct 循环的核心环节（Reason → Act 中的 Act），但翻译只是通路的一部分，比基础调用能力低一个优先级。

**Independent Test**: 传入一个 OryxTool，验证产出物包含正确的 schema 字段且不包含任何执行逻辑。

**Acceptance Scenarios**:

1. **Given** 一个 OryxTool 定义了名称、描述和参数 schema，**When** 将其翻译为 Spring AI 工具描述格式，**Then** 产出物中名称和参数 schema 与原始 OryxTool 一一对齐。
2. **Given** 翻译完成的工具描述，**When** 随 LLM 请求发送，**Then** 请求中明确禁用了自动工具执行（autoExecuteTools=false），工具执行权保留在调用方。
3. **Given** 多个 OryxTool 同时传入，**When** 全部翻译完成，**Then** 每个 Tool 的 schema 独立、互不干扰。

---

### User Story 3 - Profile 解析与加载 (Priority: P3)

作为系统启动流程，我需要从 `.oryxos/profiles/` 目录加载 Agent 的 Profile 配置，解析出全部字段（身份、provider 选择、工具列表、channel、调度等），并校验 provider 引用的有效性。坏文件记错误日志但不阻断其余 Profile 的加载。

**Why this priority**: Profile 是 Provider 的输入来源，但 Profile 加载属于基础设施层，不直接参与 LLM 调用链路——优先级排在调用能力和工具翻译之后。

**Independent Test**: 准备合法 YAML、含不存在 provider 的 YAML、语法错误的 YAML 各一份，验证加载器的解析和校验行为。

**Acceptance Scenarios**:

1. **Given** `.oryxos/profiles/` 下有一份全字段合法的 Profile YAML，**When** 系统启动时扫描加载，**Then** 所有字段正确解析为 Profile 对象，包括 identity、provider（name/model/temperature）、tools、skills、channels、bootstrap、settings 等全部字段。
2. **Given** 一份 Profile YAML 引用的 provider.name 在全局配置中不存在，**When** 加载该校验，**Then** 该 Profile 被标记为"校验失败"并记录错误日志，不影响其他合法 Profile 继续加载。
3. **Given** `.oryxos/profiles/` 下有一份 YAML 语法错误的文件，**When** 加载过程遇到该文件，**Then** 记录错误日志，跳过该文件继续加载其余 Profile。

---

### Edge Cases

- 当全局 `oryxos.providers` 列表为空（没有配置任何 provider）时，加载应正常完成，但任何使用 provider 的调用会因找不到映射而报错。
- Profile 的 api_key 字段使用 `${ENV_VAR}` 占位符时，若环境变量未设置，应在加载时给出清晰报错（不静默空值）。
- Profile YAML 中某些可选字段（如 schedules、notify_channels）缺失时，对应字段使用合理的默认值（空列表/空 Map），不报错。
- 同一个 provider name 在全局配置中出现多次时，应当检测并拒绝（或最后一条覆盖并警告）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 支持从 `.oryxos/profiles/` 目录加载 YAML 格式的 Profile 配置文件，解析出全部定义字段（name、description、identity、provider、tools、skills、mcp_servers、channels、notify_channels、schedules、bootstrap、settings），存放在内存索引中按 name 查找。
- **FR-002**: 系统 MUST 在加载 Profile 时校验其引用的 provider.name 是否存在于全局配置的 providers 列表中；不存在则拒绝该 Profile（记错误日志），不静默放行。
- **FR-003**: Profile YAML 解析失败（语法错误、格式错误）时，系统 MUST 记录错误日志并继续加载其余 Profile，不阻断启动。
- **FR-004**: 系统 MUST 通过 `application.yaml` 的 `oryxos.providers` 配置段声明可用的 Provider 列表，每个条目包含 name 和 api-key（api-key 取值 MUST 支持 `${ENV_VAR}` 环境变量占位符，不承载明文 key）。
- **FR-005**: 系统 MUST 在启动时按 `oryxos.providers` 配置为每个 Provider 创建对应的模型连接，并维护一份 provider name 到模型实例的显式映射表——不得依赖按类型扫描 Spring 容器中的 Bean 来区分不同 Provider。
- **FR-006**: 系统 MUST 对外暴露统一的 LLM 调用方法，接收会话标识、Profile 和 Prompt，按 Profile 中的 provider name 从映射表中选取正确的模型实例执行调用。
- **FR-007**: 当 Profile 指定的 provider name 在映射表中不存在时，系统 MUST 抛出明确异常（如 ProviderNotFoundException），不得返回空结果或静默使用其他 Provider。
- **FR-008**: 每次 LLM 调用 MUST 写入 `llm_calls` 审计表，记录：会话标识、provider 名称、model 名称、token 用量（prompt_tokens、completion_tokens、total_tokens）、调用耗时、成功/失败状态；调用成功时 success=true、error_message 为空；调用失败时 success=false、error_message 记录失败原因。
- **FR-009**: LLM 调用失败（超时、限流、模型报错等）时，系统 MUST 先写审计记录再继续上抛异常——失败不能没有痕迹。
- **FR-010**: 系统 MUST 支持将 OryxTool 的参数 schema 翻译为 LLM 可理解的工具描述格式，随 LLM 请求一并发送；翻译过程 MUST 只生成 schema 描述、不包含工具执行逻辑。
- **FR-011**: 发送给 LLM 的请求 MUST 明确关闭自动工具执行，确保工具执行权完全保留在调用方（ReAct 循环 + ToolExecutor）手中。
- **FR-012**: `llm_calls` 表的建表 MUST 使用手工维护的 DDL 脚本（不依赖 ORM 框架的自动建表/迁移），该表 MUST 包含 `success` 和 `error_message` 列，确保失败调用也有完整审计痕迹。

### Key Entities

- **Profile**: 一个 Agent 的完整运行配置，包含：名称、描述、身份设定（agent_name / prompt）、provider 选择（name / model / temperature）、工具列表、Skill 列表、MCP server 列表、接入渠道列表、通知渠道列表、定时调度列表、启动引导文件列表、运行时设置（最大迭代次数 / 最大历史轮数）。从 YAML 文件加载。
- **LlmCall**: 一次 LLM 调用的审计记录，包含：会话标识、provider 名称、model 名称、prompt token 数、completion token 数、total token 数、调用耗时、成功标志、错误信息。写入关系型存储。
- **Provider 全局配置**: 实例级别的 Provider 声明，包含 provider name 和 api-key 来源（环境变量引用），定义在应用配置文件中，与 Profile 层的 provider 引用形成两层配置结构。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 自动化测试套件全部通过——所有单测覆盖：双 provider 路由不串台、不存在 provider 报错、成功与失败审计写入、工具 schema 翻译正确性、自动执行关闭验证、Profile 加载与校验。构建命令 `mvn test` 全绿即代表验收通过。
- **SC-002**: 集成冒烟测试通过——使用真实 API key 完成至少一次 LLM 调用，验证从配置到响应的完整链路通畅。
- **SC-003**: 代码库中搜索不到任何明文 API key（如 `sk-` 前缀），所有凭证均通过环境变量注入。
- **SC-004**: 手工建表脚本创建的 `llm_calls` 表结构与实体定义完全一致，包含 `success` 和 `error_message` 两列且可正常读写。

## Assumptions

- 项目基于 JDK 21 + Spring Boot 3.x + Spring AI Alibaba，ChatModel 的确切 API 以实际依赖版本为准。
- 核心阶段的 Profile 从 `.oryxos/profiles/` 目录加载（CLAUDE.md 中的旧结构），后续节再切到 Agent 目录模式。
- Provider 的核心阶段目标是最小可用——不包含 fallback、hedge racing、熔断、成本看板；故障时直接上抛异常。
- `llm_calls` 表核心阶段只做写入，不提供查询 API；查询和审计报表放扩展阶段。
- OryxTool 接口和 ToolResult 记录类已在 oryxos-core 中定义，本节直接复用。
- ProviderService 接口骨架已在 oryxos-provider 中定义（仅有 resolve 方法），本节扩展为完整实现并新增 chat 方法签名。
