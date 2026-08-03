# Quickstart: CLI 命令行入口 + 会话持久化地基

**Feature**: CLI 命令行入口 + 会话持久化地基 | **Date**: 2026-08-03

## 前提

- JDK 21 + Maven；本地仓库已有 Picocli 4.7.7、SQLite、Jackson（H3 已核实）。
- 第 16/17 节交付物已就位（ProviderService、ReActLoop、AgentService、SessionManager 契约）。

## 自动化验收（harness，`mvn test` 全绿即通过）

```bash
# 会话契约层 + 持久化层（幂等/隔离/id 只一处 + 建表能存能读/messages_json 完整/模拟重启历史仍在）
mvn test -pl oryxos-storage -Dtest="SessionManagerTest,SessionRepositoryTest"

# 全量门禁（含 P3C/SpotBugs/FindSecBugs/PMD）
mvn clean verify
```

**预期**：三个测试类全绿；`mvn clean verify` 全绿即本节实现完成。

## 人工验收清单（课件"五、做完怎么验"，harness 判卷后人工过）

```bash
# 1. 轻命令秒回（不启动 Spring，无 Spring banner 日志）
java -jar oryxos-boot/target/oryxos-boot-*.jar init
java -jar oryxos-boot/target/oryxos-boot-*.jar profile list

# 2. 重命令才启动 Spring（看到 Spring 启动日志；chat 启动日志里 "Found N JPA repository interfaces" N > 0）
java -jar oryxos-boot/target/oryxos-boot-*.jar chat

# 3. chat 交互多轮对话，/quit 退出
#    > 你好
#    > /quit

# 4. 12 个子命令 --help 正常（Picocli 自带）
java -jar oryxos-boot/target/oryxos-boot-*.jar chat --help

# 5. 三种运行模式共享同一份 Profile 和 Session 存储（切换模式数据不丢）
#    chat 里说一句话 → /quit → 再 chat 同一三元组，历史还在
```

## 关键验证点（对齐课件）

| 验收点 | 怎么验 | 预期 |
|--------|--------|------|
| 会话幂等 | `SessionManagerTest`（storage） | 同一三元组返回同一 Session |
| 会话隔离 | `SessionManagerTest`（storage） | 三元组任一不同则不同 Session |
| 会话持久化 | `SessionRepositoryTest` | 存读一致、模拟重启历史还在 |
| 重命令扫描范围 | `chat` 启动日志 | "Found N JPA repository interfaces" 且 N > 0 |
| 轻/重分流 | `init` vs `chat` | init 秒回无 Spring 日志；chat 有 Spring 启动日志 |

> 契约细节见 [contracts/README.md](./contracts/README.md)，表结构与序列化见 [data-model.md](./data-model.md)。
