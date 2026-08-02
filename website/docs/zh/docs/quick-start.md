---
title: 快速开始
---

# 快速开始

## 前置要求

- JDK 21 及以上
- Maven 3.9+(或直接用仓库自带的 Maven Wrapper `./mvnw`,无需安装 Maven)
- 一个 LLM API key(如 [DeepSeek](https://platform.deepseek.com/) 或 [Kimi](https://platform.moonshot.cn/))

## 构建与运行

```bash
# 1. 构建 fat JAR
./mvnw clean package

# 2. 初始化工作区(在当前目录生成 .oryxos/)
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar init

# 3. 配置 LLM API key(通过环境变量注入,不明文写进配置文件)
export DEEPSEEK_API_KEY=sk-xxx

# 4. 编辑默认 Profile,填入 Provider 和模型
#    .oryxos/profiles/default.yaml

# 5. 交互式对话(开发调试主要方式)
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar chat

# 6. 或启动 HTTP API 服务,供业务系统集成
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar serve  # 默认端口 8080
```

## 通过 REST API 调用

```bash
# 创建会话
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"profile":"default","user_id":"u1"}'

# 发消息(保持会话上下文)
curl -X POST http://localhost:8080/api/v1/sessions/{id}/messages \
  -H "Content-Type: application/json" \
  -d '{"message":"查一下北京天气并告诉我穿什么"}'

# 无状态调用一次 Agent(适合短任务)
curl -X POST http://localhost:8080/api/v1/agents/default/invoke \
  -H "Content-Type: application/json" \
  -d '{"message":"查一下北京天气并告诉我穿什么"}'
```

启动后完整 API 文档见 `http://localhost:8080/swagger-ui`(OpenAPI 3.0)。

## 工作区结构

`oryxos init` 生成 `.oryxos/` 工作目录:

```text
.oryxos/
├── profiles/          # Profile YAML(每个 Agent 一个)
├── sessions/          # 会话数据
├── skills/            # SKILL.md 文件
├── memory/MEMORY.md   # 长期记忆(Agent 通过 save_memory 写入)
├── logs/              # 结构化日志
├── tools/             # 自定义 Tool 配置
├── mcp_servers.yaml   # MCP server 配置
├── oryxos.db          # SQLite(会话 + 审计表)
├── AGENTS.md          # Bootstrap:项目级 agent 行为说明
├── SOUL.md            # Bootstrap:agent 人格定义
└── USER.md            # Bootstrap:用户偏好
```

## 命令行

三种运行模式(共享同一份 Profile 配置和 Session 存储):

| 命令 | 说明 |
| --- | --- |
| `oryxos chat` | 交互式多轮对话(可选 `--profile`) |
| `oryxos serve` | 启动 HTTP API 服务(默认 8080) |
| `oryxos gateway` | 常驻守护进程(多 Channel,核心阶段挂 CLI + HTTP API) |

核心 12 个命令:`init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`。
