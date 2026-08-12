# Feature Specification: Sandbox 安全校验模块 — 给 Agent 的行动上把锁

**Feature Branch**: `024-lesson24-sandbox`

**Created**: 2026-08-12

**Status**: Draft

**Input**: User description: "第24节需求：Sandbox 安全校验模块——把第23节评审定下的'接口先行'白名单沙箱落成代码，接进已有的 read_file/write_file/list_dir/shell/http_get/http_post/notify 工具。"

## Clarifications

### Session 2026-08-12

- Q: application.yaml 里 file.allowed_paths / shell.allowed_commands / http.allowed_domains 三个配置键的默认值应该怎么定？（本节交付物含这三个键，但默认值课件未指定） → A: 宽松默认，不打断现有流程——application.yaml 里给三块白名单配宽松默认值（file 含工作区/当前目录、shell 含常用命令、http 含 deepseek.com 等现有依赖域名），沙箱生效但默认不打断 chat/demo/notify 流程，用户按需收紧。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 危险命令被拦下，Agent 收到可读的失败原因（shell）(Priority: P1)

运维 Agent 被 prompt injection 诱导执行一条不在白名单内的命令（比如 `rm -rf /`）。shell 能力在执行前先过命令首 token 白名单，`rm` 不在允许列表里，动作被当场拦下、真实子进程根本没有启动。失败原因是一句人能读懂的说明（"命令不在白名单内: rm"），并且按普通工具失败记录在案；Agent 在下一轮能看到这条失败原因，知道这条路走不通、换一条走。

**Why this priority**: "拦截危险动作 + 失败可读 + 留审计"是 Sandbox 能力的核心闭环，也是"防模型犯傻"直接对应的场景，最先交付。

**Independent Test**: 配一个只允许 `ls` 的命令白名单，直接对 shell 能力发起白名单外的命令，断言动作被拦、抛出的异常携带可读原因；再走完整链路跑一次，断言审计记录里该次调用的成败标记为失败、错误信息与该原因一致。

**Acceptance Scenarios**:

1. **Given** 命令白名单只含 `ls`，**When** 发起 `rm -rf /`，**Then** 动作被拦下，异常信息为可读的"命令不在白名单内: rm"，真实命令未执行。
2. **Given** 同上场景，**When** 该次拦截走完整工具执行链路，**Then** 审计记录写入该次调用：成败标记为失败、错误信息与拦截原因一致。

---

### User Story 2 - 越界文件读写与形似域名访问被拦下（file / http）(Priority: P1)

模型试图读取白名单目录之外的文件（如 `/etc/passwd`），或试图访问一个形似白名单域名的外部地址（如 `evil-example.com`，白名单是 `*.example.com`）。文件路径经过标准化后按白名单根比对，越界的被拦、真实 IO 不发生；域名匹配带点号边界，形似域名（`evil-example.com`）不会被 `*.example.com` 误命中，请求根本不发出。

**Why this priority**: 文件与网络是 Agent 动作面最大的两类 IO，和白名单命令拦截并列，构成 Sandbox 第一档的核心覆盖。

**Independent Test**: 配好路径白名单与域名白名单后，分别发起越界路径读取与形似域名请求，断言均被拦、底层 IO 未发生（可证明动作没有真实执行）。

**Acceptance Scenarios**:

1. **Given** 路径白名单含工作区目录，**When** 尝试读取 `/etc/passwd`，**Then** 动作被拦、真实读操作未发生。
2. **Given** 域名白名单含 `*.example.com`，**When** 尝试访问 `https://evil-example.com/`，**Then** 动作被拦、请求未发出。
3. **Given** 域名白名单含 `*.example.com`，**When** 尝试访问 `https://api.example.com/`，**Then** 动作放行（点号边界不误伤真实子域）。

---

### User Story 3 - 相对路径穿越被拦下（路径标准化回归）(Priority: P2)

模型传入一段含 `..` 的相对路径，试图爬出白名单目录（如 `/workspace/../../outside/secret.txt`）。校验先把路径标准化（去掉 `..` 归一化、转绝对路径）再按白名单根比对，穿越序列被展开后落在白名单之外，动作被拦。应用层白名单防的是犯傻不是攻击，但最常见的路径穿越手法至少在这里被堵上。

**Why this priority**: 路径穿越是应用层路径校验最容易漏的绕过点，属于安全回归的关键一条。

**Independent Test**: 白名单只含一个工作目录，构造 `..` 序列爬向其外的路径，断言被拦。

**Acceptance Scenarios**:

1. **Given** 路径白名单只含工作目录，**When** 发起 `/workspace/../../outside/secret.txt`，**Then** 动作被拦（路径标准化后落在白名单外）。

---

### Edge Cases

- 白名单配置项为空：语义是"什么都不允许"而不是"不校验"——任何动作都被拦下，而不是放行。
- 命令首 token 带前导空格、大小写变体时，校验不误拦也不误放。
- 通配符域名带 `*.` 前缀时必须带点号边界匹配：`evil-example.com` 不得命中 `example.com`，`api.example.com` 必须命中。
- 接口换用未来更重的实现（容器/microVM）时，调用方代码一行不用改——接口签名不出现任何一档实现特有的词。
- 三个校验方法是实现内部的私有细节，不暴露在接口契约上。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供统一的沙箱校验入口，只表达"在受控环境里执行一个动作"这一种意图；入口签名由动作类型（四类：文件读/文件写/Shell 命令/HTTP 请求）与动作目标组成，不出现白名单、容器、microVM 等任何一档实现特有的概念。
- **FR-002**: 文件读与文件写 MUST 是两种不同的动作类型（未来按读/写分权限预留）。
- **FR-003**: 校验不通过时 MUST 抛出可读的校验失败异常（携带失败原因，如"命令不在白名单内: rm"），由既有工具执行失败路径承接，按普通工具失败写入审计（成败标记失败、错误信息为该原因），MUST NOT 为此新增独立审计逻辑。
- **FR-004**: 核心阶段 MUST 提供且只提供一档实现——白名单校验，读三块配置：允许的文件路径根、允许的 shell 命令、允许的 HTTP 域名。
- **FR-005**: 文件路径校验 MUST 先标准化（归一化 `..`、转绝对路径）再按白名单根比对，穿越序列落在白名单外的 MUST 被拦。
- **FR-006**: shell 命令校验 MUST 拆出命令首个 token 与命令白名单比对。
- **FR-007**: HTTP 域名校验 MUST 解析出 host 再做匹配；`*.` 前缀通配 MUST 带点号边界，形似域名不得命中。
- **FR-008**: 已交付的四个工具（文件/Shell/HTTP/通知）MUST 在各自执行动作的第一步先过沙箱校验，校验通过才继续，校验不通过直接终止、真实 IO 不发生。
- **FR-009**: 白名单配置项为空 MUST 语义为"什么都不允许"，而不是"不校验"；默认配置 MUST 提供宽松白名单（覆盖现有 chat/demo/notify 依赖的路径、命令、域名），保证沙箱生效但不默认打断现有流程。
- **FR-010**: 三个具体校验方法 MUST 是实现内部的私有细节，不暴露在接口契约上（接口不被第一档实现带偏）。

### Key Entities

- **沙箱校验动作（SandboxAction）**: 一次需要过校验的动作，含动作类型与动作目标（路径/命令/URL）。
- **动作类型（ActionType）**: 文件读、文件写、Shell 命令、HTTP 请求四类。
- **白名单沙箱（WhitelistSandbox）**: 核心阶段唯一的沙箱实现，按动作类型路由到三类私有校验。
- **校验失败异常（SandboxViolationException）**: 校验不通过时抛出的可读异常，携带失败原因。
- **沙箱校验接口（Sandbox）**: 唯一的对外入口，抽象"在受控环境里执行一个动作"这个意图，不表达任何一档实现。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 白名单内动作放行、白名单外动作拦截——三类校验（文件/命令/域名）"允许 + 拒绝"各成对通过，100% 用例通过。
- **SC-002**: 路径穿越（`..` 爬出白名单目录）被标准化后拦截，100% 用例通过。
- **SC-003**: 通配符域名 `*.example.com` 命中 `api.example.com`、不命中 `evil-example.com`，100% 用例通过。
- **SC-004**: 四个已交付工具对白名单外的输入全部拦截，且真实 IO 未发生（证明危险动作没跑），100% 用例通过。
- **SC-005**: 校验失败按普通工具失败写入审计（成败标记失败、错误信息可读），复用既有失败路径，不新增独立审计逻辑。
- **SC-006**: 换用未来更重实现（容器/microVM）时，接口签名与调用方代码无需修改（接口中立性自查通过）。

## Assumptions

- 动作类型采用四值设计（文件读/文件写/Shell 命令/HTTP 请求），与既有代码及既有测试一致；课件正文的三值写法视为示意图，以技术方案与既有契约为准。
- 核心阶段只交付白名单校验这一档实现；容器隔离与 microVM 留扩展阶段，接口已为它们预留位置，但本节不写任何对应代码。
- 白名单配置项为空语义为"什么都不允许"（配置边界语义写进配置说明）；默认配置给宽松白名单，覆盖现有 chat/demo/notify 依赖的路径、命令与域名，用户按需收紧（2026-08-12 澄清）。
- 沙箱拒绝复用既有工具执行失败路径（审计写 `success=false`、`error_message`），工具本身的 IO 代码不改。
- 无新增第三方依赖（校验逻辑全部由 JDK/Spring Boot 内置能力承担）。
