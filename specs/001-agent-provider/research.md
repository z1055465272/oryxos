# Research: Agent Provider

## 1. Spring AI ChatModel API 形态

**Decision**: 使用 `ChatModel.call(Prompt)` 方法进行 LLM 调用, 不经过 `ChatClient`（后者自带 auto-execute 机制）。

**Rationale**: 根据课件三.3 节和宪法原则 II, `ChatClient.prompt().tools().call()` 会自动执行 tool, 导致 tool 被调两次。直接使用 `ChatModel` 的 `call(Prompt)` 方法, 传入 `ChatOptions` 控制 tool execution mode。

**API 核实** (Spring AI 1.0.9):
- `org.springframework.ai.chat.model.ChatModel` — 接口, 有 `call(Prompt)` 方法
- `org.springframework.ai.chat.model.ChatResponse` — 返回值, 含 `List<Generation>` 和 `ChatResponseMetadata`
- `org.springframework.ai.chat.prompt.Prompt` — 封装 `List<Message>` + `ChatOptions`
- `org.springframework.ai.model.tool.ToolCallingChatOptions` — ChatOptions impl，可通过 `setInternalToolExecutionEnabled(false)` 关闭自动执行
- `org.springframework.ai.openai.OpenAiChatModel` — OpenAI 协议实现, 也覆盖 DeepSeek/Kimi 等兼容 OpenAI 协议的 provider

**Alternatives considered**: 曾考虑用 `ChatClient` 但传入不包含 tool 的 prompt。但是 `ChatClient` 本身就是 Spring AI 的自动执行入口, 即使在 prompt 层不带 tool, 架构上也不干净。选择直接使用底层的 `ChatModel.call()` 避免所有自动执行路径。

## 2. Provider 显式映射实现方式

**Decision**: 不在 Spring Bean 层做 `@Qualifier` 注入, 而是通过 `application.yaml` 配置 + `@ConfigurationProperties` 加载 provider 列表, 在 `DefaultProviderService` 构造函数中逐条创建 `OpenAiChatModel` 并填入 `Map<String, ChatModel>`。

**Rationale**: 
- 课件明确要求"显式建表, 不靠类型扫描"
- 用 `@Qualifier` 仍需在 Bean 定义时指定 qualifier name, 配置分散
- `application.yaml` → `OryxOsProperties` → 循环创建 `ChatModel` 的路径允许运行时根据配置动态调整, 且配置与代码一目了然

**实现路径**:
```yaml
oryxos:
  providers:
    - name: deepseek
      base-url: https://api.deepseek.com/v1
      api-key: ${DEEPSEEK_API_KEY}
```
→ `OryxOsProperties.providers()` (List of ProviderConfig) → `DefaultProviderService` constructor 中 for each `providerConfig` 创建 `OpenAiChatModel.builder().baseUrl(...).apiKey(...).build()` → `.put(name, model)`

**Alternatives considered**: `@Qualifier` + `@Bean` — 可行但需要在每个 Bean 定义处写 qualifier, 不如全部收敛到 `application.yaml` 清晰。

## 3. Spring AI 自动 Tool 执行关闭方式

**Decision**: 使用 `ToolCallingChatOptions.setInternalToolExecutionEnabled(false)` 在每次调用时设置。

**Rationale**: 课件三.3 节骨架代码用 `model.call(request(..., false))` 示意。Spring AI 1.0.x 的实际 API 通过 `ToolCallingChatOptions` 控制:
```java
var options = ToolCallingChatOptions.builder()
    .internalToolExecutionEnabled(false)  // 关键: 关掉 Spring AI 自带的 tool loop
    .build();
var prompt = new Prompt(messages, options);
ChatResponse response = chatModel.call(prompt);
```
关闭后, 模型的 tool call 请求会在 `ChatResponse` 中返回, 但不会在 Spring AI 层被自动执行。OryxOS 的 ReActLoop + ToolExecutor 拿走并执行。

**Alternatives considered**: 不传 tool schema 到 LLM 请求 → 这样 LLM 根本无法请求 tool call, 但 tool calling 是 ReAct 的核心环节, 功能废了。必须传 schema 但关执行。

## 4. SnakeYAML Profile 解析

**Decision**: 使用 Spring Boot 内置的 SnakeYAML (via `org.yaml.snakeyaml.Yaml`) 直接解析 `.oryxos/profiles/*.yaml` 为 `Map`, 然后手工映射到 `Profile` record。

**Rationale**:
- `Profile` 字段固定且不多, 不需要 Jackson/snakeyaml 的自动反序列化
- 手工映射允许精确控制校验逻辑 (如 provider name 必须在全局配置中存在)
- 与 SnakyAML `Yaml` 类配合简单: `new Yaml().load(inputStream)` → `Map<String, Object>`

**${ENV_VAR} 占位符处理**: 解析 YAML 得到字符串值后, 用正则 `\$\{(.+?)\}` 提取环境变量名 → `System.getenv(varName)` → 替换。未设置的环境变量 → 校验错误, 不静默空值。

## 5. SQLite 手工建表脚本

**Decision**: 在 `oryxos-storage/src/main/resources/schema.sql` 中放 `CREATE TABLE IF NOT EXISTS llm_calls (...)` DDL。测试用 `@Sql` 注解在 `LlmCallRepositoryTest` 中执行。

**Rationale**: 课件四.4 节和宪法技术栈约束都明确"SQLite 用手工建表脚本, 不依赖 hibernate.ddl-auto=update"。`@DataJpaTest` 默认会用 `data.sql`/`schema.sql`, 配置 `spring.sql.init.mode=always` 确保执行。

**llm_calls 表结构** (与课件三.4 节和 CLAUDE.md §9.2 对齐):
```sql
CREATE TABLE IF NOT EXISTS llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER DEFAULT 0,
    completion_tokens INTEGER DEFAULT 0,
    total_tokens INTEGER DEFAULT 0,
    duration_ms BIGINT DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

关键列: `success` (失败调用不能没痕迹) 和 `error_message` (失败原因)。这是课件点名要求的两列。
