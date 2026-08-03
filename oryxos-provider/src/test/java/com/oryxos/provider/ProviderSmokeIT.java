package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.oryxos.core.Profile;
import com.oryxos.core.Profile.ProviderRef;
import com.oryxos.core.Prompt;
import com.oryxos.storage.LlmCallRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

@Tag("integration")
class ProviderSmokeIT {

  @Test
  @DisplayName("真调一次模型，拿到非空响应")
  @Disabled(
      "Requires DEEPSEEK_API_KEY env var and network access — "
          + "run manually with: DEEPSEEK_API_KEY=xxx mvn test -Dgroups=integration -pl oryxos-provider")
  void realCall_returnsNonEmptyResponse() {
    String apiKey = System.getenv("DEEPSEEK_API_KEY");
    assertThat(apiKey)
        .as("DEEPSEEK_API_KEY environment variable must be set for integration smoke test")
        .isNotBlank();

    var openAiApi =
        OpenAiApi.builder().baseUrl("https://api.deepseek.com/v1").apiKey(apiKey).build();
    var chatModel =
        OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(OpenAiChatOptions.builder().model("deepseek-chat").build())
            .build();

    var adapter = new ToolSchemaAdapter();
    var noopRepo = mock(LlmCallRepository.class);

    var service = new DefaultProviderService(Map.of("deepseek", chatModel), adapter, noopRepo);

    Profile profile =
        new Profile(
            "smoke-test",
            "smoke",
            new Profile.Identity("smoke", "You are helpful."),
            new ProviderRef("deepseek", "deepseek-chat", 0.7),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new Profile.Settings(10, 20));

    ChatResponse response = service.chat("smoke-1", profile, new Prompt("Say hello in one word."));

    assertThat(response).isNotNull();
    assertThat(response.getResults()).isNotEmpty();
    String content = response.getResults().get(0).getOutput().getText();
    assertThat(content).isNotBlank();

    System.out.println("=== ProviderSmokeIT: LLM response ===");
    System.out.println(content);
    System.out.println("=== PASS: real call returned non-empty response ===");
  }
}
