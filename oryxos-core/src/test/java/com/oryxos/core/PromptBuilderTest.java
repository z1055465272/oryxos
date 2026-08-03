package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

  private ContextLoader contextLoader;
  private ToolRegistry toolRegistry;
  private PromptBuilder promptBuilder;

  @BeforeEach
  void setUp() {
    contextLoader = mock(ContextLoader.class);
    toolRegistry = mock(ToolRegistry.class);
    promptBuilder = new PromptBuilder(contextLoader, toolRegistry);
  }

  @Test
  @DisplayName("四部分顺序组装")
  void assemblesFourPartsInOrder() {
    when(contextLoader.loadSystemPrompt(any())).thenReturn("【Bootstrap 内容】\n\n【SKILL.md 内容】");
    OryxTool httpGet = mock(OryxTool.class);
    when(toolRegistry.get("http_get")).thenReturn(Optional.of(httpGet));

    Session session = new Session("s-1", "test", "cli", "u-1");
    session.appendUserMessage("你好");
    Profile profile = profile("You are 运维小欧", List.of("http_get"), List.of(), 20);

    Prompt prompt = promptBuilder.build(session, profile);

    // ① system prompt：角色设定 + Bootstrap + Skill 正文 + 当前日期时间
    assertThat(prompt.systemMessage()).contains("You are 运维小欧");
    assertThat(prompt.systemMessage()).contains("【Bootstrap 内容】");
    assertThat(prompt.systemMessage()).contains("【SKILL.md 内容】");
    assertThat(prompt.systemMessage()).contains("当前时间：" + LocalDate.now());
    // ③ 会话历史
    assertThat(prompt.messages()).hasSize(1);
    assertThat(prompt.messages().get(0)).isInstanceOf(Session.UserMessage.class);
    // ④ 可用工具列表
    assertThat(prompt.availableTools()).containsExactly(httpGet);
  }

  @Test
  @DisplayName("历史超N轮被截断")
  void truncatesHistory_whenExceedsMaxTurns() {
    when(contextLoader.loadSystemPrompt(any())).thenReturn("");
    Session session = new Session("s-1", "test", "cli", "u-1");
    for (int i = 0; i < 25; i++) {
      session.appendUserMessage("user-" + i);
      session.appendAssistant("assistant-" + i, List.of());
    }
    Profile profile = profile("role", List.of(), List.of(), 20);

    Prompt prompt = promptBuilder.build(session, profile);

    assertThat(prompt.messages()).hasSize(20);
    assertThat(prompt.messages().get(19))
        .isEqualTo(new Session.AssistantMessage("assistant-24", List.of()));
    assertThat(prompt.userMessage()).isEqualTo("user-24");
  }

  @Test
  @DisplayName("system prompt 末尾附当前日期时间")
  void systemPromptEndsWithCurrentDateTime() {
    when(contextLoader.loadSystemPrompt(any())).thenReturn("【Bootstrap 内容】");
    Session session = new Session("s-1", "test", "cli", "u-1");
    session.appendUserMessage("hi");

    Prompt prompt = promptBuilder.build(session, profile("role", List.of(), List.of(), 20));

    // 最后一行是日期时间行，且含今天的日期——模型自己不知道今天几号，全靠这一行
    String systemMessage = prompt.systemMessage().trim();
    String lastLine = systemMessage.split("\\R")[systemMessage.split("\\R").length - 1];
    assertThat(lastLine).startsWith("当前时间：").contains(LocalDate.now().toString());
  }

  @Test
  @DisplayName("空工具与空历史不崩溃")
  void emptyToolsAndEmptyHistory_doNotCrash() {
    when(contextLoader.loadSystemPrompt(any())).thenReturn("");
    Session session = new Session("s-1", "test", "cli", "u-1");

    Prompt prompt = promptBuilder.build(session, profile("role", List.of(), List.of(), 20));

    assertThat(prompt.availableTools()).isEmpty();
    assertThat(prompt.messages()).isEmpty();
    assertThat(prompt.systemMessage()).isNotBlank();
  }

  private static Profile profile(
      String rolePrompt, List<String> tools, List<String> skills, int maxHistoryTurns) {
    return new Profile(
        "test",
        "desc",
        new Profile.Identity("agent", rolePrompt),
        null,
        tools,
        skills,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Settings(10, maxHistoryTurns));
  }
}
