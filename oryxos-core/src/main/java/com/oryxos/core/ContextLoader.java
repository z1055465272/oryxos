package com.oryxos.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上下文加载器：按 Profile 的 bootstrap/skills 字段读取文件拼成文本.
 *
 * <p>无缓存——每次组装 prompt 都重新读文件（改完立即生效，避免"人格悄悄丢了"这种静默软故障）。 Bootstrap 缺失 WARN 跳过不阻断；Skill
 * 引用缺失必须报错（宪法原则四 v1.2.0：SKILL.md 正文预载）.
 */
public class ContextLoader {

  private static final Logger log = LoggerFactory.getLogger(ContextLoader.class);

  private final Path workspaceDir;

  public ContextLoader(Path workspaceDir) {
    this.workspaceDir = workspaceDir;
  }

  /**
   * 加载当前 Profile 的上下文：Bootstrap 文件 + 绑定的 SKILL.md 正文. 角色设定由 PromptBuilder 从 Profile.identity.prompt
   * 拼入，不在此处.
   *
   * @return 拼接文本（无 bootstrap/skills 时为 ""）
   * @throws IllegalStateException 显式引用的 Skill 缺失
   */
  public String loadSystemPrompt(Profile profile) {
    StringBuilder sb = new StringBuilder();
    for (String bootstrap : profile.bootstrap()) {
      loadOptionalFile(bootstrap, sb);
    }
    for (String skill : profile.skills()) {
      loadRequiredSkill(skill, sb);
    }
    return sb.toString();
  }

  /** 读可选文件（Bootstrap）：缺失或读取失败 WARN 并跳过，不阻断整体组装. */
  private void loadOptionalFile(String name, StringBuilder sb) {
    Path path = workspaceDir.resolve(name);
    try {
      if (!Files.exists(path)) {
        log.warn("Bootstrap 文件缺失（跳过）: {}", path);
        return;
      }
      append(path, sb);
    } catch (IOException e) {
      log.warn("Bootstrap 文件读取失败（跳过）: {} - {}", path, e.getMessage());
    }
  }

  /** 读必选 Skill（SKILL.md 正文预载）：缺失必须报错——静默跳过会造成最难查的软故障. */
  private void loadRequiredSkill(String skill, StringBuilder sb) {
    Path path = workspaceDir.resolve("skills").resolve(skill).resolve("SKILL.md");
    if (!Files.exists(path)) {
      throw new IllegalStateException("Skill 引用缺失: " + path);
    }
    try {
      append(path, sb);
    } catch (IOException e) {
      throw new IllegalStateException("Skill 读取失败: " + path, e);
    }
  }

  private void append(Path path, StringBuilder sb) throws IOException {
    String content = Files.readString(path);
    if (!content.isBlank()) {
      if (sb.length() > 0) {
        sb.append("\n\n");
      }
      sb.append(content);
    }
  }
}
