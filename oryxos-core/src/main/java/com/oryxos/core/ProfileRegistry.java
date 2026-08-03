package com.oryxos.core;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profile 内存索引，按 name 提供快速查找.
 *
 * <p>启动时通过 {@link ProfileLoader} 批量注册，29 节补运行时注册方法.
 */
public class ProfileRegistry {

  private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

  /** 注册一个 Profile（启动扫描或运行时注册共用此入口）. */
  public void register(Profile profile) {
    profiles.put(profile.name(), profile);
  }

  /** 按 name 查找，返回 {@code Optional.empty()} 表示未注册. */
  public Optional<Profile> get(String name) {
    return Optional.ofNullable(profiles.get(name));
  }

  /** 返回全部已注册 Profile 的不可修改快照. */
  public Collection<Profile> listAll() {
    return Collections.unmodifiableCollection(profiles.values());
  }
}
