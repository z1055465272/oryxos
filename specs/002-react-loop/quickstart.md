# Quickstart: ReAct 循环引擎 — 验证指南

**Feature**: ReAct 循环引擎 | **Date**: 2026-08-03

## 前置条件

- 第 16 节交付物已就位：`Profile`/`ProfileRegistry`/`Prompt`/`OryxTool`/`ToolResult`（core），`DefaultProviderService`/`ToolSchemaAdapter`（provider），`LlmCall`+`LlmCallRepository`（storage）。
- 环境变量：Provider API key（如 `DEEPSEEK_API_KEY`）走环境变量占位，测试用 mock 不触网。

## 验证场景

### 1. 全量构建门禁（实现完成的定义）

```bash
mvn clean verify
```

**预期**: BUILD SUCCESS，单测全绿，P3C/SpotBugs/FindSecBugs/PMD 无阻断问题。

### 2. 只跑本节测试

```bash
mvn -pl oryxos-core,oryxos-storage,oryxos-provider test
```

**预期**: 
- `oryxos-core`：`ReActLoopTest`（无工具调用一轮收尾 / 多轮工具闭环 / **最大轮数强制停** / 消息累积）、`PromptBuilderTest`（四部分顺序 / **历史超 N 轮截断** / system prompt 末尾日期时间 / 空工具/空历史不崩）、`ToolExecutorTest`（成功审计 success=true / 失败审计 success=false 带原因 / **异常不吞** / 未知工具）、`AgentServiceTest`（处理期间 ProfileContext 可取 / **异常路径 finally 清理** / 会话持久化）、`ContextLoaderTest`（**改文件无缓存** / Skill 缺失报错 / Bootstrap 缺失 WARN）全绿。
- `oryxos-storage`：`ToolInvocationRepositoryTest`（tool_invocations 表存读 / success+error_message 列存在）。
- `oryxos-provider`：第 16 节测试回归 + 多轮消息映射测试全绿。

### 3. 关键回归单测（课件点名的最值钱两个）

```bash
mvn -pl oryxos-core test -Dtest=ReActLoopTest#stopsAfterMaxIterations_whenModelKeepsRequestingTools
mvn -pl oryxos-core test -Dtest=AgentServiceTest#clearsProfileContext_whenProcessingThrows
```

**预期**: 前者断言循环恰好 10 轮、回复含"达到最大轮数"；后者断言异常后 `ProfileContext.current()` 为 null。

### 4. 模块依赖方向检查（无 core→provider/storage 反向依赖）

```bash
grep -rn "com.oryxos.provider\|com.oryxos.storage" oryxos-core/src/main/java --include="*.java" | grep -v "^Binary"
```

**预期**: 无输出（core 只依赖接口/值对象，不 import provider/storage 具体类）。

### 5. 手工冒烟（真模型，人工项）

```bash
DEEPSEEK_API_KEY=xxx mvn -pl oryxos-provider test -Dgroups=integration -Dtest=ProviderSmokeIT
```

**预期**: 真实调用一次模型返回非空响应。`ReActLoop` 的 Demo 一对话版（问天气→调 http_get→给穿搭建议）需在后续节工具就位后用真模型人工跑通。

## 边界说明

- 本节 `ToolExecutor` 的 Sandbox 调用位留空并注明 24 节接线（沙箱未就位）。
- 长期记忆部分（Prompt 第二部分）留空，第 22 节接入。
- `ToolRegistry`/`SessionManager`/`ToolInvocationStore` 为 core 契约，`ToolInvocationStore` 本节在 storage 有 JPA 实现，其余后续节实现。
