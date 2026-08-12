---

description: "Sandbox 安全校验模块（第 24 节）实现任务清单"
---

# Tasks: Sandbox 安全校验模块（第 24 节）

**Input**: Design documents from `/specs/007-sandbox-module/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 课件"验收 harness"要求 `WhitelistSandboxTest`（三类校验"允许+拒绝"成对 + 穿越 + 通配符边界 + 空配置）+ 四个 Tool 的拦截接线回归（真实 IO 未发生）。harness 先行——实现前先写测试并跑红。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## 关键设计约束（plan.md 声明）

- `ActionType` **保留既有四值**（`FILE_READ`/`FILE_WRITE`/`SHELL`/`HTTP_REQUEST`），不改课件三值——与 TechnicalSolution §6.7 及既有代码/测试一致（软门禁 2/4 记录）。
- 全部新代码落 `com.oryxos.tool.sandbox`（`oryxos-tool` 模块），不往 `oryxos-core` 塞概念。
- 三个 `@ConfigurationProperties` record 用 `@EnableConfigurationProperties` 在 `CliSpringBootstrap` 追加注册（不新增自动配置类）。
- `Sandbox` 接口/`SandboxAction`/`ActionType` 是既有占位（20/23 节已建），只读契约不改；`NoOpSandbox` 占位实现被 `WhitelistSandbox` 替换（删除）。
- 四个 Tool 的 `enforce` 接线已就位（每个 `@Tool` 方法首行），零新增接线代码；接线回归测试用**真实受限 WhitelistSandbox** 证明拦截 + IO 未发生。
- 默认白名单配置取宽松值（2026-08-12 澄清），不打断现有 chat/demo/notify 流程。
- 测试方法名必须英文（驼峰），课件 harness 中文方法名进 `@DisplayName`。
- 不改 `ToolExecutor`（课件明确"不改审计逻辑"，Sandbox 拒绝走既有失败路径）。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 确认 oryxos-tool 无需新增依赖，sandbox 包与四 Tool 接线处于干净可改状态

- [x] T001 [P] 核实 `oryxos-tool/pom.xml` 已含 `spring-boot-starter-test`（test scope）且无新增第三方依赖需求（`@ConfigurationProperties`/`Path`/`URI` 均为 Spring Boot / JDK 内置）；确认 `com.oryxos.tool.sandbox` 既有四个占位文件（`Sandbox`/`SandboxAction`/`ActionType`/`NoOpSandbox`）与四 Tool 构造器注入 + `enforce` 调用在位

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: `SandboxViolationException` + 三个配置绑定 + `WhitelistSandbox` 实现——所有 user story 的地基

**⚠️ CRITICAL**: 本节未完成前任何 user story 都不能开工

- [x] T002 [P] 创建 `SandboxViolationException` 在 `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/SandboxViolationException.java`：`extends RuntimeException`，构造器收 `String message`（message 为可读原因，如 "命令不在白名单内: rm"）
- [x] T003 [P] 创建三个 `@ConfigurationProperties` record 在 `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/`：
  - `FileSandboxProperties.java`（`@ConfigurationProperties(prefix = "file")`，`record FileSandboxProperties(List<String> allowedPaths)`）
  - `ShellSandboxProperties.java`（`@ConfigurationProperties(prefix = "shell")`，`record ShellSandboxProperties(List<String> allowedCommands)`）
  - `HttpSandboxProperties.java`（`@ConfigurationProperties(prefix = "http")`，`record HttpSandboxProperties(List<String> allowedDomains)`）
- [x] T004 [P] [US1] 编写 `WhitelistSandboxTest` 在 `oryxos-tool/src/test/java/com/oryxos/tool/sandbox/WhitelistSandboxTest.java`（先跑红；直接 `new WhitelistSandbox(fileProps, shellProps, httpProps)`）。方法名英文 + `@DisplayName` 保留课件原文：
  - file 组：`allowlistedPathIsAllowed`（白名单内放行）/ `pathOutsideAllowlistIsRejected`（白名单外拒绝）/ `traversalOutsideAllowlistIsBlocked`（课件：相对路径穿越必须被拦——`/workspace/../../outside/secret.txt`）
  - realpath 组（宪法 VI 加固）：`symlinkEscapingAllowlistIsBlocked`（白名单目录内一个符号链接指向白名单外真实文件，读它被拦——`toRealPath()` 解析后落白名单外）
  - shell 组：`allowlistedCommandIsAllowed`（白名单内放行）/ `commandOutsideAllowlistIsRejected`（白名单外拒绝）/ `firstTokenTrimmedAndCaseHandled`（首 token 带前导空格、大小写变体的处理）
  - http 组：`exactDomainMatches`（精确匹配放行）/ `domainOutsideAllowlistIsRejected`（白名单外拒绝）/ `wildcardDomainHitsSubdomain_notSpoofed`（课件：通配符域名_不能被形似域名绕过——`api.example.com` 放行、`evil-example.com` 被拦，endsWith 必须带点号边界）
  - empty 组：`emptyAllowlistDeniesEverything`（配置为空 = 全拒绝，不是不校验）
- [x] T005 [US1] 实现 `WhitelistSandbox` 在 `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/WhitelistSandbox.java`：`@Component implements Sandbox`；构造器收三 record，预解析 `allowedRoots`（`Path::of` + `normalize`）/ `allowedCommands`（`Set.copyOf`）/ `allowedDomainPatterns`（`List.copyOf`）；`enforce` 按 `action.type()` 路由——`FILE_READ`/`FILE_WRITE`→`checkFilePath`、`SHELL`→`checkShellCommand`、`HTTP_REQUEST`→`checkHttpUrl`；`checkFilePath` 用 `Path.of(raw).normalize().toAbsolutePath()` + `startsWith`（防穿越），并**叠 `toRealPath()` 加固（宪法 VI）**——目标存在时 `toRealPath()` 解析真实路径再 `startsWith`，目标不存在时沿父目录链向上找最近存在祖先 `toRealPath()` 后再 `startsWith`（防符号链接逃逸）；`checkShellCommand` 用 `trim().split("\\s+")[0]` 首 token 比对；`checkHttpUrl` 用 `URI.create(url).getHost()` + `matchesDomain`（`*.` 前缀 `endsWith(pattern.substring(1))` 带点号边界，否则 `equals`）；失败抛 `SandboxViolationException`（三个校验方法均 `private`，不暴露在接口契约上）
- [x] T006 [P] 删除占位：`oryxos-tool/src/main/java/com/oryxos/tool/sandbox/NoOpSandbox.java` 与 `oryxos-tool/src/test/java/com/oryxos/tool/sandbox/SandboxPlaceholderTest.java`（占位实现被 `WhitelistSandbox` 替换，测试随占位退役）

**Checkpoint**: Foundation ready——`WhitelistSandboxTest` 全绿（三类校验"允许+拒绝"成对 + 穿越 + 通配符边界 + 空配置），user story 可开工

---

## Phase 3: User Story 1 - 危险命令被拦下（shell）(Priority: P1) 🎯 MVP

**Goal**: 真实 `WhitelistSandbox` 拦下白名单外命令，抛可读 `SandboxViolationException`，真实子进程不启动

**Independent Test**: `WhitelistSandboxTest` shell 组 + `WhitelistSandboxWiringTest.shellBlocked`——`shell("rm -rf /")` 抛异常而非返回 `ToolResult`

### Implementation for User Story 1

- [x] T007 [P] [US1] 在 `oryxos-boot/src/main/resources/application.yaml` 增加 `shell.allowed_commands` 配置（宽松默认，覆盖常用命令如 `ls`/`cat`/`echo`/`git`/`java`/`curl` 等，2026-08-12 澄清；配置为空 = 什么都不允许的边界语义在注释写明）
- [x] T008 [US1] 在 `oryxos-tool/src/test/java/com/oryxos/tool/sandbox/WhitelistSandboxWiringTest.java` 写 ShellTools 接线回归 `shellBlockedByWhitelist_doesNotStartProcess`：构造 `ShellTools(真受限 WhitelistSandbox)`（白名单只含 `ls`），调 `shell("rm -rf /")` → `assertThrows(SandboxViolationException.class, ...)`，断言消息含 "命令不在白名单内: rm"——真实子进程未启动（若启动会返回 `ToolResult` 而非抛异常）

**Checkpoint**: US1 完成——真实沙箱拦下白名单外命令，可读失败原因

---

## Phase 4: User Story 2 - 越界文件读写与形似域名访问被拦下（file / http / notify）(Priority: P1)

**Goal**: 真实 `WhitelistSandbox` 拦下白名单外文件路径与形似域名请求，底层 IO/请求不发出

**Independent Test**: `WhitelistSandboxTest` file/http 组 + `WhitelistSandboxWiringTest` 的 file/http/notify 用例——越界路径读被拦、形似域名请求被拦且 `MockWebServer` 无请求记录、notify 适配器未被调用

### Implementation for User Story 2

- [x] T009 [P] [US2] 在 `oryxos-boot/src/main/resources/application.yaml` 增加 `file.allowed_paths`（含 `.oryxos` 与当前工作目录）与 `http.allowed_domains`（含 `deepseek.com` 等现有依赖域名）配置（宽松默认；边界语义注释）
- [x] T010 [US2] 在 `WhitelistSandboxWiringTest.java` 写 FileTools 接线回归 `fileReadBlockedByWhitelist_doesNotRead`：构造 `FileTools(真受限 WhitelistSandbox)`（白名单只含临时目录），调 `readFile("/etc/passwd")` → `assertThrows(SandboxViolationException.class, ...)`——真实 IO 未发生（若读会返回成功 `ToolResult`）
- [x] T011 [P] [US2] 在 `WhitelistSandboxWiringTest.java` 写 HttpTools 接线回归 `httpGetBlockedByWhitelist_requestNotSent`：`MockWebServer` 起服，构造 `HttpTools(真受限 WhitelistSandbox, RestClient.create())`（域名白名单 `*.example.com`），调 `httpGet("https://evil.example.com/")` → `assertThrows(SandboxViolationException.class, ...)`；断言 `mockWebServer` 未收到任何请求（证明请求未发出）
- [x] T012 [US2] 在 `WhitelistSandboxWiringTest.java` 写 NotifyTools 接线回归 `notifyBlockedByWhitelist_adapterNotCalled`：`ProfileContext.set(含 webhook 渠道 Profile)`，mock `NotifyChannelAdapterRouter`，构造 `NotifyTools(真受限 WhitelistSandbox, router, profileContext)`，调 `notify("hi", null)` → `assertThrows(SandboxViolationException.class, ...)`；`verifyNoInteractions(router)`（发送未发生）

**Checkpoint**: US1 + US2 完成——真实沙箱拦下越界文件/形似域名/非法通知目标

---

## Phase 5: User Story 3 - 相对路径穿越与形似域名绕过经工具链路被拦 (Priority: P2)

**Goal**: 穿越与通配符边界不止在 `WhitelistSandbox` 单测里成立，经真实工具链路同样被拦

**Independent Test**: `WhitelistSandboxWiringTest` 的穿越与通配符用例——`..` 序列爬出白名单目录被拦、`evil-example.com` 被拦而 `api.example.com` 放行

### Implementation for User Story 3

- [x] T013 [US3] 在 `WhitelistSandboxWiringTest.java` 写 FileTools 穿越接线回归 `fileTraversalBlockedByWhitelist_doesNotRead`：构造 `FileTools(白名单只含 tempDir 的受限 sandbox)`，调 `readFile(tempDir + "/../../outside/secret.txt")` → `assertThrows(SandboxViolationException.class, ...)`（normalize 后落白名单外，真实 IO 未发生）
- [x] T014 [US3] 在 `WhitelistSandboxWiringTest.java` 写 HttpTools 通配符接线回归 `httpWildcard_allowsSubdomain_blocksSpoofedDomain`：域名白名单 `*.example.com`，`httpGet("https://api.example.com/x")` 放行（`MockWebServer` 收到请求、返回 `ToolResult.ok`）；`httpGet("https://evil-example.com/x")` 被拦（`SandboxViolationException`）

**Checkpoint**: US1 + US2 + US3 完成——三类校验 + 穿越 + 通配符边界在真实沙箱与工具链路上全部成立

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量门禁、H4 不变量、交付物核对

- [ ] T015 [P] 跑 `mvn clean verify` 全量门禁（含 P3C/SpotBugs/FindSecBugs/PMD），全绿；确认前序节（16-22）测试回归全绿（尤其 `ToolExecutorTest` 失败路径审计、四 Tool 既有测试、`OryxToolContractTest`）
- [ ] T016 [P] H4 六条不变量自查：①涉外 IO 首行过 `Sandbox.enforce`（本节 `WhitelistSandbox` 已接上四 Tool 首行）②LLM 调用落 `llm_calls`、工具执行成败落 `tool_invocations`（Sandbox 拒绝走 `ToolExecutor` 既有失败路径）③grep 无明文 key ④`session_id` 只在 `SessionManager` 内拼接（本节不触碰）⑤无 Reactor/`CompletableFuture`/自建线程池（`WhitelistSandbox` 同步阻塞）⑥无 Spring AI 自动工具执行路径
- [ ] T017 [P] 交付物存在性核对：`SandboxViolationException`/`WhitelistSandbox`/三个 record 存在且非空；`WhitelistSandboxTest` + `WhitelistSandboxWiringTest` 非空；`application.yaml` 有 `file.allowed_paths`/`shell.allowed_commands`/`http.allowed_domains` 三键；四 Tool 首行 `enforce` 在位；`NoOpSandbox`/`SandboxPlaceholderTest` 已删除

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup——BLOCKS 所有 user story
- **User Stories (Phase 3+)**: 依赖 Foundational；US1 → US2 → US3 顺序（US2/US3 复用 US1 的 `WhitelistSandboxWiringTest` 类与受限 sandbox 构造）
- **Polish (Phase 6)**: 依赖所有 user story 完成

### User Story Dependencies

- **US1 (P1)**: 依赖 Foundational（`WhitelistSandbox` 实现）——无其他故事依赖
- **US2 (P2)**: 依赖 US1（`WhitelistSandboxWiringTest` 类扩展）
- **US3 (P2)**: 依赖 US1 + US2（复用同一测试类 + 配置）

### 并行机会

- T002/T003（异常 + 三 record）可并行
- T004（`WhitelistSandboxTest`）与 T002/T003 无文件冲突
- T007/T008 与 T009/T010/T011/T012 无文件冲突（US1/US2 不同阶段，但配置写入同文件 `application.yaml` 需顺序）
- 各 user story 的 [P] 测试任务可与其实现并行起步

### 关键验证命令

```bash
# 只跑 oryxos-tool 模块测试（含 sandbox 与内置 Tool）
mvn -pl oryxos-tool -am test
# 只跑 WhitelistSandbox 相关测试
mvn -pl oryxos-tool -Dtest=WhitelistSandboxTest,WhitelistSandboxWiringTest test
# 全量门禁
mvn clean verify
```

---

## 实现策略

### MVP First (US1 先行)

1. 完成 Phase 1（Setup）——无新增依赖核实
2. 完成 Phase 2（Foundational）——`WhitelistSandbox` 三类校验测试全绿
3. 完成 Phase 3（US1）——真实沙箱拦下白名单外命令
4. **STOP 验证**：US1 独立测试通过

### 增量交付

1. US1（shell 拦截）→ 测试绿 → 真实沙箱可拦命令
2. US2（file/http/notify 拦截）→ 测试绿 → 三类 IO 全拦
3. US3（穿越 + 通配符经工具链路）→ 测试绿 → 完整闭环

## Notes

- [P] 任务 = 不同文件、无依赖
- 每个实现任务前，对应测试先红后绿（harness 先行）
- 课件"验收 harness"写出代码的两个测试（T004 里的穿越 + 通配符形似域名）断言逐条保真，是本节最重要保险
- `ToolExecutor` 不修改（课件明确"不改审计逻辑"）
- 不 commit / push，同步时机由用户决定
