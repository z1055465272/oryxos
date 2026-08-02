# Phase 0 调研：第一周技术决策

**日期**: 2026-08-02

**输入**: `spec.md`、`plan.md`、`docs/TechnicalSolution.md` §13 / §3.2 / §4 / §6.7

---

## 决策 1：天气数据源选型（澄清 Q2 落地）

- **Decision**: Demo 一使用 **wttr.in**（无密钥公开天气 API，返回 JSON）作为主数据源，域名白名单配置 `wttr.in`；保留 `open-meteo.com` 作为备选。
- **Rationale**: 
  - 无密钥，避免引入第二套密钥管理（宪法：凭证最小化、走环境变量）——与"零配置演示"目标一致
  - wttr.in 对中文城市名支持好（`/北京?format=j1` 或 `/api/...`），Demo 一"查北京天气"最顺滑
  - JSON 输出结构稳定，`SandboxChecker` 的域名白名单校验有真实对比例子
- **Alternatives considered**:
  - 和风天气 / OpenWeatherMap：需 API key，引入第二套密钥管理，与本周零配置演示目标冲突，**否决**
  - open-meteo：无密钥、质量高，但接口参数略繁琐，作为备选保留
- **落地**: `application.yaml` 的 `http.allowed_domains` 加入 `wttr.in`；`http_get` 工具按需拼接 URL

## 决策 2：Provider 显式映射实现方式

- **Decision**: `ProviderService` 维护 `Map<String, ChatModel>`，key 为 Provider 配置里声明的 provider name（`deepseek`/`kimi`），value 为对应的 `ChatModel` Bean；通过 Spring `@Qualifier` 注入各 Bean 后手工组装映射表。
- **Rationale**: 宪法原则三（Provider 必须显式映射）+ 技术方案 §3.2。多 Provider 并存时 Bean 类型相同，靠类型扫描无法区分；显式 name 映射是唯一可靠路由。
- **Alternatives considered**:
  - 扫描 Spring 容器所有 `ChatModel`：Bean 类型相同无法区分 provider，**违反宪法，否决**
  - 纯配置表不依赖 Bean：失去 Spring AI 自动建 Bean 的能力，反而复杂，**否决**
- **落地**: `ProviderConfig` 读 `application.yaml` 的 `ai.providers` 列表，`ProviderService` 组装映射并暴露 `ChatModel get(String providerName)`；Profile 的 `provider.name` 作为路由 key

## 决策 3：Spring AI 使用边界（宪法原则二）

- **Decision**: 只用 Spring AI 的**协议转换**（`chatModel.call(new Prompt(...))`）与 **`@Tool` schema 生成**；不使用 `ChatClient` 的自动 tool 执行。`FunctionCallingAdapter` 把 `OryxTool` 转成 Spring AI 的工具 schema，LLM 返回的 tool call 由 `ReActLoop` 自己解析、`ToolExecutor` 自己执行。
- **Rationale**: 宪法原则二最容易被写错的一条。启用自动执行会导致 tool 被调两次。
- **Alternatives considered**: 无（宪法锁定，无替代）
- **落地**: 代码审查重点检查项；`ReActLoop` 内用 `ChatResponse.getToolCalls()` 手动取 tool call

## 决策 4：Sandbox 接口（宪法原则六）

- **Decision**: 本周在 `oryxos-tool` 实现 `Sandbox` 接口 + `WhitelistSandbox`（HTTP 域名白名单）。接口只表达"在受控环境里执行动作"意图，不携带"白名单/容器/microVM"等实现细节。
- **Rationale**: 技术方案 §6.7 决策六——接口先行，未来加重隔离只加实现类不改调用方。SecurityManager 在 JDK 21 不可用，必须应用层白名单。
- **Alternatives considered**: SecurityManager（JDK 21 不可用，**否决**）；容器/microVM（核心阶段过重，扩展阶段按信号驱动）
- **落地**: `SandboxAction{type, target}` + `Sandbox.enforce(SandboxAction)`；本周只实现 `HTTP_REQUEST` 的域名白名单校验，`FILE_READ/WRITE`、`SHELL_COMMAND` 留接口待第二周

## 决策 5：审计写入的受控延迟（宪法原则五）

- **Decision**: 本周 `ToolExecutor` 与 Provider 调用通过 core 定义的 `ToolInvocationRecorder` / `LlmCallRecorder` **接口**记录审计，本周用**内存实现**；第四周 `oryxos-storage` 落地 SQLite 时换 JPA 实现。
- **Rationale**: 技术方案 §13 把 SQLite 持久化排在第四周，本周无存储层。但宪法意图"可审计数据地基 day one 立起来"通过接口预留兑现，第四周不返工。
- **Alternatives considered**: 本周直接上 SQLite（打乱节奏，**否决**）；只记日志（违反宪法意图，**否决**）
- **落地**: 见 `contracts/` 中 `audit.md` 与 plan 的 Complexity Tracking

## 决策 6：`oryxos init` 工作区初始化

- **Decision**: 实现 `InitCommand`，`oryxos init` 创建 `.oryxos/` 工作区：`agents/`、`memory/`、`sessions/`、`logs/`、`mcp_servers.yaml`、`AGENTS.md`、`SOUL.md`、`USER.md`、`oryxos.db`（空占位）。最小 AGENT.md 示例写入 `agents/`。
- **Rationale**: 技术方案 §8.1。本周 Agent 用最小 AGENT.md（frontmatter 派生 Profile，用户已澄清 Q1）。
- **Alternatives considered**: 延迟 init 到后续周次（Demo 一需要 `oryxos chat` 前有 Profile 可路由，**否决**）
- **落地**: 不需要 Spring 上下文的命令直接文件操作，启动快

## 决策 7：Session 内存版

- **Decision**: 本周 `SessionManager` 纯内存实现，按 `channel+user+profile` 联合键管理；消息序列存内存 `List<Message>`。持久化第四周上 SQLite。
- **Rationale**: 技术方案 §13 第一周"Session 内存版（第四周加 SQLite）"。
- **Alternatives considered**: 本周上 SQLite（打乱节奏，**否决**）
- **落地**: `Session`/`Message` 数据结构按 SQLite 最终形态设计（`session_id`、`profile_name`、`channel`、`user_id`、`messages_json` 语义对齐），第四周迁移零返工

---

## 未决项

- **并发指标**: 本周 CLI 单会话场景，无并发要求；虚拟线程模型由 Spring Boot 3.5 默认开启（server 场景），第四周 Web 场景再验证
- **`max_history_turns`**: 默认 20（技术方案 §4.3 明确），本周采用默认值，不另议
