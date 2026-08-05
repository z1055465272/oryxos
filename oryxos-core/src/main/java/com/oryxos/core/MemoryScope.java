package com.oryxos.core;

/**
 * 长期记忆写入的区块归类：核心记忆（永远完整、不截断、不换出）还是归档记忆（超阈值截断、可检索）.
 *
 * <p>写入哪个区块由调用方经 {@code scope} 显式指定（课件坑三：系统不猜），缺省 {@link #ARCHIVAL}.
 */
public enum MemoryScope {
  CORE,
  ARCHIVAL
}
