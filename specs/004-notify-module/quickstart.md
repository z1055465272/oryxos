# Quickstart: Notify 模块

**Feature**: Notify 模块 — Agent 主动推送出口 | **Date**: 2026-08-04

## Prerequisites

- JDK 21
- Maven wrapper (`./mvnw`) 可用
- 前序节（16/17/18）交付物完整，`mvn clean verify` 全绿

## Build & Test

```bash
# 全量门禁（含前序节测试 + 静态检查）
./mvnw clean verify

# 只跑本节测试
./mvnw test -pl oryxos-tool -Dtest="com.oryxos.tool.notify.*,com.oryxos.tool.builtin.*,com.oryxos.tool.sandbox.*,com.oryxos.tool.core.*"

# 只跑关键回归单测
./mvnw test -pl oryxos-tool -Dtest=WebhookNotifyAdapterTest
./mvnw test -pl oryxos-tool -Dtest=NotifyToolsTest

# 验证依赖方向（notify 包不逆向依赖 oryxos-web, oryxos-channel-cli）
./mvnw dependency:tree -pl oryxos-tool | grep -E "oryxos-web|oryxos-channel-cli" # 预期无输出
```

## Validation Scenarios

### 1. WebhookNotifyAdapter 发送到 MockWebServer

```bash
./mvnw test -pl oryxos-tool -Dtest=WebhookNotifyAdapterTest
```

预期：3 个测试全绿
- `sendPostsJsonWithContentField` — POST body 含 `{"content": "test"}`
- `targetUrlFromNotifyTargetConfigNotHardcoded` — URL 来自 NotifyTarget.config
- `serverError5xxPropagatesException` — 5xx 时异常向上抛

### 2. NotifyTools 三步顺序 & 缺省逻辑

```bash
./mvnw test -pl oryxos-tool -Dtest=NotifyToolsTest
```

预期：3+ 测试全绿
- notify_channels 未配置 → `ToolResult.fail(...)`
- channel 缺省 → 取第一个渠道
- InOrder 断言 enforce 先于 send 被调用

### 3. Profile 配置解析

```bash
# 验证 NotifyChannelConfig 解析（环境变量占位符）
./mvnw test -pl oryxos-core -Dtest=ProfileLoaderTest
```

修改 ProfileLoaderTest 的 `notify_channels` YAML 配置段后，所有 ProfileLoaderTest 测试仍全绿。

## Cross-Module Regression

```bash
# oryxos-core 测试不破（Profile 类型变更后）
./mvnw test -pl oryxos-core
# oryxos-provider 测试不破
./mvnw test -pl oryxos-provider
# oryxos-storage 测试不破
./mvnw test -pl oryxos-storage
```

## Remaining Manual Verification

- 用真实 webhook URL（如企业微信群机器人）配置到 Profile YAML，通过 CLI chat 让 Agent 调 notify，验证群内收到消息
- 接口中立性自查：构想企业微信 SDK 实现——`NotifyChannelAdapter.send(NotifyTarget, String)` 签名不需要改
