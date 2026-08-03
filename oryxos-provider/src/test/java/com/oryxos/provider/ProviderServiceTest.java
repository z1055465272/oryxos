package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.OryxTool;
import com.oryxos.core.Profile;
import com.oryxos.core.Profile.ProviderRef;
import com.oryxos.core.Prompt;
import com.oryxos.core.Response;
import com.oryxos.core.Session;
import com.oryxos.core.ToolCall;
import com.oryxos.core.ToolResult;
import com.oryxos.storage.LlmCall;
import com.oryxos.storage.LlmCallRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

class ProviderServiceTest {

  private ChatModel deepseekModel;
  private ChatModel kimiModel;
  private ToolSchemaAdapter adapter;
  private LlmCallRepository audit;
  private DefaultProviderService service;

  @BeforeEach
  void setUp() {
    deepseekModel = mock(ChatModel.class);
    kimiModel = mock(ChatModel.class);
    adapter = new ToolSchemaAdapter();
    audit = mock(LlmCallRepository.class);

    service =
        new DefaultProviderService(
            Map.of("deepseek", deepseekModel, "kimi", kimiModel), adapter, audit);

    // 用真实 ChatResponse（含 Generation/AssistantMessage）让 Core 转换路径可跑通
    ChatResponse plainResponse =
        new ChatResponse(List.of(new Generation(new AssistantMessage("hello"))));
    when(deepseekModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(plainResponse);
    when(kimiModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(plainResponse);
  }

  @Test
  @DisplayName("按名路由_两个provider不串台")
  void routesToCorrectProviderTwoProvidersDoNotConflict() {
    service.chat("s-1", profileUsing("kimi"), new Prompt("hello"));

    verify(kimiModel, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    verify(deepseekModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
  }

  @Test
  @DisplayName("调用失败_审计必须留下success为false的记录")
  void callFailureMustRecordAuditWithSuccessFalse() {
    when(deepseekModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenThrow(new RuntimeException("connect timeout"));

    assertThrows(
        RuntimeException.class,
        () -> service.chat("s-1", profileUsing("deepseek"), new Prompt("hello")));

    verify(audit)
        .save(
            org.mockito.ArgumentMatchers.<LlmCall>argThat(
                call ->
                    "s-1".equals(call.getSessionId())
                        && "deepseek".equals(call.getProvider())
                        && Boolean.FALSE.equals(call.getSuccess())
                        && call.getErrorMessage() != null
                        && call.getErrorMessage().contains("timeout")));
  }

  @Test
  @DisplayName("带工具schema调用_请求里关闭了自动执行")
  @SuppressWarnings("unchecked")
  void callWithToolSchemaDisablesAutoExecution() {
    OryxTool httpGetTool = mock(OryxTool.class);
    when(httpGetTool.getName()).thenReturn("http_get");
    when(httpGetTool.getDescription()).thenReturn("HTTP GET request");
    when(httpGetTool.getInputSchema())
        .thenReturn("{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}");

    service.chat("s-1", profileUsing("deepseek"), new Prompt("fetch data", List.of(httpGetTool)));

    var captor = ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
    verify(deepseekModel).call(captor.capture());
    var captured = captor.getValue();

    assertThat(captured.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
    var options = (ToolCallingChatOptions) captured.getOptions();
    assertThat(options.getInternalToolExecutionEnabled()).isFalse();

    assertThat(captured.getInstructions()).isNotEmpty();
  }

  @Test
  @DisplayName("多轮消息被翻译成Spring AI消息序列")
  void mapsMultiTurnMessages_intoSpringAiMessages() {
    Session session = new Session("s-1", "test", "cli", "u-1");
    session.appendUserMessage("查天气");
    ToolCall call = new ToolCall("call-1", "http_get", "{\"city\":\"beijing\"}");
    session.appendAssistant("我用 http_get 查", List.of(call));
    session.appendToolResult(call, ToolResult.ok("{\"temp\":-5}"));

    Prompt prompt = new Prompt("查天气", session.messages(), "You are 运维小欧", List.of());

    service.chat("s-1", profileUsing("deepseek"), prompt);

    var captor = ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
    verify(deepseekModel).call(captor.capture());
    List<Message> messages = captor.getValue().getInstructions();

    // system + user + assistant(带 toolCall) + tool 结果，四段完整
    assertThat(messages).hasSize(4);
    assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
    assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
    assertThat(messages.get(3)).isInstanceOf(ToolResponseMessage.class);

    AssistantMessage assistant = (AssistantMessage) messages.get(2);
    assertThat(assistant.getToolCalls()).hasSize(1);
    assertThat(assistant.getToolCalls().get(0).id()).isEqualTo("call-1");
    assertThat(assistant.getToolCalls().get(0).name()).isEqualTo("http_get");

    ToolResponseMessage toolResponse = (ToolResponseMessage) messages.get(3);
    assertThat(toolResponse.getResponses().get(0).id()).isEqualTo("call-1");
    assertThat(toolResponse.getResponses().get(0).name()).isEqualTo("http_get");
    assertThat(toolResponse.getResponses().get(0).responseData()).isEqualTo("{\"temp\":-5}");
  }

  @Test
  @DisplayName("返回自有Response_携带文本与工具调用")
  void returnsOryxosResponse_withTextAndToolCalls() {
    ChatResponse toolResponse =
        new ChatResponse(
            List.of(
                new Generation(
                    new AssistantMessage(
                        "我来查",
                        Map.of(),
                        List.of(
                            new AssistantMessage.ToolCall(
                                "call-9", "function", "http_get", "{\"url\":\"a\"}"))))));
    when(deepseekModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(toolResponse);

    Response response = service.chat("s-1", profileUsing("deepseek"), new Prompt("查天气"));

    assertThat(response.text()).isEqualTo("我来查");
    assertThat(response.hasToolCalls()).isTrue();
    assertThat(response.toolCalls()).hasSize(1);
    assertThat(response.toolCalls().get(0).name()).isEqualTo("http_get");
    assertThat(response.toolCalls().get(0).id()).isEqualTo("call-9");
  }

  @Test
  @DisplayName("未知provider抛异常")
  void unknownProviderThrowsException() {
    assertThrows(
        ProviderNotFoundException.class,
        () -> service.chat("s-1", profileUsing("nonexistent"), new Prompt("hello")));
  }

  private static Profile profileUsing(String providerName) {
    return new Profile(
        "test-profile",
        "test description",
        new Profile.Identity("test-agent", "You are a test agent."),
        new ProviderRef(providerName, providerName + "-chat", 0.7),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Settings(10, 20));
  }
}
