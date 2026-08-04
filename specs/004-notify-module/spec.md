# Feature Specification: Notify 模块 — Agent 主动推送出口

**Feature Branch**: `019-lesson19-notify`

**Created**: 2026-08-04

**Status**: Draft

**Input**: User description: "第19节需求：Notify 模块——Agent 主动推送消息的统一出口"

## Clarifications

### Session 2026-08-04

- Q: channel 参数如何映射到 notify_channels 列表中的具体渠道？NotifyChannelConfig 是否需要单独的 name 字段？ → A: 用 `type` 字段值（如 "webhook"）作为渠道标识，不需要额外 name 字段。channel 参数匹配 NotifyChannelConfig 的 type 值。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Webhook 适配器发送通知 (Priority: P1)

运维人员配置了一个 webhook 通知渠道（如企业微信群机器人 URL），Agent 在完成任务后需要把结果推送到这个 webhook。调用方只需表达"把这条内容发到那个渠道"，不用关心底层是 HTTP POST、还是未来可能的企业微信 SDK——适配器负责把通用意图翻译成具体协议。

**Why this priority**: 这是 Notify 模块的核心能力——没有它，Agent 的结果只能烂在 Session 里，定时任务毫无意义。Webhook 适配器是核心阶段唯一实现，必须先行交付并可独立验证。

**Independent Test**: 在本地启动一个假 webhook 服务，构造一个 NotifyTarget（含 webhook URL），调用适配器的 send 方法，验证假 webhook 收到了正确的 POST 请求。

**Acceptance Scenarios**:

1. **Given** 一个配置了 webhook URL 的通知目标，**When** 调用方通过适配器发送内容 "任务完成"，**Then** 目标 webhook 收到一个 POST 请求，Content-Type 为 JSON，请求体包含 `{"content": "任务完成"}`。
2. **Given** 两个不同 URL 的通知目标 A 和 B，**When** 分别向 A 和 B 发送内容，**Then** 各自的 webhook 分别收到请求，URL 来自 NotifyTarget 配置而非硬编码。
3. **Given** 目标 webhook 服务返回 5xx 错误，**When** 适配器发送内容，**Then** 异常向上传播、不被静默吞掉——调用方能感知到推送失败。

---

### User Story 2 - Agent 通过 Notify 工具推送 (Priority: P2)

Agent 在执行过程中需要主动把一条消息推送出去。它调用一个统一的 `notify` 能力，传内容和可选的渠道名，系统自动从当前 Agent 配置中解析渠道、过安全校验、发出消息。Agent 不用知道 webhook URL 是什么、也不用关心底层是 HTTP 还是别的协议——这些是配置和适配器的事。

**Why this priority**: NotifyTools 是 Agent 视角的入口，把底层适配器和安全校验串联成 Agent 可直接调用的单一操作。它依赖 P1 的适配器，但自身逻辑（渠道解析、安全校验顺序）需要独立验证。

**Independent Test**: Mock 掉底层适配器和安全校验组件，调用 notify 方法，验证三步走的顺序和参数传递正确。

**Acceptance Scenarios**:

1. **Given** 当前 Agent 配置了两个通知渠道 `[{type: "webhook-ops", url: "..."}, {type: "webhook-dev", url: "..."}]`，**When** Agent 调用 notify 且不指定渠道类型，**Then** 系统自动选择第一个渠道（type="webhook-ops"）发送。
2. **Given** 当前 Agent 未配置任何通知渠道，**When** Agent 调用 notify，**Then** 系统明确报错（而非静默返回成功让 Agent 以为已发出）。
3. **Given** 安全校验组件可用，**When** Agent 调用 notify，**Then** 安全校验必须在实际发送之前执行——如果校验顺序颠倒，意味着"往外推"绕过了安全白名单，这是一个安全漏洞。
4. **Given** 当前 Agent 配置了通知渠道，**When** Agent 调用 notify 并指定渠道类型 "webhook-dev"，**Then** 系统用 type="webhook-dev" 渠道的配置发送。

---

### User Story 3 - Agent 配置中声明通知渠道 (Priority: P3)

运维人员在定义 Agent 时，在 Agent 配置文件中声明这个 Agent 可以把消息推到哪些渠道。每个渠道有类型（如 webhook）和对应的连接配置（如 URL），URL 支持从环境变量读取（避免明文写在配置文件里）。

**Why this priority**: 配置是 Agent 和渠道之间的桥梁——没有它，P2 的渠道解析无从谈起。它是支撑性的，优先级低于核心发送逻辑。

**Independent Test**: 加载一份包含 notify_channels 的 Agent 配置，验证渠道列表被正确解析为结构化数据。

**Acceptance Scenarios**:

1. **Given** Agent 配置中包含 `notify_channels: [{type: webhook, url: "https://hooks.example.com/xxx"}]`，**When** 加载配置，**Then** 解析出一个渠道对象，type 为 "webhook"，url 为配置的值。
2. **Given** Agent 配置中 notify_channels 为空列表，**When** 加载配置，**Then** 渠道列表为空（不报错，因为不是所有 Agent 都需要通知能力）。
3. **Given** Agent 配置中 url 使用环境变量占位符 `${HOOK_URL}`，**When** 环境变量 HOOK_URL 已设置，**Then** 解析后的 url 为环境变量的值。

---

### Edge Cases

- 渠道配置的 url 为空字符串或 null 时，应明确报错而非发一个空 URL 的 HTTP 请求。
- webhook 响应超时时，异常应向上传播（超时也是一种失败，不应静默）。
- 同一个 Agent 配置了多个同类型渠道时，按列表顺序匹配第一个符合 type 的渠道。
- Sandbox 安全校验组件在本阶段是占位实现（默认放行），但调用链已就位，后续章节只需替换实现。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须提供通知适配器抽象，定义"将指定内容发送到指定通知目标"的统一操作，接口签名不包含任何特定渠道类型（如 webhook、企业微信）的细节。
- **FR-002**: 系统必须提供通知目标数据结构，包含渠道类型标识和键值对形式的渠道配置，由各适配器实现自行解释配置内容。
- **FR-003**: 系统必须提供基于 HTTP webhook 的通知适配器实现，将内容包装为 JSON 格式（`{"content": "..."}`），以 POST 请求发送到目标 URL，Content-Type 为 `application/json`。
- **FR-004**: webhook 适配器在目标服务器返回 5xx 错误时必须将异常向上传播，不得静默吞掉失败。
- **FR-005**: webhook 适配器的目标 URL 必须从通知目标配置中读取，不得硬编码。
- **FR-006**: 系统必须提供 Agent 可调用的统一通知操作，接收通知内容和可选的渠道类型参数（`channel` 参数匹配 NotifyChannelConfig 的 `type` 字段值，如 "webhook"）。
- **FR-007**: 通知操作必须按以下顺序执行：先解析渠道配置，再执行安全校验，最后发送消息。安全校验必须在发送之前完成，顺序不可颠倒。
- **FR-008**: 当前 Agent 未配置任何通知渠道时，通知操作必须明确报错（不得返回成功）。
- **FR-009**: 通知操作的渠道参数缺省时，系统自动使用配置中的第一个渠道（按列表顺序）。
- **FR-010**: Agent 配置必须支持 `notify_channels` 字段，包含渠道列表，每个渠道有类型和连接配置。
- **FR-011**: 渠道配置中的敏感值（如 webhook URL）必须支持从环境变量读取（占位符格式），不得要求明文写在配置文件中。
- **FR-012**: 安全校验组件在本阶段以占位方式接入（默认放行），为后续沙箱模块预留接口。

### Key Entities

- **通知渠道适配器（NotifyChannelAdapter）**: 通知发送的抽象接口，定义"发送内容到通知目标"的操作契约，不绑定任何具体渠道协议。
- **通知目标（NotifyTarget）**: 描述"发到哪里"的值对象——包含渠道类型（如 webhook）和配置键值对（如 url）。由适配器实现按类型解释配置。
- **Webhook 通知适配器（WebhookNotifyAdapter）**: 通知渠道适配器的 HTTP webhook 实现，负责将内容以 JSON POST 方式发送到目标 URL。
- **Notify 工具（NotifyTools）**: Agent 可见的统一通知入口，串联渠道解析 → 安全校验 → 适配器发送三步。
- **通知渠道配置（NotifyChannelConfig）**: Agent 配置中 notify_channels 列表的单项——包含渠道类型（`type`，同时作为渠道标识用于 `channel` 参数匹配）和渠道特定连接参数（如 `url`），支持环境变量占位符。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: webhook 适配器的发送行为可通过本地假 webhook 服务完整验证，无需外部网络依赖。
- **SC-002**: 所有错误路径（渠道未配置、webhook 5xx、空 URL）都有明确的失败信号，不存在静默失败路径。
- **SC-003**: 安全校验 → 消息发送的执行顺序被自动化测试锁定，顺序颠倒会导致测试失败。
- **SC-004**: 通知渠道适配器接口可以接受新的渠道类型实现（如企业微信 SDK），无需修改接口签名或调用方代码。
- **SC-005**: 现有自动化测试套件（前序节交付的测试）全部保持绿色，本节改动不破坏已有功能。

## Assumptions

- 核心阶段只实现通用 webhook 适配器，企业微信、飞书、钉钉等专用 SDK 适配器留给扩展阶段。
- 安全校验（沙箱白名单）的具体逻辑由后续章节实现，本节仅创建接口和占位实现（默认放行），确保调用链就位。
- 通知渠道配置跟随 Agent 配置（Profile YAML），不做独立的数据库表和 CRUD API。
- `@Tool` 注解的完整注册机制（ToolRegistry）由下一节（20 节）交付；本节 NotifyTools 的 `@Tool` 方法可先定义，完整接线在 20 节后补验。
- 通知结果像其他工具调用一样走已有的审计记录路径（tool_invocations 表），不新增审计逻辑。
- Agent 的当前配置通过已有的 ThreadLocal 机制（ProfileContext）获取，本节在其上新增渠道解析方法。
