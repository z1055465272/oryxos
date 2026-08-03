# Contracts: CLI 命令行入口 + 会话持久化地基

**Feature**: CLI 命令行入口 + 会话持久化地基 | **Date**: 2026-08-03

## 公共接口契约

### 1. SessionManager（oryxos-core，第 17 节 `save` 扩展为三方法）

```java
package com.oryxos.core;

import java.util.Optional;

/**
 * 会话管理契约：按三元组幂等获取/创建、按标识查询、持久化。
 * session_id 的拼接（channel + user + profile 联合唯一）只发生在实现内部这一处，
 * 所有入口（CLI 传 "cli"、Web 传 "web"、定时传 "scheduler"）只提供三元组、不自己拼字符串。
 * 第 18 节在 oryxos-storage 提供 JPA 实现 JpaSessionManager。
 */
public interface SessionManager {

  /**
   * 按三元组幂等获取或创建会话。
   * 同一三元组返回同一个 Session（多轮对话靠它串起来）；任一元素不同则不同会话。
   */
  Session getOrCreate(String channel, String user, String profileName);

  /** 按会话标识查询. */
  Optional<Session> get(String sessionId);

  /** 持久化 Session（累积完的历史）. */
  void save(Session session);
}
```

### 2. JpaSessionManager（oryxos-storage，实现 core.SessionManager）

```java
package com.oryxos.storage;

import com.oryxos.core.Session;
import com.oryxos.core.SessionManager;
import java.util.Optional;

/** SessionManager 的 JPA 实现（依赖倒置：契约在 core、实现在 storage，同 §8.5 ScheduledTaskStore 模式）. */
public class JpaSessionManager implements SessionManager {

  public JpaSessionManager(SessionRepository repository);

  @Override
  public Session getOrCreate(String channel, String user, String profileName);

  @Override
  public Optional<Session> get(String sessionId);

  @Override
  public void save(Session session);
}
```

### 3. SessionRepository（oryxos-storage，JPA Repository）

```java
package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/** sessions 表数据访问. */
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

  /** 按三元组幂等查询：session_id 由 SessionManager 拼接，此处按精确 key 查. */
  // findBySessionId(String sessionId) —— 继承自 JpaRepository.findById，无需额外声明
}
```

### 4. SessionEntity（oryxos-storage，JPA 实体）

```java
package com.oryxos.storage;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** sessions 表 JPA 实体（字段照 TechnicalSolution §9.2）.
 *  对话历史整体 JSON 序列化存 messages_json 一列；编解码用静态方法 encodeMessages/decodeMessages. */
@Entity
@Table(name = "sessions")
public class SessionEntity {
  @Id private String sessionId;
  private String profileName;
  private String channel;
  private String userId;
  private String messagesJson;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime lastActiveAt;
  private LocalDateTime archivedAt;
  // getters / setters
}
```

### 5. CliSpringBootstrap（oryxos-cli，重命令共用 Spring 启动器）

```java
package com.oryxos.cli;

/**
 * 重命令（chat/serve/gateway）共用的 Spring 上下文启动器。
 * 显式声明 @EnableJpaRepositories/@EntityScan 的 basePackages="com.oryxos.storage"——
 * CLI 模块与 storage 模块不同 Java 包，不声明会得到 "Found 0 JPA repository interfaces"。
 * 并把 16/17 节 POJO 引擎以 @Bean 方法装配进上下文。
 */
@Configuration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.oryxos.storage")
@EntityScan(basePackages = "com.oryxos.storage")
public class CliSpringBootstrap {
  // @Bean: ProfileRegistry / ContextLoader / ToolRegistry(空实现或第20节占位) /
  //        ToolInvocationStore(JpaToolInvocationStore) / PromptBuilder / ToolExecutor /
  //        ReActLoop / SessionManager(JpaSessionManager) / AgentService /
  //        Map<String, ChatModel> 显式映射 + DefaultProviderService
}
```

### 6. CliChannel（oryxos-channel-cli，chat 交互）

```java
package com.oryxos.channel.cli;

import com.oryxos.core.AgentService;
import com.oryxos.core.Session;
import com.oryxos.core.SessionManager;

/**
 * CLI Channel：oryxos chat 命令的交互实现。
 * 读 stdin 写 stdout，维护当前 Session，每行交 AgentService.process，/quit 退出。
 * 只做"读输入→交引擎→打印结果"，不承担任何 Agent 智能。
 */
public class CliChannel {

  public CliChannel(AgentService agentService, SessionManager sessionManager);

  /** 进入交互循环，直到用户输入 /quit. */
  public void runInteractive(String channel, String user, String profileName);
}
```

### 7. 12 个子命令（oryxos-cli）

| 命令 | 轻/重 | 行为 |
|------|-------|------|
| `init` | 轻 | 创建 `.oryxos/` 工作区骨架 |
| `status` | 轻 | 查配置与工作区状态 |
| `chat [--profile <name>]` | 重 | 启动 Spring → CliChannel 交互对话 |
| `serve [--port]` | 重 | 启动 Spring（26 节补 WebServer 本体） |
| `gateway` | 重 | 启动 Spring（守护进程，多 Channel，后续节补） |
| `profile list` | 轻 | 列 Profile/Agent 目录 |
| `profile create <name>` | 轻 | 创建 Profile |
| `profile show <name>` | 轻 | 查看 Profile |
| `profile delete <name>` | 轻 | 删除 Profile |
| `provider list` | 轻 | 列 Provider |
| `tool list` | 轻 | 列可用 Tool |
| `session list` | 轻 | 列会话 |

## 命令解析（Picocli）

- `OryxOsCli` 是 `@Command(name = "oryxos")` 主命令，`mixinStandardHelpOptions = true`（自带 `--help`/`--version`），`run()` 裸命令打印版本。
- 12 子命令各一个 `@Command` 类，`main` 里 `CommandLine.addSubcommand(...)` 注册。
- 每个子命令类 `implements Runnable`，`run()` 按轻/重分流执行。
