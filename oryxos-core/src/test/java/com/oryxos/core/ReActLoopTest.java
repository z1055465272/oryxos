package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReActLoopTest {

  private final ProviderService providerService = mock(ProviderService.class);
  private final PromptBuilder promptBuilder = mock(PromptBuilder.class);
  private final ToolExecutor toolExecutor = mock(ToolExecutor.class);
  private final ReActLoop loop = new ReActLoop(providerService, promptBuilder, toolExecutor);

  @Test
  @DisplayName("无工具调用_一轮收尾")
  void returnsFinalResponse_whenNoToolCalls() {
    when(providerService.chat(any(), any(), any()))
        .thenReturn(new Response("你好，我是运维小欧", List.of()));

    Session session = newSession();
    String reply = loop.run(session, "介绍一下自己", profileWithMaxIterations(10));

    assertThat(reply).isEqualTo("你好，我是运维小欧");
    verify(providerService, times(1)).chat(any(), any(), any());
    verify(toolExecutor, never()).execute(any(), any());
  }

  @Test
  @DisplayName("多轮工具调用_闭环后返回最终响应")
  void executesToolsAndLoops_untilFinalResponse() {
    when(providerService.chat(any(), any(), any()))
        .thenReturn(
            new Response(
                "我用 http_get 查天气",
                List.of(new ToolCall("call-1", "http_get", "{\"city\":\"beijing\"}"))))
        .thenReturn(new Response("建议穿羽绒服", List.of()));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("{\"temp\":-5}"));

    Session session = newSession();
    String reply = loop.run(session, "今天北京天气怎么样，我该穿什么", profileWithMaxIterations(10));

    assertThat(reply).isEqualTo("建议穿羽绒服");
    verify(providerService, times(2)).chat(any(), any(), any());
    verify(toolExecutor, times(1)).execute(any(), any());
  }

  @Test
  @DisplayName("模型一直要调工具_转满最大轮数强制停")
  void stopsAfterMaxIterations_whenModelKeepsRequestingTools() {
    // 每轮都要调工具，永不收敛（课件坑一回归：死循环兜底）
    when(providerService.chat(any(), any(), any()))
        .thenReturn(new Response("需要工具", List.of(new ToolCall("call-x", "http_get", "{}"))));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("ok"));

    Session session = newSession();
    String reply = loop.run(session, "查天气", profileWithMaxIterations(10));

    // 恰好 10 轮，一轮不多
    verify(providerService, times(10)).chat(any(), any(), any());
    assertThat(reply).contains("达到最大轮数");
  }

  @Test
  @DisplayName("Profile未配置最大轮数_用默认值10")
  void usesDefaultMaxIterations10_whenNotConfigured() {
    when(providerService.chat(any(), any(), any()))
        .thenReturn(new Response("需要工具", List.of(new ToolCall("call-x", "http_get", "{}"))));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("ok"));

    loop.run(newSession(), "hi", profileWithoutSettings());

    verify(providerService, times(10)).chat(any(), any(), any());
  }

  @Test
  @DisplayName("每轮响应与工具结果都累积进Session")
  void accumulatesMessagesAndToolResults_intoSession() {
    when(providerService.chat(any(), any(), any()))
        .thenReturn(
            new Response(
                "我用 http_get 查天气",
                List.of(new ToolCall("call-1", "http_get", "{\"city\":\"beijing\"}"))))
        .thenReturn(new Response("建议穿羽绒服", List.of()));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("{\"temp\":-5}"));

    Session session = newSession();
    loop.run(session, "天气", profileWithMaxIterations(10));

    assertThat(session.messages()).filteredOn(m -> m instanceof Session.UserMessage).hasSize(1);
    assertThat(session.messages())
        .filteredOn(m -> m instanceof Session.ToolResultMessage)
        .hasSize(1);
    assertThat(session.messages())
        .filteredOn(m -> m instanceof Session.AssistantMessage)
        .hasSize(2);
  }

  private static Session newSession() {
    return new Session("s-1", "test", "cli", "u-1");
  }

  private static Profile profileWithMaxIterations(int maxIterations) {
    return new Profile(
        "test",
        "desc",
        new Profile.Identity("agent", "role"),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Settings(maxIterations, 20));
  }

  private static Profile profileWithoutSettings() {
    return new Profile(
        "test",
        "desc",
        new Profile.Identity("agent", "role"),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);
  }
}
