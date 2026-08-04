# Quickstart: Tool 体系（第 20 节）验证指南

**Branch**: `020-lesson20-tool` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md) | **Contracts**: [contracts/README.md](./contracts/README.md)

## 前置条件

- JDK 21（本地 `D:\envconfig\JDK\jdk-21.0.11`）+ Maven 3.9.16（不在 PATH，用 `cmd //c` 调用，先 `export JAVA_HOME`，见记忆 build-environment-windows）
- 前序节（16~19）测试全绿（本节依赖其契约）

## 自动化验证（harness，全不碰网络）

```bash
export JAVA_HOME='D:\envconfig\JDK\jdk-21.0.11'
export PATH="$JAVA_HOME/bin:$PATH"

# 1. 全量门禁（含 Spotless/Checkstyle/P3C/PMD，mvn clean verify）
cmd //c "mvn clean verify"

# 2. 只跑本节测试类（oryxos-tool 模块）
cmd //c "mvn -pl oryxos-tool test"

# 3. 关键回归单测
cmd //c "mvn -pl oryxos-tool test -Dtest=OryxToolContractTest"     # 契约三件套非空
cmd //c "mvn -pl oryxos-tool test -Dtest=ToolRegistryTest"          # 按 Profile 过滤子集不多不少
cmd //c "mvn -pl oryxos-tool test -Dtest=McpClientServiceTest"      # 失联只 WARN、其余照常注册、不炸启动
cmd //c "mvn -pl oryxos-tool test -Dtest=FileToolsTest,ShellToolsTest,HttpToolsTest,McpToolAdapterTest"
```

**预期结果**：以上命令全绿。`OryxToolContractTest` 遍历注册表内每个工具断言三件套非空；`ToolRegistryTest` 断言过滤子集精确匹配；`McpClientServiceTest` 断言坏 server 只 WARN、好 server 工具照常注册、整体不抛异常。

## 运行期冒烟（轻命令/重命令）

```bash
# 1. 初始化工作区（已存在则幂等；本节点在 .oryxos/ 新增 mcp_servers.yaml 模板）
cmd //c "mvn -pl oryxos-cli -am exec:java -Dexec.mainClass=com.oryxos.cli.OryxOscCli -Dexec.args=init"

# 2. 查看已注册工具（tool list，验证内置工具 + 方式三示例工具可见）
cmd //c "mvn -pl oryxos-cli -am exec:java -Dexec.mainClass=com.oryxos.cli.OryxOscCli -Dexec.args='tool list'"

# 3. 与 Agent 对话，触发工具调用（读文件/发请求/推通知，真链路目检 tool_invocations）
cmd //c "mvn -pl oryxos-cli -am exec:java -Dexec.mainClass=com.oryxos.cli.OryxOscCli -Dexec.args='chat'"
```

**预期结果**：`tool list` 能看到 `read_file`/`write_file`/`list_dir`/`shell`/`http_get`/`http_post`/`notify`（及 MCP 工具若配了 server）；`chat` 里 Agent 调工具后，`.oryxos/oryxos.db` 的 `tool_invocations` 表多一条记录（审计写入目检）。

## 人工确认项（harness 覆盖不到）

| 项 | 验证方式 | 依赖 |
|---|---|---|
| 方式一零代码 | 写 SKILL.md + 连真实 MCP server，Agent 读懂意图并调用外部工具 | 真模型 + 真 server |
| 方式三重代码 | `@Tool` 示例工具在 `tool list` 可见、Agent 能调通 | 真模型 |
| `tool_invocations` 真链路写入 | 跑一次 chat 后查 SQLite | 真模型 |
| 24 节接线 | Sandbox 换成 WhitelistSandbox 后越界拦截语义不变 | 24 节实现 |

> 详见课件"五、做完怎么验"。以上 harness 判卷 + 人工项清单在第 7 步验收报告中重申。
