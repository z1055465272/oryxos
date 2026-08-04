package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotifyChannelAdapterRouterTest {

  private static NotifyTarget targetFor(String url) {
    return new NotifyTarget("webhook", Map.of("url", url));
  }

  @Test
  @DisplayName("按 URL 特征命中第一个支持的适配器")
  void routesToFirstSupportingAdapter() {
    NotifyChannelAdapter wecom = mock(NotifyChannelAdapter.class);
    NotifyChannelAdapter feishu = mock(NotifyChannelAdapter.class);
    NotifyChannelAdapter webhook = mock(NotifyChannelAdapter.class);
    String otherUrl = "https://example.com/hook";
    when(webhook.supports(otherUrl)).thenReturn(true);

    NotifyChannelAdapterRouter router =
        new NotifyChannelAdapterRouter(List.of(wecom, feishu, webhook));
    router.send(targetFor(otherUrl), "msg");

    verify(wecom).supports(otherUrl);
    verify(wecom, never()).send(any(), any());
    verify(feishu).supports(otherUrl);
    verify(feishu, never()).send(any(), any());
    verify(webhook).send(any(), any());
  }

  @Test
  @DisplayName("未知 URL 落到兜底适配器")
  void fallsBackToLastAdapterWhenNoFeatureMatches() {
    NotifyChannelAdapter wecom = mock(NotifyChannelAdapter.class);
    NotifyChannelAdapter fallback = mock(NotifyChannelAdapter.class);
    when(fallback.supports(anyString())).thenReturn(true);

    NotifyChannelAdapterRouter router = new NotifyChannelAdapterRouter(List.of(wecom, fallback));
    router.send(targetFor("https://example.com/hook"), "msg");

    verify(fallback).send(any(), any());
  }

  @Test
  @DisplayName("所有适配器都不支持时抛明确异常，不静默")
  void throwsWhenNoAdapterSupportsUrl() {
    NotifyChannelAdapter wecom = mock(NotifyChannelAdapter.class);
    NotifyChannelAdapter feishu = mock(NotifyChannelAdapter.class);

    NotifyChannelAdapterRouter router = new NotifyChannelAdapterRouter(List.of(wecom, feishu));
    assertThrows(
        IllegalArgumentException.class,
        () -> router.send(targetFor("https://x.example/hook"), "msg"));
  }

  @Test
  @DisplayName("适配器按传入顺序依次匹配，先命中者负责发送")
  void firstMatchWinsAndIsTheSender() {
    NotifyChannelAdapter first = mock(NotifyChannelAdapter.class);
    NotifyChannelAdapter second = mock(NotifyChannelAdapter.class);
    String url = "https://hooks.example.com/a";
    when(first.supports(anyString())).thenReturn(true);
    NotifyTarget target = targetFor(url);

    NotifyChannelAdapterRouter router = new NotifyChannelAdapterRouter(List.of(first, second));
    router.send(target, "msg");

    verify(first).send(target, "msg");
    verify(second, never()).send(any(), any());
    assertThat(target.config().get("url")).isEqualTo(url);
  }
}
