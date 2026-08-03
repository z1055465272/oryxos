package com.oryxos.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * 启动时扫描 .oryxos/profiles/ 下所有 YAML，逐个解析为 {@link Profile} 并做校验.
 *
 * <p>坏 YAML 记错误日志跳过、不阻断其余加载；校验失败的 Profile 记错误日志.
 */
public class ProfileLoader {

  private static final Logger log = LoggerFactory.getLogger(ProfileLoader.class);
  private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{(.+?)}");

  private final Path profilesDir;
  private final Set<String> validProviderNames;
  private final Yaml yaml;

  /** 创建 ProfileLoader 实例. */
  public ProfileLoader(Path profilesDir, Set<String> validProviderNames) {
    this.profilesDir = profilesDir;
    this.validProviderNames = Set.copyOf(validProviderNames);
    this.yaml = new Yaml();
  }

  /** 扫描并加载所有合法 Profile，失败的跳过不阻断其余. */
  public List<Profile> loadAll() {
    if (!Files.isDirectory(profilesDir)) {
      log.warn("Profiles directory does not exist: {}", profilesDir);
      return Collections.emptyList();
    }

    List<Profile> profiles = new ArrayList<>();
    try (Stream<Path> files = Files.list(profilesDir)) {
      files
          .filter(
              p ->
                  Files.isRegularFile(p)
                      && p.getFileName() != null
                      && (p.getFileName().toString().endsWith(".yaml")
                          || p.getFileName().toString().endsWith(".yml")))
          .forEach(
              file -> {
                try {
                  Profile profile = loadOne(file);
                  if (profile != null) {
                    profiles.add(profile);
                  }
                } catch (Exception e) {
                  log.error("Failed to load profile from {}", file.getFileName(), e);
                }
              });
    } catch (IOException e) {
      log.error("Failed to list profiles directory {}", profilesDir, e);
    }
    return profiles;
  }

  @SuppressWarnings("unchecked")
  private Profile loadOne(Path file) {
    Map<String, Object> raw;
    try (InputStream in = Files.newInputStream(file)) {
      raw = (Map<String, Object>) yaml.load(in);
    } catch (IOException e) {
      log.error("Failed to read profile file: {}", file.getFileName(), e);
      return null;
    }

    if (raw == null) {
      log.error("Empty YAML in profile: {}", file.getFileName());
      return null;
    }

    String name = getString(raw, "name");
    if (name == null || name.isBlank()) {
      log.error("Profile missing required field 'name' in {}", file.getFileName());
      return null;
    }

    // Validate provider reference
    Map<String, Object> providerMap = (Map<String, Object>) raw.get("provider");
    if (providerMap == null) {
      log.error("Profile '{}' missing 'provider' section in {}", name, file.getFileName());
      return null;
    }
    String providerName = getString(providerMap, "name");
    if (providerName == null || !validProviderNames.contains(providerName)) {
      log.error(
          "Profile '{}' references unknown provider '{}'. Available providers: {}",
          name,
          providerName,
          validProviderNames);
      return null;
    }

    return parseProfile(name, raw);
  }

  @SuppressWarnings("unchecked")
  private Profile parseProfile(String name, Map<String, Object> raw) {
    String description = getString(raw, "description");

    // identity
    Map<String, Object> idMap = (Map<String, Object>) raw.get("identity");
    Profile.Identity identity =
        new Profile.Identity(
            idMap != null ? getString(idMap, "agent_name") : null,
            idMap != null ? getString(idMap, "prompt") : null);

    // provider
    Map<String, Object> provMap = (Map<String, Object>) raw.get("provider");
    Profile.ProviderRef providerRef =
        new Profile.ProviderRef(
            getString(provMap, "name"),
            getString(provMap, "model"),
            getDouble(provMap, "temperature"));

    // tools
    List<String> tools = getStringList(raw, "tools");

    // skills
    List<String> skills = getStringList(raw, "skills");

    // mcp_servers
    List<String> mcpServers = getStringList(raw, "mcp_servers");

    // channels
    List<Profile.ChannelRef> channels = parseChannels(raw);

    // notify_channels
    List<String> notifyChannels = getStringList(raw, "notify_channels");

    // schedules
    List<Profile.ScheduleConfig> schedules = parseSchedules(raw);

    // bootstrap
    List<String> bootstrap = getStringList(raw, "bootstrap");

    // settings
    Map<String, Object> setMap = (Map<String, Object>) raw.get("settings");
    Profile.Settings settings =
        new Profile.Settings(
            setMap != null ? getInt(setMap, "max_iterations") : null,
            setMap != null ? getInt(setMap, "max_history_turns") : null);

    return new Profile(
        name,
        description,
        identity,
        providerRef,
        tools,
        skills,
        mcpServers,
        channels,
        notifyChannels,
        schedules,
        bootstrap,
        settings);
  }

  @SuppressWarnings("unchecked")
  private List<Profile.ChannelRef> parseChannels(Map<String, Object> raw) {
    List<Map<String, Object>> channelList = (List<Map<String, Object>>) raw.get("channels");
    if (channelList == null) {
      return Collections.emptyList();
    }
    return channelList.stream()
        .map(
            ch -> {
              String chName = getString(ch, "name");
              Map<String, String> config =
                  (Map<String, String>)
                      (Map<?, ?>) ch.getOrDefault("config", Collections.emptyMap());
              return new Profile.ChannelRef(chName, config);
            })
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<Profile.ScheduleConfig> parseSchedules(Map<String, Object> raw) {
    List<Map<String, Object>> schedList = (List<Map<String, Object>>) raw.get("schedules");
    if (schedList == null) {
      return Collections.emptyList();
    }
    return schedList.stream()
        .map(
            s -> {
              String id = getString(s, "id");
              String cron = getString(s, "cron");
              String zone = getString(s, "zone");
              String message = getString(s, "message");
              Map<String, String> config =
                  (Map<String, String>)
                      (Map<?, ?>) s.getOrDefault("config", Collections.emptyMap());
              return new Profile.ScheduleConfig(id, cron, zone, message, config);
            })
        .toList();
  }

  /** 解析字符串中的 ${ENV_VAR} 占位符为环境变量值. */
  static String resolveEnvVars(String value) {
    if (value == null) {
      return null;
    }
    Matcher matcher = ENV_PATTERN.matcher(value);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String varName = matcher.group(1);
      String envValue = System.getenv(varName);
      if (envValue == null) {
        log.error("Environment variable '{}' referenced but not set", varName);
        matcher.appendReplacement(sb, "");
      } else {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  // -- YAML value helpers --

  private static String getString(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val instanceof String s) {
      return resolveEnvVars(s);
    }
    return val != null ? val.toString() : null;
  }

  private static Double getDouble(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val instanceof Number n) {
      return n.doubleValue();
    }
    return null;
  }

  private static Integer getInt(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val instanceof Number n) {
      return n.intValue();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static List<String> getStringList(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val instanceof List<?> list) {
      return list.stream()
          .map(item -> item instanceof String s ? resolveEnvVars(s) : String.valueOf(item))
          .toList();
    }
    return Collections.emptyList();
  }
}
