# 契约：`oryxos` CLI 命令（第一周）

**关联实体**: [Profile](../data-model.md#profile)、[Session](../data-model.md#session)

**来源**: 技术方案 §8.1 / §8.7 / §8.4

---

## `oryxos init`

初始化 `.oryxos/` 工作区。

**行为**：在当前目录创建 `.oryxos/`，含 `agents/`、`memory/`、`sessions/`、`logs/`、`mcp_servers.yaml`、`AGENTS.md`、`SOUL.md`、`USER.md`、`oryxos.db`（空占位）；写入一个最小 `agents/default/AGENT.md` 示例（frontmatter：name/description/provider/tools/settings）。

**退出码**：0 成功；非 0 失败（目录已存在且校验失败时给出明确报错）。

**示例**：
```bash
$ oryxos init
已创建 .oryxos/ 工作区
```

---

## `oryxos chat [--profile <name>]`

交互式多轮对话（CLI Channel）。

**参数**：
- `--profile <name>`：指定 Profile（默认 `default`）
- 运行时输入：stdin 读入用户消息
- `exit` / `/quit`：退出对话

**行为**：维护当前 `Session`（`channel=cli`），每行输入调 `AgentService.process(session, text)`，把最终回复写 stdout。多轮共享同一 Session，后续轮次参考先前上下文。

**示例**：
```bash
$ oryxos chat --profile default
你> 你好
Oryx> 你好！有什么可以帮你？
你> 查一下北京天气并告诉我穿什么
Oryx> （调用 http_get 获取北京天气，基于数据给出穿衣建议）
你> /quit
```

**退出码**：0 正常退出；非 0 异常。
