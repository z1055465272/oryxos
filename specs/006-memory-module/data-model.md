# Data Model: Memory 模块（第 22 节）

**Branch**: `022-lesson22-memory` | **Date**: 2026-08-05 | **Spec**: [spec.md](spec.md)

## 实体：MEMORY.md（长期记忆文件）

位置 `.oryxos/memory/MEMORY.md`，一个 Markdown 文件、两个区块。手工维护（Agent 经 `save_memory` 写入，系统不自动生成内容；`oryxos init` 生成初始骨架）。

### 区块结构

```markdown
## 核心记忆

- [2026-08-05] 用户偏好用 Java

## 归档记忆

- [2026-08-04] 昨天排查了一个网络问题
- [2026-08-05] 今天部署了 v0.1.0
```

| 区块 | Header | 行为 |
|------|--------|------|
| 核心记忆 | `## 核心记忆` | 永远完整返回、不截断、不换出；`scope=CORE` 写入此区 |
| 归档记忆 | `## 归档记忆` | 超 `MAX_ARCHIVE_CHARS=4000` 字截断（保留最近 4000 字）；`scope=ARCHIVAL`（缺省）写入此区；检索只在此区做 |

### 写入格式

`append(content, scope)` 追加一条：`\n- [<LocalDate.now()>] <content>`，按 scope 定位目标区块、追加到该区块段末尾。

### 读取形态

`load()` = `extractSection(核心区)`（完整）+ `"\n"` + `truncateIfNeeded(extractSection(归档区))`（归档区可能被截断）。**每次重新 `Files.readString`，不缓存**（契约一）。

### 截断规则（只裁归档区，核心区一字不动）

`truncateIfNeeded(archiveSection)`：`len <= 4000` 原样返回；否则 `substring(len - 4000)` —— 只裁这一段文本，物理上不可能动到核心区。

### 检索规则（只搜归档区，核心区不参与）

`recallByKeyword(keyword)`：读文件 → 提取归档区 → `lines().filter(line -> line.contains(keyword)).toList()`，朴素包含匹配。

### 状态/边界

| 场景 | 行为 |
|------|------|
| 文件不存在（首次） | 读取返回空（核心/归档都空），append 自动建目录 + 文件 |
| 归档区超过 4000 字 | 截断，最早内容被裁、最近内容保留 |
| scope 非法/缺失 | 缺省 `ARCHIVAL`（系统不猜核心，也不因参数异常中断写入） |
| 检索未命中 | 返回"没有找到相关记忆"提示（Tool 层），不抛异常 |

## 实体：MemoryScope（枚举）

`CORE` / `ARCHIVAL` 两个值。值对象，随 `MemoryService` 接口放 `oryxos-core`。

## 实体：会话记忆（复用，非本节新增）

`Session`（core，18 节交付）承载对话历史。`DefaultMemoryService.buildContext(session, profile)` 从 `session.messages()` 取历史摘要拼入记忆上下文。不新增表、不改 sessions 表。
