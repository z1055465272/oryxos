# Feature Specification: 定时任务模块 — 第三种触发源，让 Agent 到点自己干活

**Feature Branch**: `025-lesson25-scheduler`

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description: "第25节需求：定时任务模块——第三种触发源，让 Agent 不用人推、到点自己说话。定时配置（cron/时区/消息）在 Profile 里声明，启动时动态注册，到点触发走跟 CLI/Web 完全一样的 AgentService 入口；进程内本地锁防重叠；失败只记日志不崩调度器且照常审计；时区显式声明；会话身份固定三元组。"

## Clarifications

（暂无——本 feature 全部按第 25 节课件与 `docs/TechnicalSolution.md` §8.5 落地，无歧义项。）

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 到点自动触发，Agent 无需人喂话（每日 PR 评审）(Priority: P1)

运营方配置了一个"每日 PR 评审"Agent：Profile 里声明了定时规则（cron 表达式、时区、到点要说什么话），没有人在每天早上手动问它。到点了，系统自己拼一条消息喂给 Agent，Agent 照常走 ReAct 循环、调工具、写审计——从 Agent 的视角看，这次触发跟用户在 CLI/Web 里手动发一条消息没有任何区别。

**Why this priority**: "到点自动干活"是定时任务的本质价值，也是 31 节两个 Demo（每日天气、每日科技日报）的地基。这是本 feature 的 MVP 核心。

**Independent Test**: 直接调用一次触发（`runOnce`），断言统一入口被调用且参数是该 Profile 的定时消息；再断言会话身份三元组固定、同一 Profile 历次触发复用同一 Session。

**Acceptance Scenarios**:

1. **Given** 一个 Profile 声明了一条定时规则（含 cron、时区、消息），**When** 该任务到点被触发，**Then** 系统拼出配置里的消息、交给统一入口处理，走跟手动消息完全一样的处理链路。
2. **Given** 同一 Profile 的定时任务被触发两次，**When** 记录两次的会话身份，**Then** 三元组固定为（scheduler, scheduler, profileName），两次拿到同一个会话，历史自然累积。

---

### User Story 2 - 上一次没跑完，本次触发不叠加（重叠执行防护）(Priority: P1)

一个每分钟触发的定时任务，某一次 ReAct 循环跑了超过一分钟还没结束。下一次触发点到了，系统判断上一次还在跑，直接跳过这次触发——不排队、不堆积、不并行跑两份。这样同一个 Agent 不会因为一次慢循环被后续触发点叠出一堆并发任务。

**Why this priority**: 重叠执行是定时任务最容易踩的坑之一，跳过而非排队是"单实例本地锁"这个设计的最小正确实现。

**Independent Test**: 占住某任务 id 的锁，再触发一次该任务，断言统一入口没有被再次调用（没有叠加执行）。

**Acceptance Scenarios**:

1. **Given** 某个定时任务的锁被上一次执行占着，**When** 下一次触发点到了，**Then** 本次触发直接跳过，不调用统一入口、不排队。

---

### User Story 3 - 任务失败不崩调度器、锁不卡死，且照常留审计 (Priority: P1)

一次定时任务执行过程中抛了异常（模型调用失败、工具执行失败等）。这次失败不能把整个调度器带崩，不能影响其他定时任务的下一次触发；同时失败本身必须留痕——审计该怎么写还怎么写，跟手动触发失败没有区别。最关键的是：锁必须在 finally 里放掉，否则这个任务从此"卡死"，永远跳过后续触发。

**Why this priority**: 失败隔离 + 不留死锁是调度器的可靠性底线。只断言"不抛异常"不够，必须用"再触发一次还能进来"来证明锁真的放了。

**Independent Test**: 让统一入口抛异常，触发一次（断言不外抛）；再触发一次，断言统一入口被调用了两次（证明锁已释放、没有永久卡死）。

**Acceptance Scenarios**:

1. **Given** 统一入口在处理定时消息时抛异常，**When** 触发该任务，**Then** 异常不外抛（调度器不崩），只记日志。
2. **Given** 同上场景，**When** 紧接着再触发一次，**Then** 第二次能正常进入统一入口（锁在 finally 里被释放，没有永久卡死）。

---

### User Story 4 - cron 按声明的时区跑，不被服务器时区带偏 (Priority: P2)

运营方配置的 cron 表达式按"用户以为的早上 9 点"来写，但服务器系统时区可能是 UTC。定时任务必须按配置里显式声明的时区执行，不能全靠服务器系统时区约定俗成——否则"每天早上 9 点推 PR 评审"在服务器时区下会变成另一个时间触发。

**Why this priority**: 时区错位是"到点干活"最隐蔽的坑之一，配置里显式声明时区是坑四的解法。

**Independent Test**: 注册定时任务时，断言传给调度器的触发规则同时带上了配置里的 cron 表达式和时区，两者缺一不可。

**Acceptance Scenarios**:

1. **Given** 一个定时任务配置了声明的 cron 和时区，**When** 系统注册该任务，**Then** 注册参数同时包含 cron 表达式与时区（时区不是服务器默认值顶替）。

---

### Edge Cases

- Profile 没有任何 `schedules` 配置：不注册任何定时任务，启动不受影响。
- 配置里 cron 表达式非法：交由调度器按标准 cron 校验拒绝，AgentScheduler 不为它单独处理。
- 锁被占时的跳过行为：直接丢弃本次触发，不排队、不补跑。
- 任务失败后的锁释放：必须进 finally，防止"跳过"变成"永远跳过"。
- 同一 Profile 的历次触发：复用同一 Session，对话历史靠既有的最大历史轮数截断兜底，不为定时触发新设任何概念。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 定时配置（cron 表达式、时区、到点要说的消息）MUST 能在 Profile 里声明，MUST NOT 写死在 Java 代码里（配置即 Agent 原则的延伸）。
- **FR-002**: 启动时 MUST 扫描所有 Profile 的定时配置，逐条注册进调度器；注册 MUST 是动态的（触发规则来自配置），MUST NOT 用编译期写死的静态注解。
- **FR-003**: 到点触发时 MUST 拼出配置里的消息，交给统一入口处理——处理链路 MUST 与 CLI/Web 手动触发完全一致（ReAct 循环、工具执行、Provider 调用、审计全部复用，不新增独立逻辑）。
- **FR-004**: 每个定时任务 MUST 用一把按任务 id 区分的进程内本地锁防重叠执行；上一次没跑完时本次触发 MUST 直接跳过、不排队不堆积。MUST NOT 引入分布式锁（多实例协调放扩展阶段）。
- **FR-005**: 一次任务执行失败 MUST 只记日志、不外抛、不崩调度器、不影响其他任务下一次触发；失败这次调用 MUST 仍走完整审计路径（成败照常落审计）。
- **FR-006**: cron 触发 MUST 按配置里显式声明的时区执行，MUST NOT 默认用服务器系统时区顶替。
- **FR-007**: 定时触发的会话身份 MUST 固定为三元组（scheduler, scheduler, profileName），同一 Profile 的历次触发复用同一 Session；MUST NOT 为定时触发新设任何会话概念。
- **FR-008**: 锁 MUST 在任务执行结束（成功或失败）的 finally 路径被释放，防止任务永久卡死。

### Key Entities

- **定时任务（ScheduleConfig）**: 一条定时触发规则——任务 id、cron 表达式、时区、到点要说的消息。声明在 Profile 里。
- **调度器（AgentScheduler）**: 启动时扫描所有 Profile 的定时配置、逐条注册进调度器、到点触发的薄调度层。职责只到"拼一条消息交给统一入口"为止。
- **任务锁**: 按任务 id 的进程内本地锁，防止同一任务重叠执行。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 注册定时任务时，触发规则同时携带配置里的 cron 与时区，100% 用例通过（抓注册参数断言）。
- **SC-002**: 锁被占时本次触发直接跳过、统一入口不被调用，100% 用例通过。
- **SC-003**: 任务执行抛异常不外抛、锁在 finally 释放——"二进宫"断言（再次触发能进入统一入口、被调用两次）通过。
- **SC-004**: 会话三元组固定（scheduler, scheduler, profileName），两次触发拿到同一 Session，100% 用例通过。
- **SC-005**: 以上四类覆盖由同一个测试类承载，`mvn test` 全绿即通过；真实到点触发链路（等一次真实 cron 触发）留人工项。

## Assumptions

- `ScheduleConfig` 字段（id/cron/zone/message）与 Profile 的 `schedules` 字段已在第 19 节随 `Profile`/`ProfileLoader` 落地（本节交付物"配置：Profile 新增 schedules 字段"已由前序完成，本节只消费它）。
- 触发规则用 cron 表达式 + 时区（`java.time.ZoneId`）表达；"到点"的计算完全交给调度器的标准 cron 解析，AgentScheduler 不自己解析 cron。
- 定时任务执行期间，`ProfileContext` 的上下文管理与手动触发共用同一路径（`AgentService.process` 内部已处理 finally 清理），定时触发不新增上下文管理逻辑。
- 核心阶段单实例部署，本地锁只解决"同一进程内不重叠"；分布式协调（选主/分布式锁/租约）、失败自动重试、失败告警、任务状态持久化与运行控制均不在本节范围。
- 无新增第三方依赖：`ThreadPoolTaskScheduler` 与 `CronTrigger` 由 Spring 内置提供（已在既有依赖树中核实）。
