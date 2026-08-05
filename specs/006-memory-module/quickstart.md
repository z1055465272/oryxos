# Quickstart: Memory 模块（第 22 节）

**Branch**: `022-lesson22-memory` | **Date**: 2026-08-05 | **Spec**: [spec.md](spec.md)

本指南只说明如何验证本节功能，不含实现细节。

## 1. 自动化验收（harness）

```bash
# 只跑 Memory 模块测试
mvn -pl oryxos-memory test
# 全量门禁（含 P3C/SpotBugs/FindSecBugs/PMD）
mvn clean verify
```

**预期**：
- `LongTermMemoryTest` 绿：写后立读（无缓存）、截断只裁归档核心一字不动、scope 路由正确区块、recall 只搜归档区。
- `MemoryToolsTest` 绿：scope 缺省写归档、未命中返回"没有找到相关记忆"不抛异常。
- `MemoryServiceTest` 绿：`buildContext` 返回核心记忆 + 会话历史组合，归档区不整体注入。
- 前序节（16-20）测试回归全绿——尤其 `PromptBuilderTest`（构造器新增 MemoryService 参数，mock 注入）与 `OryxToolContractTest`。

## 2. 关键回归点（课件写出代码的两个测试）

1. **截断只裁归档区、核心记忆一字不能少**：写核心记忆 + 500 条归档流水灌到远超 4000 字 → `load()` 后核心内容仍在、`归档流水 0` 被裁掉、`归档流水 499` 保留。
2. **写入后立刻可读、不允许有缓存**：`append("刚记的事", ARCHIVAL)` 后同进程内 `load().contains("刚记的事")` 为真、`recallByKeyword("刚记的事")` 非空。

## 3. 人工验证清单（课件"五、做完怎么验"）

- [ ] 真模型完整走一遍：对话里说一句值得记的话，Agent 主动调 `save_memory`；开新会话，系统提示里带着核心记忆。
- [ ] 跨进程：重启后 `MEMORY.md` 里的记忆还在（目检文件）。
- [ ] `MEMORY.md` 与 `USER.md` 角色分清：`USER.md` 全程只读、`MEMORY.md` 能被 Agent 写入（code review 确认无写 `USER.md` 的代码路径）。

## 4. 运行前提

- `oryxos init` 生成 `.oryxos/memory/MEMORY.md` 骨架（两区块：`## 核心记忆` / `## 归档记忆`）。
- 长记忆文件路径约定：`.oryxos/memory/MEMORY.md`。
