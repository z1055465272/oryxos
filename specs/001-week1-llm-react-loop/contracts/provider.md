# 契约：`ProviderService`（能力一）

**关联实体**: [Provider](../data-model.md#provider)、[Profile](../data-model.md#profile)

**来源**: 技术方案 §3.1 / §3.2，宪法原则二、三

---

## 职责

统一管理所有 LLM Provider，对 `ReActLoop` 屏蔽不同厂商差异。只做两件事（宪法原则二）：**协议转换**（`chatModel.call`）+ 配合 `@Tool` 的 **schema 生成**。**禁用** Spring AI 自动 tool 执行。

## 接口

```java
interface ProviderService {
    // 按 provider name 显式路由到 ChatModel（宪法原则三）
    ChatModel chatModel(String providerName);

    // 按 Profile 发起一次 LLM 调用，返回原始响应（含 tool calls）
    ChatResponse call(Profile profile, Prompt prompt);

    // 运行状态：可用 provider 列表（供 status / 调试）
    List<String> availableProviders();
}
```

## 关键约束

1. **显式映射，不靠类型扫描**（宪法原则三）：`ProviderConfig` 从 `application.yaml` 读 `ai.providers` 列表，`ProviderService` 组装 `Map<String, ChatModel>`。`@Qualifier` 注入各 Bean 后按配置的 name 手工建表。
2. **只调 `chatModel.call(...)`，不用 `ChatClient.prompt().call()`**（宪法原则二）：返回的 `ChatResponse` 里 tool call 由 `ReActLoop` 自己取、`ToolExecutor` 自己执行，Provider 层不自动执行任何工具。
3. **不做 fallback / hedge racing**（技术方案 §3.3）：Provider 故障直接抛异常给 ReAct 循环，由上层按可重试策略处理。
4. **每次调用记录 `LlmCallRecorder`**（宪法原则五）：provider、model、token 用量、耗时。

## 配置示例（`application.yaml`）

```yaml
ai:
  providers:
    - name: deepseek
      model: deepseek-chat
      api-key: ${DEEPSEEK_API_KEY}     # 环境变量，不明文
    - name: kimi
      model: moonshot-v1-8k
      api-key: ${KIMI_API_KEY}
```

## 错误语义

| 情况 | 行为 |
|------|------|
| provider name 不存在 | 抛 `UnknownProviderException`（启动时 Profile 校验发现并记错误日志） |
| API key 缺失 | 启动时 `ConfigLoader` 报清晰错误，不静默失败 |
| 调用失败（网络/超时） | 异常上抛给 ReAct 循环，本轮失败按策略返回错误信息 |
