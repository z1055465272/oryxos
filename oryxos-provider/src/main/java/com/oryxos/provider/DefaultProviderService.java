package com.oryxos.provider;

import com.oryxos.core.Profile;
import com.oryxos.core.Prompt;
import com.oryxos.storage.LlmCall;
import com.oryxos.storage.LlmCallRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;

/** ProviderService 的默认实现：显式映射路由 + 审计落库 + 关闭自动 tool 执行. */
public class DefaultProviderService implements ProviderService {

  private static final Logger log = LoggerFactory.getLogger(DefaultProviderService.class);

  private final Map<String, ChatModel> providerMap;
  private final ToolSchemaAdapter toolSchemaAdapter;
  private final LlmCallRepository llmCallRepository;

  /** 构建 ProviderService 实例，传入显式映射表而非靠类型扫描. */
  public DefaultProviderService(
      Map<String, ChatModel> providerMap,
      ToolSchemaAdapter toolSchemaAdapter,
      LlmCallRepository llmCallRepository) {
    this.providerMap = Map.copyOf(providerMap);
    this.toolSchemaAdapter = toolSchemaAdapter;
    this.llmCallRepository = llmCallRepository;
  }

  @Override
  public ChatModel resolve(String providerName) {
    ChatModel model = providerMap.get(providerName);
    if (model == null) {
      throw new ProviderNotFoundException(providerName);
    }
    return model;
  }

  @Override
  public ChatResponse chat(String sessionId, Profile profile, Prompt prompt) {
    String providerName = profile.provider() != null ? profile.provider().name() : null;
    ChatModel model = providerMap.get(providerName);
    if (model == null) {
      throw new ProviderNotFoundException(providerName != null ? providerName : "null");
    }

    var tools =
        prompt.availableTools() != null && !prompt.availableTools().isEmpty()
            ? toolSchemaAdapter.toSpringAiTools(prompt.availableTools())
            : null;

    var userMsg = new UserMessage(prompt.userMessage());
    var options =
        OpenAiChatOptions.builder()
            .internalToolExecutionEnabled(false)
            .model(profile.provider() != null ? profile.provider().model() : null)
            .build();
    if (tools != null) {
      options.setTools(tools);
    }

    var springPrompt = new org.springframework.ai.chat.prompt.Prompt(List.of(userMsg), options);

    long startedAt = System.currentTimeMillis();
    String modelName = profile.provider() != null ? profile.provider().model() : "unknown";

    try {
      ChatResponse response = model.call(springPrompt);
      recordAudit(sessionId, providerName, modelName, response, startedAt, true, null);
      return response;
    } catch (RuntimeException e) {
      log.error("LLM call failed: provider={}, model={}", providerName, modelName, e);
      recordAudit(sessionId, providerName, modelName, null, startedAt, false, e.getMessage());
      throw e;
    }
  }

  private void recordAudit(
      String sessionId,
      String provider,
      String model,
      ChatResponse response,
      long startedAt,
      boolean success,
      String errorMessage) {
    try {
      LlmCall call = new LlmCall();
      call.setSessionId(sessionId);
      call.setProvider(provider);
      call.setModel(model);
      call.setDurationMs(System.currentTimeMillis() - startedAt);
      call.setSuccess(success);
      call.setErrorMessage(errorMessage);
      call.setCreatedAt(LocalDateTime.now());

      if (response != null) {
        var usage = response.getMetadata().getUsage();
        if (usage != null) {
          call.setPromptTokens(usage.getPromptTokens() != null ? usage.getPromptTokens() : 0);
          call.setCompletionTokens(
              usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0);
          call.setTotalTokens(usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
        }
      }

      llmCallRepository.save(call);
    } catch (Exception e) {
      log.error("Failed to write llm_calls audit record", e);
    }
  }
}
