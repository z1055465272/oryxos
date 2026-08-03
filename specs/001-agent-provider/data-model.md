# Data Model: Agent Provider

## Entity: Profile (Record)

**Location**: `oryxos-core/src/main/java/com/oryxos/core/Profile.java`

**Purpose**: 承载一个 Agent 的全部运行配置, 从 YAML 文件解析而来。后续各节按需取用对应字段。

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | Profile 唯一标识名 |
| `description` | String | Profile 描述 |
| `identity` | Identity (nested record) | 身份设定 (agentName, prompt) |
| `provider` | ProviderRef (nested record) | Provider 选择 (name, model, temperature) |
| `tools` | List\<String\> | 工具列表 |
| `skills` | List\<String\> | Skill 列表 |
| `mcpServers` | List\<String\> | MCP Server 列表 |
| `channels` | List\<ChannelRef\> | 接入渠道列表 |
| `notifyChannels` | List\<String\> | 通知渠道列表 |
| `schedules` | List\<ScheduleConfig\> | 定时调度列表 |
| `bootstrap` | List\<String\> | 启动引导文件列表 |
| `settings` | Settings (nested record) | 运行时设置 (maxIterations, maxHistoryTurns) |

**Validation Rules**:
- `name` MUST 非空
- `provider.name` MUST 能在全局 `oryxos.providers` 列表中找到
- `provider.apiKey` 占位符 `${ENV_VAR}` MUST 对应真实环境变量
- 可选字段缺失时使用合理默认值 (空列表/空 Map/默认数值)

**Lifecycle**: 启动时由 `ProfileLoader` 一次性扫描加载 → 注册到 `ProfileRegistry` → 运行时只读查询。29 节补 `register()` 运行时注册方法。

## Nested Records

### Identity
| 字段 | 类型 | 说明 |
|------|------|------|
| `agentName` | String | Agent 显示名 |
| `prompt` | String | 系统提示词正文 |

### ProviderRef
| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | Provider 名, 对应全局配置中的 name |
| `model` | String | 模型名, 如 "deepseek-chat" |
| `temperature` | Double | 温度参数, 默认 0.7 |

### Settings
| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `maxIterations` | Integer | 10 | ReAct 最大迭代次数 |
| `maxHistoryTurns` | Integer | 20 | 对话历史最大保留轮数 |

---

## Entity: LlmCall (JPA)

**Location**: `oryxos-storage/src/main/java/com/oryxos/storage/LlmCall.java`

**Purpose**: 每次 LLM 调用的审计记录。宪法 V: day one 写入, 不依赖日志。

**Table**: `llm_calls`

| 列 | 类型 | 约束 | 说明 |
|---|------|------|------|
| `id` | BIGINT | PK, AUTOINCREMENT | 主键 |
| `session_id` | VARCHAR(255) | NOT NULL | 关联 Session |
| `provider` | VARCHAR(100) | NOT NULL | Provider 名称 (如 "deepseek") |
| `model` | VARCHAR(100) | NOT NULL | 模型名 (如 "deepseek-chat") |
| `prompt_tokens` | INTEGER | DEFAULT 0 | 输入 token 数 |
| `completion_tokens` | INTEGER | DEFAULT 0 | 输出 token 数 |
| `total_tokens` | INTEGER | DEFAULT 0 | 总 token 数 |
| `duration_ms` | BIGINT | DEFAULT 0 | 调用耗时 (ms) |
| `success` | BOOLEAN | NOT NULL, DEFAULT TRUE | 调用是否成功 |
| `error_message` | TEXT | NULLABLE | 失败原因 (成功时为 NULL) |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 记录时间 |

**JPA Mapping**:
```java
@Entity
@Table(name = "llm_calls")
public class LlmCall {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false) private String sessionId;
    @Column(nullable = false) private String provider;
    @Column(nullable = false) private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long durationMs;
    private Boolean success;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "created_at") private LocalDateTime createdAt;
}
```

**DDL**: 手工维护在 `oryxos-storage/src/main/resources/schema.sql`, 包含完整的 `CREATE TABLE IF NOT EXISTS` 语句。见 research.md §5。

---

## Configuration Entity: ProviderConfig

**Location**: `oryxos-provider` 内部使用, 不对外暴露

**Purpose**: `application.yaml` 中 `oryxos.providers` 列表的每一条映射对象。

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | Provider 唯一标识, Profile 层按此引用 |
| `baseUrl` | String | API base URL (可选, 有默认值) |
| `apiKey` | String | API Key, 启动时从环境变量解析 `${ENV_VAR}` |

---

## Relationships

```
Profile.provider.name  ──references──>  ProviderConfig.name
                                            │
                                     Map<String, ChatModel>  (DefaultProviderService 内部)
                                            │
LlmCall.session_id ──关联──> Session (17 节创建)
```
