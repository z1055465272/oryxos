package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.MemoryScope;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LongTermMemory 回归测试：对应课件"验收 harness"，钉死四个坑——无缓存、截断只裁归档、scope 路由、检索只搜归档区.
 *
 * <p>课件写出代码的两个最值钱测试（截断只裁归档区_核心记忆一字不能少、写入后立刻可读_不允许有缓存）在此原样落地，方法名译英文、课件原文进 {@code @DisplayName}.
 */
class LongTermMemoryTest {

  @TempDir Path tempDir;

  private LongTermMemory memory;

  @BeforeEach
  void setUp() {
    memory = new LongTermMemory(tempDir.resolve("MEMORY.md"));
  }

  @Test
  @DisplayName("截断只裁归档区_核心记忆一字不能少")
  void truncatesArchiveOnly_keepsCoreIntact() {
    memory.append("用户叫小王，偏好用 Java", MemoryScope.CORE);
    for (int i = 0; i < 500; i++) {
      memory.append("归档流水 " + i, MemoryScope.ARCHIVAL);
    }

    String loaded = memory.load();

    assertThat(loaded).contains("用户叫小王，偏好用 Java");
    assertThat(loaded).doesNotContain("归档流水 0");
    assertThat(loaded).contains("归档流水 499");
  }

  @Test
  @DisplayName("写入后立刻可读_不允许有缓存")
  void writeThenReadImmediately_noCache() {
    memory.append("刚记的事", MemoryScope.ARCHIVAL);

    assertThat(memory.load()).contains("刚记的事");
    assertThat(memory.recallByKeyword("刚记的事")).isNotEmpty();
  }

  @Test
  @DisplayName("scope 路由到正确区块")
  void routesScopeToCorrectSection() {
    memory.append("核心的一条", MemoryScope.CORE);
    memory.append("归档的一条", MemoryScope.ARCHIVAL);

    String raw = memory.load();
    assertThat(raw).contains("## 核心记忆").contains("## 归档记忆");
    assertThat(section(raw, "核心记忆")).contains("核心的一条").doesNotContain("归档的一条");
    assertThat(section(raw, "归档记忆")).contains("归档的一条").doesNotContain("核心的一条");
  }

  @Test
  @DisplayName("recall 只搜归档区，核心区不参与检索")
  void recallSearchesArchiveOnly() {
    memory.append("用户偏好 Java", MemoryScope.CORE);
    memory.append("昨天归档了 Java 项目", MemoryScope.ARCHIVAL);

    assertThat(memory.recallByKeyword("Java")).hasSize(1);
    assertThat(memory.recallByKeyword("Java").get(0)).contains("昨天归档了 Java 项目");
  }

  @Test
  @DisplayName("文件不存在时读取返回空，不崩溃")
  void missingFile_loadsEmpty() {
    assertThat(memory.load()).isEmpty();
    assertThat(memory.recallByKeyword("anything")).isEmpty();
  }

  private static String section(String raw, String name) {
    int start = raw.indexOf("## " + name);
    int end = raw.indexOf("## ", start + 1);
    String section = end == -1 ? raw.substring(start) : raw.substring(start, end);
    return section;
  }
}
