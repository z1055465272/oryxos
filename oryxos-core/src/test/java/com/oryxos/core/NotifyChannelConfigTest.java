package com.oryxos.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NotifyChannelConfigTest {

  @Test
  void createsWithValidTypeAndUrl() {
    NotifyChannelConfig config = new NotifyChannelConfig("webhook", "https://example.com/hook");
    assertEquals("webhook", config.type());
    assertEquals("https://example.com/hook", config.url());
  }

  @Test
  void emptyTypeThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new NotifyChannelConfig("", "https://example.com/hook"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new NotifyChannelConfig(null, "https://example.com/hook"));
  }

  @Test
  void emptyUrlThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> new NotifyChannelConfig("webhook", ""));
    assertThrows(IllegalArgumentException.class, () -> new NotifyChannelConfig("webhook", null));
  }
}
