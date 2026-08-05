package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.MemoryScope;
import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DefaultMemoryService 回归测试：buildContext 返回核心记忆 + 会话历史的组合，归档区不整体注入（超阈值截断）. */
class MemoryServiceTest {

  @TempDir Path tempDir;

  private DefaultMemoryService memoryService;
  private Session session;
  private Profile profile;

  @BeforeEach
  void setUp() {
    memoryService = new DefaultMemoryService(new LongTermMemory(tempDir.resolve("MEMORY.md")));
    session = new Session("s-1", "test", "cli", "u-1");
    profile =
        new Profile(
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
            new Profile.Settings(10, 20));
  }

  @Test
  @DisplayName("buildContext 返回核心记忆 + 会话历史的组合，归档区不整体注入")
  void buildContext_returnsCoreAndHistory_archiveNotFullyInjected() {
    memoryService.remember("用户叫小王，偏好用 Java", MemoryScope.CORE);
    for (int i = 0; i < 500; i++) {
      memoryService.remember("归档流水 " + i, MemoryScope.ARCHIVAL);
    }
    session.appendUserMessage("今天想查天气");

    String context = memoryService.buildContext(session, profile);

    assertThat(context).contains("用户叫小王，偏好用 Java");
    assertThat(context).contains("今天想查天气");
    assertThat(context).doesNotContain("归档流水 0");
    assertThat(context).contains("归档流水 499");
  }

  @Test
  @DisplayName("buildContext 无记忆无历史时返回空串")
  void buildContext_returnsEmpty_whenNothingToShow() {
    assertThat(memoryService.buildContext(session, profile)).isEmpty();
  }
}
