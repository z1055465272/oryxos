package com.oryxos.provider;

import com.oryxos.core.Profile;
import com.oryxos.core.Prompt;
import com.oryxos.core.ProviderService;
import com.oryxos.core.Response;
import com.oryxos.core.Session;
import com.oryxos.core.ToolCall;
import com.oryxos.storage.LlmCall;
import com.oryxos.storage.LlmCallRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * ProviderService 的默认实现：显式映射路由 + 消息序列映射 + 审计落库 + 关闭自动 tool 执行.
 *
 * <p>第 17 节适配：实现 core 契约，把 Prompt 的 systemMessage + messages 映射成 Spring AI 消息序列 （只做格式翻译、不执行任何工具），并把
 * Spring AI ChatResponse 转成 OryxOS 自有 {@link Response}.
 */
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

  /** 按 provider name 解析对应的 ChatModel，不存在时抛异常（保留显式映射能力，供扩展/排障用）. */
  public ChatModel resolve(String providerName) {
    ChatModel model = providerMap.get(providerName);
    if (model == null) {
      throw new ProviderNotFoundException(providerName);
    }
    return model;
  }

  @Override
  public Response chat(String sessionId, Profile profile, Prompt prompt) {
    String providerName = profile.provider() != null ? profile.provider().name() : null;
    ChatModel model = providerMap.get(providerName);
    if (model == null) {
      throw new ProviderNotFoundException(providerName != null ? providerName : "null");
    }

    var tools =
        prompt.availableTools() != null && !prompt.availableTools().isEmpty()
            ? toolSchemaAdapter.toSpringAiTools(prompt.availableTools())
            : null;

    List<Message> springMessages = buildSpringMessages(prompt);

    var options =
        OpenAiChatOptions.builder()
            .internalToolExecutionEnabled(false)
            .model(profile.provider() != null ? profile.provider().model() : null)
            .build();
    if (tools != null) {
      options.setTools(tools);
    }

    var springPrompt = new org.springframework.ai.chat.prompt.Prompt(springMessages, options);

    long startedAt = System.currentTimeMillis();
    String modelName = profile.provider() != null ? profile.provider().model() : "unknown";

    try {
      ChatResponse response = model.call(springPrompt);
      recordAudit(sessionId, providerName, modelName, response, startedAt, true, null);
      return toResponse(response);
    } catch (RuntimeException e) {
      log.error("LLM call failed: provider={}, model={}", providerName, modelName, e);
      recordAudit(sessionId, providerName, modelName, null, startedAt, false, e.getMessage());
      throw e;
    }
  }

  /** 把 Prompt 的 systemMessage + messages 映射成 Spring AI 消息序列；messages 为空时回退单条用户消息. */
  private List<Message> buildSpringMessages(Prompt prompt) {
    List<Message> springMessages = new ArrayList<>();
    if (prompt.systemMessage() != null && !prompt.systemMessage().isBlank()) {
      springMessages.add(new SystemMessage(prompt.systemMessage()));
    }
    if (prompt.messages() != null && !prompt.messages().isEmpty()) {
      for (Session.Message message : prompt.messages()) {
        springMessages.add(mapToSpringAiMessage(message));
      }
    } else {
      // 第 16 节兼容路径：无多轮消息时回退单条用户消息
      springMessages.add(new UserMessage(prompt.userMessage()));
    }
    return springMessages;
  }

  /** 把 OryxOS 自有消息转成 Spring AI 消息（只做格式翻译，不执行任何工具）. */
  private Message mapToSpringAiMessage(Session.Message message) {
    if (message instanceof Session.UserMessage userMessage) {
      return new UserMessage(userMessage.content());
    }
    if (message instanceof Session.AssistantMessage assistantMessage) {
      List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
      for (ToolCall call : assistantMessage.toolCalls()) {
        toolCalls.add(
            new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()));
      }
      return new AssistantMessage(assistantMessage.content(), Map.of(), toolCalls);
    }
    if (message instanceof Session.ToolResultMessage toolResult) {
      return new ToolResponseMessage(
          List.of(
              new ToolResponseMessage.ToolResponse(
                  toolResult.toolCallId(), toolResult.toolName(), toolResult.content())));
    }
    throw new IllegalArgumentException("Unknown session message type: " + message.getClass());
  }

  /** 把 Spring AI ChatResponse 转成 OryxOS 自有 Response（text + toolCalls）. */
  private Response toResponse(ChatResponse response) {
    if (response.getResult() == null || response.getResult().getOutput() == null) {
      return new Response("", List.of());
    }
    AssistantMessage output = response.getResult().getOutput();
    List<ToolCall> toolCalls = new ArrayList<>();
    if (output.getToolCalls() != null) {
      for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
        toolCalls.add(new ToolCall(toolCall.id(), toolCall.name(), toolCall.arguments()));
      }
    }
    String text = output.getText() != null ? output.getText() : "";
    return new Response(text, toolCalls);
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
