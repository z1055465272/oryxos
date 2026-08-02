# OryxOS Constitution

## Core Principles

### I. 自实现 ReAct Loop (NON-NEGOTIABLE)

`ReActLoop` MUST be implemented by OryxOS itself. The project MUST NOT use
Spring AI's agent abstractions, such as `ChatClient.prompt().call()` with
automatic tool execution. The core loop is a few dozen lines of Java, fully
owned by the project so future loop behavior can be customized.

**Rationale**: Full control of the agent mechanism is a core differentiator;
delegating it to a framework turns the loop into a black box.

### II. Spring AI 只用两件事 ⚠️

Spring AI MAY be used only for: (1) LLM Provider protocol conversion
(absorbing OpenAI / Anthropic / Gemini format differences), and (2) `@Tool`
annotation JSON Schema generation. Spring AI's automatic tool execution MUST
be disabled; tool dispatch and execution MUST be controlled entirely by
`ReActLoop` + `ToolExecutor`.

```java
// 错误：不得用 Spring AI 自动执行 tool
chatClient.prompt(prompt).tools(tools).call().content();

// 正确：只用 Spring AI 做 LLM 调用，tool 调用结果自己处理
ChatResponse response = chatModel.call(new Prompt(messages, options));
// 然后自己检查 response 里的 tool call，自己执行
```

**Rationale**: Violating this principle causes tools to be invoked twice
(the most common bug in this codebase). It is the easiest principle to get
wrong and MUST be checked in every review.

### III. Provider 必须显式映射

Multiple providers MUST NOT be distinguished by scanning Spring container
`ChatModel` beans (identical bean types are ambiguous). The project MUST
maintain an explicit `provider name -> ChatModel` mapping.

```java
// 正确：显式映射
Map<String, ChatModel> providerMap = Map.of(
    "deepseek", deepseekChatModel,
    "qwen",     qwenChatModel,
    "kimi",     kimiChatModel
);
```

### IV. 一个目录 = 一个 Agent；Skill 以本地软连接绑定并渐进披露

One agent = one directory `.oryxos/agents/<name>/` containing `AGENT.md`
(frontmatter = runtime profile via `AgentLoader.deriveProfile`, body = task
instructions) plus optional `skills/`, `scripts/`, and `REFERENCE.md`.
Public Skill entities live in `.oryxos/skills/<name>/`.

Agent-visible skills MUST be expressed only as **relative symlinks** under
`.oryxos/agents/<agent>/skills/<name>/` pointing at public entities; the
symlink set is the single source of truth for bindings, and `AGENT.md`
frontmatter MUST NOT declare a `skills:` list.

Loading follows three-tier progressive disclosure: each turn injects only the
bound skill's name + description + local absolute read path; on model hit the
`SKILL.md` body is read via `read_file`; reference/script attachments are
read or run on demand. Bodies MUST NOT be preloaded, no `use_skill` tool MAY
be added, and Skills MUST NOT enter `ToolRegistry`.

CRUD and startup recovery MUST detect dangling / escaped / invalid-target /
name-mismatch / stale-reference symlinks. Deleting a public Skill that is
referenced MUST be refused by default and the referencing Agent reported.

### V. 审计表 Day One 写入

`tool_invocations` and `llm_calls` audit tables MUST be written from the core
phase onward (no query API is required, but writes are mandatory). Logging
alone MUST NOT substitute for persistence.

**Rationale**: Auditability is OryxOS's core differentiator for regulated
enterprises; deferring it re-parses logs later and loses fidelity.

### VI. 沙箱白名单 + 真实路径校验（不使用 SecurityManager）

`SecurityManager` is deprecated since JDK 17 and unavailable in JDK 21 —
MUST NOT be used. Sandboxing MUST be implemented via `SandboxChecker`
path/pattern allowlists:

- 文件操作：路径白名单（`file.allowed_paths`）
- Shell：命令首 token 白名单（`shell.allowed_commands`）
- HTTP：域名通配符白名单（`http.allowed_domains`）

Existing file targets MUST be validated with `toRealPath()` to confirm the
real path stays under the allowlist root; new paths MUST validate the nearest
existing parent directory's real path. Agent Skill bindings MUST only allow
relative symlinks pointing into `.oryxos/skills/`; absolute and out-of-bounds
links MUST be rejected.

### VII. 同步执行模型

The core phase MUST be fully synchronous and blocking; concurrency is handled
by Java 21 Virtual Threads. Reactor / WebFlux / CompletableFuture async
programming models MUST NOT be introduced (SSE streaming is deferred to the
extension phase).

### VIII. Tool 模块三合一

Built-in Tools and the MCP Client MUST live in a single `oryxos-tool` module
(not split into multiple modules). Loading of `AGENT.md` and in-agent
sub-instructions belongs to `oryxos-core`'s `ContextLoader`.

## 技术栈约束

- **语言 / 运行时**: Java 21 (MUST) + Spring Boot 3.x 单体应用；构建用 Maven 多模块（9 个模块），单二进制部署
- **LLM 调用**: Spring AI Alibaba，仅协议转换 + `@Tool` schema 生成
- **HTTP 服务**: Spring MVC + Java 21 Virtual Thread
- **命令行**: Picocli；**YAML 解析**: SnakeYAML
- **持久化**: SQLite + Spring Data JPA；表结构变更不依赖 `hibernate.ddl-auto=update`，手动维护建表脚本或引入 Flyway
- **日志**: Logback + SLF4J（结构化 JSON）
- **敏感配置**: API key / MCP 凭证 MUST 通过环境变量注入，不得明文写在 Profile YAML；`ConfigLoader` 启动时校验必填项与格式，非法即清晰报错

## 开发工作流与质量门禁

- **五大核心能力优先，分阶段克制**: 核心阶段交付 Agent OS 运行时内核（对接 LLM、ReAct、Memory、Tool、Web Service）；企业级治理层与分布式基础设施在真实使用数据验证后再做
- **可演示交付**: 每个 user story 完成后 MUST 有可演示 Demo；优先级是跑通而非完美
- **防漂移**: 每个 user story 结束后 MUST 运行 `/speckit-analyze` 做 constitution + spec + plan + tasks + 代码一致性检查
- **Constitution 只读**: AI agent MUST NOT 自行修改 constitution；发现某条原则不对时停下来重新讨论
- **版本标记**: git commit 标记每个 user story 完成，便于回退到稳定状态
- **开发模式**: 主体开发用 Spec-Kit（大颗粒度 greenfield），增量开发用手动提示词 + Claude Code（小颗粒度增量）

## Governance

Constitution supersedes all other practices and MUST be honored by every
spec, plan, tasks, and implementation artifact.

- **修订流程**: 任何修订 MUST 记录变更原因；结构性修订 MUST 附迁移计划并获批后方可生效
- **版本策略**: MAJOR = 原则移除或重定义（向后不兼容）；MINOR = 新增原则或实质性扩展；PATCH = 澄清、措辞、非语义修正
- **合规审查**: 每个 user story 的 plan 与代码审查 MUST 验证 constitution 合规；复杂度 MUST 有正当理由
- **运行时指导**: 开发遵循 `CLAUDE.md`；需求见 `docs/DemandAnalysis.md`，技术方案见 `docs/TechnicalSolution.md`，AI 编程实施见 `docs/AiProgrammingGuilde.md`

**Version**: 1.1.0 | **Ratified**: 2026-08-02 | **Last Amended**: 2026-08-02
