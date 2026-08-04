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
    assertEquals(null, config.secret());
  }

  @Test
  void createsWithSecret() {
    NotifyChannelConfig config =
        new NotifyChannelConfig("dingtalk", "https://oapi.dingtalk.com/hook", "SEC123");
    assertEquals("dingtalk", config.type());
    assertEquals("https://oapi.dingtalk.com/hook", config.url());
    assertEquals("SEC123", config.secret());
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
