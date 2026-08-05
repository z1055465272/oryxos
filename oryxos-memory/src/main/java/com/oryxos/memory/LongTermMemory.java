package com.oryxos.memory;

import com.oryxos.core.MemoryScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * 长期记忆的文件式实现：读写 {@code MEMORY.md}，按两个二级标题分区——核心记忆（永远完整、不截断、不换出）与归档记忆（超阈值截断、可检索）.
 *
 * <p>对应课件四个坑：①不缓存——每次 {@code load()} 重新 {@code Files.readString}，Agent 调完 save_memory
 * 下一轮立刻看到；②截断只裁归档区 ——{@code truncateIfNeeded} 只接收归档段文本，物理上不可能动到核心区；③写入核心还是归档由 {@code scope}
 * 显式指定（系统不猜）；④检索用 {@code String.contains} 朴素包含匹配，只在归档区做.
 */
public class LongTermMemory {

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVE_HEADER = "## 归档记忆";

  /** 归档区阈值：只作用在归档区，核心区不受限（课件坑二）. */
  public static final int MAX_ARCHIVE_CHARS = 4000;

  private final Path memoryFile;

  public LongTermMemory(Path memoryFile) {
    this.memoryFile = memoryFile;
  }

  /** 按 scope 追加一条记忆到对应区块（scope 缺省由调用方已归一为 ARCHIVAL）. */
  public void append(String content, MemoryScope scope) {
    String header = scope == MemoryScope.CORE ? CORE_HEADER : ARCHIVE_HEADER;
    String entry = "\n- [" + LocalDate.now() + "] " + content;
    writeIntoSection(header, entry);
  }

  /** 每次重新读文件不缓存（坑一）：核心区完整返回，归档区可能截断（坑二）. */
  public String load() {
    String raw = readAll();
    String core = extractSection(raw, CORE_HEADER);
    String archive = truncateIfNeeded(extractSection(raw, ARCHIVE_HEADER));
    return joinSections(core, archive);
  }

  /** 只在归档区做关键词包含匹配（坑四：核心区永远在场，不需要检索）. */
  public java.util.List<String> recallByKeyword(String keyword) {
    String archive = extractSection(readAll(), ARCHIVE_HEADER);
    return archive.lines().filter(line -> line.contains(keyword)).map(String::strip).toList();
  }

  /** 截断只裁归档区：段长超阈值保留最近 {@value #MAX_ARCHIVE_CHARS} 字，核心区经此方法之外根本不会被传入（坑二）. */
  static String truncateIfNeeded(String archiveSection) {
    if (archiveSection.length() <= MAX_ARCHIVE_CHARS) {
      return archiveSection;
    }
    return archiveSection.substring(archiveSection.length() - MAX_ARCHIVE_CHARS);
  }

  /** 找到 header 所在区块、把 entry 追加到该区块末尾；区块不存在则新建. */
  private void writeIntoSection(String header, String entry) {
    writeAll(insertIntoSection(readAll(), header, entry));
  }

  /** 在 raw 里定位 header 区块并追加 entry；header 不存在则创建新区块（空文件或追加到末尾）. */
  private static String insertIntoSection(String raw, String header, String entry) {
    int headerIdx = raw.indexOf(header);
    if (headerIdx == -1) {
      String firstEntry = entry.startsWith("\n") ? entry.substring(1) : entry;
      String newSection = header + "\n\n" + firstEntry + "\n";
      if (raw.isEmpty()) {
        return newSection;
      }
      return raw.stripTrailing() + "\n\n" + newSection;
    }
    int sectionEnd = raw.indexOf("\n## ", headerIdx + header.length());
    if (sectionEnd == -1) {
      return raw + entry + "\n";
    }
    return raw.substring(0, sectionEnd) + entry + "\n" + raw.substring(sectionEnd);
  }

  /** 取 header 区块文本（含 header），到下一个二级标题或文件末尾为止；header 缺失返回空串. */
  private static String extractSection(String raw, String header) {
    int start = raw.indexOf(header);
    if (start == -1) {
      return "";
    }
    int end = raw.indexOf("\n## ", start + header.length());
    if (end == -1) {
      return raw.substring(start);
    }
    return raw.substring(start, end);
  }

  /** 拼装核心 + 归档两段；都空时返回空串（首次使用文件不存在）. */
  private static String joinSections(String core, String archive) {
    if (core.isEmpty() && archive.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (!core.isEmpty()) {
      sb.append(core);
    }
    if (!archive.isEmpty()) {
      if (sb.length() > 0) {
        sb.append("\n");
      }
      sb.append(archive);
    }
    return sb.toString();
  }

  /** 读文件全部内容；文件不存在返回空串（首次使用）. IO 异常上抛（不吞）. */
  private String readAll() {
    if (!Files.exists(memoryFile)) {
      return "";
    }
    try {
      return Files.readString(memoryFile, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("MEMORY.md 读取失败: " + memoryFile, e);
    }
  }

  /** 写文件（自动建父目录）. IO 异常上抛（不吞）. */
  private void writeAll(String content) {
    try {
      Path parent = memoryFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(memoryFile, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("MEMORY.md 写入失败: " + memoryFile, e);
    }
  }
}
