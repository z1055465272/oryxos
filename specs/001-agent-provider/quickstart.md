# Quickstart: Agent Provider 验证

## Prerequisites

- JDK 21+
- Maven 3.6.3+ (当前环境 Maven 3.3.9 过低, 需先升级或使用 Maven Wrapper)
- 可选: `DEEPSEEK_API_KEY` 环境变量 (仅集成冒烟测试需要)

## 运行所有单测

```bash
mvn test
```

预期: ProfileLoaderTest, ProviderServiceTest, ToolSchemaAdapterTest, LlmCallRepositoryTest 全部 PASS。

## 运行集成冒烟 (需要真 API Key)

```bash
export DEEPSEEK_API_KEY=sk-your-key-here
mvn test -Dgroups=integration -pl oryxos-provider
```

预期: `ProviderSmokeIT` PASS, 控制台可看到真实 LLM 响应内容。

## 仅跑本节相关模块

```bash
mvn test -pl oryxos-core,oryxos-provider,oryxos-storage
```

## 完整构建门禁

```bash
mvn clean verify
```

预期:
- Spotless check PASS (Google 格式)
- Checkstyle PASS
- P3C-PMD PASS (阿里编码规约)
- 所有单测 PASS
- SpotBugs PASS (安全扫描)

## 人工验证项

1. **依赖确认**: `mvn dependency:tree -pl oryxos-provider` 确认 `spring-ai-openai` 和 `spring-ai-model` 存在且版本正确
2. **集成冒烟真跑一次**: 配真 key, 跑通 `ProviderSmokeIT`
3. **明文 key 扫描**: `grep -r "sk-" oryxos-provider/src/` 无结果 (key 全走环境变量)
4. **`llm_calls` 表结构**: 启动后 `.oryxos/oryxos.db` 中 `llm_calls` 表包含 `success` 和 `error_message` 列
