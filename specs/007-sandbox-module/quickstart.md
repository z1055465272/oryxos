# Quickstart: Sandbox 安全校验模块（第 24 节）

**Branch**: `024-lesson24-sandbox` | **Date**: 2026-08-12 | **Spec**: [spec.md](spec.md)

本指南只说明如何验证本节功能，不含实现细节。契约详见 [contracts/README.md](contracts/README.md)，校验规则见 [data-model.md](data-model.md)。

## 1. 自动化验收（harness）

```bash
# 只跑 oryxos-tool 模块测试
mvn -pl oryxos-tool -am test
# 全量门禁（含 P3C/SpotBugs/FindSecBugs/PMD）
mvn clean verify
```

**预期**：
- `WhitelistSandboxTest` 绿：三类校验"允许 + 拒绝"成对、相对路径穿越被拦（normalize 回归）、通配符 `*.example.com` 命中 `api.example.com` 不命中 `evil-example.com`、空配置 = 全拒绝。
- 四个 Tool 既有测试全绿（mock `Sandbox` 拦截用例 + IO 未发生断言）：`FileToolsTest` / `ShellToolsTest` / `HttpToolsTest` / `NotifyToolsTest`。
- 前序节（16-22）测试回归全绿——尤其 `ToolExecutorTest`（失败路径审计）与 `OryxToolContractTest`。

## 2. 关键回归点（课件 harness 写出代码的守点）

1. **相对路径穿越必须被拦**：白名单只含 `/workspace`，构造 `..` 序列（如 `/workspace/../../outside/secret.txt`）→ 抛 `SandboxViolationException`。
2. **通配符域名不能被形似域名绕过**：白名单 `*.example.com` → `https://api.example.com/x` 放行（`assertDoesNotThrow`）、`https://evil-example.com/x` 被拦（`assertThrows`）——`endsWith` 必须带点号边界。
3. **接线回归"IO 未发生"**：四个 Tool 对白名单外输入被拦，mock 底层执行器 `verify(executor, never())`——只断言抛异常不够，要证明危险动作真的没跑。

## 3. 人工验证清单（课件"五、做完怎么验"）

- [ ] **集成验证（真实链路）**：配一个只允许 `ls` 的命令白名单，真跑一次白名单外命令，确认链路上抛 `SandboxViolationException`、`tool_invocations` 有 `success=false` 记录、`error_message` 人能读懂。
- [ ] **接口中立性自查**（思维练习）：`Sandbox.enforce(SandboxAction)` 换成 `KataMicroVmSandbox` 实现需要加方法吗？不需要才算墙立住了。
- [ ] **配置边界语义**：白名单配置项为空 = "什么都不允许"而非"不校验"，在配置说明里写明。
- [ ] **回归**：改造后的四个 Tool 原有测试全绿（harness 已覆盖）。

## 4. 运行前提

- `application.yaml` 已配置三块白名单（默认宽松值，覆盖现有 chat/demo/notify 依赖的路径/命令/域名）。
- 无需新表、无需 `.oryxos/` 额外初始化。
