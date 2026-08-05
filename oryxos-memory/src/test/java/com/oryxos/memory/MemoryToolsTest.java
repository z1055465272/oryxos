package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.MemoryScope;
import com.oryxos.core.MemoryService;
import com.oryxos.core.ToolResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MemoryTools 回归测试：scope 缺省写归档（坑三）、检索未命中返回提示不抛异常（坑四）. */
class MemoryToolsTest {

  private MemoryService memoryService;
  private MemoryTools memoryTools;

  @BeforeEach
  void setUp() {
    memoryService = mock(MemoryService.class);
    memoryTools = new MemoryTools(memoryService);
  }

  @Test
  @DisplayName("scope 缺省写归档")
  void saveMemory_defaultsToArchival() {
    memoryTools.saveMemory("值得记的事", null);

    verify(memoryService).remember("值得记的事", MemoryScope.ARCHIVAL);
  }

  @Test
  @DisplayName("scope 非法值按归档处理")
  void saveMemory_unknownScopeFallsBackToArchival() {
    memoryTools.saveMemory("值得记的事", "unknown-scope");

    verify(memoryService).remember("值得记的事", MemoryScope.ARCHIVAL);
  }

  @Test
  @DisplayName("save_memory 返回成功结果")
  void saveMemory_returnsSuccess() {
    ToolResult result = memoryTools.saveMemory("值得记的事", "core");

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("已记住");
  }

  @Test
  @DisplayName("关键词未命中返回没有找到相关记忆而不是抛异常")
  void recallMemory_returnsNoResultMessage_whenNoMatch() {
    when(memoryService.recall("xyz")).thenReturn(List.of());

    ToolResult result = memoryTools.recallMemory("xyz");

    assertThat(result.success()).isTrue();
    assertThat(result.content()).contains("没有找到相关记忆");
  }

  @Test
  @DisplayName("检索命中返回匹配条目拼接")
  void recallMemory_returnsHitsJoined() {
    when(memoryService.recall("Java"))
        .thenReturn(List.of("- [2026-08-05] 偏好 Java", "- [2026-08-04] Java 项目"));

    ToolResult result = memoryTools.recallMemory("Java");

    assertThat(result.success()).isTrue();
    assertThat(result.content()).contains("偏好 Java").contains("Java 项目");
  }
}
