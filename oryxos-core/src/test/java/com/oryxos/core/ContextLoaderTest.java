package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextLoaderTest {

  @TempDir Path workspaceDir;

  private ContextLoader loader;

  @BeforeEach
  void setUp() {
    loader = new ContextLoader(workspaceDir);
  }

  @Test
  @DisplayName("改文件后下一次build立即读到新内容_无缓存")
  void readsFreshContent_afterFileChange() throws IOException {
    Path bootstrap = workspaceDir.resolve("AGENTS.md");
    Files.writeString(bootstrap, "内容A");
    Profile profile = profileWithBootstrap("AGENTS.md");

    assertThat(loader.loadSystemPrompt(profile)).contains("内容A");

    Files.writeString(bootstrap, "内容B");
    assertThat(loader.loadSystemPrompt(profile)).contains("内容B").doesNotContain("内容A");
  }

  @Test
  @DisplayName("Bootstrap缺失WARN不阻断")
  void missingBootstrap_logsWarn_butContinues() throws IOException {
    Files.writeString(workspaceDir.resolve("AGENTS.md"), "存在的内容");
    Profile profile = profileWithBootstrap("不存在.md", "AGENTS.md");

    String prompt = loader.loadSystemPrompt(profile);

    // 缺失项跳过，其余照常组装
    assertThat(prompt).contains("存在的内容");
  }

  @Test
  @DisplayName("Skill引用缺失_报错")
  void missingSkill_throws() {
    Profile profile = profileWithSkills("missing-skill");

    assertThrows(IllegalStateException.class, () -> loader.loadSystemPrompt(profile));
  }

  @Test
  @DisplayName("存在的SKILL.md正文被预载拼入")
  void includesSkillBody_whenPresent() throws IOException {
    Path skillDir = workspaceDir.resolve("skills").resolve("pr-digest");
    Files.createDirectories(skillDir);
    Files.writeString(skillDir.resolve("SKILL.md"), "这是 PR digest 技能正文");
    Profile profile = profileWithSkills("pr-digest");

    assertThat(loader.loadSystemPrompt(profile)).contains("这是 PR digest 技能正文");
  }

  private static Profile profileWithBootstrap(String... bootstraps) {
    return new Profile(
        "test",
        "desc",
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(bootstraps),
        null);
  }

  private static Profile profileWithSkills(String... skills) {
    return new Profile(
        "test",
        "desc",
        null,
        null,
        List.of(),
        List.of(skills),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);
  }
}
