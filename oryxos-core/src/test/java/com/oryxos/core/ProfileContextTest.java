package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProfileContextTest {

  private final ProfileContext profileContext = new ProfileContext();

  @AfterEach
  void tearDown() {
    ProfileContext.clear();
  }

  @Test
  void resolveNotifyChannelReturnsFirstWhenChannelNull() {
    Profile profile =
        profileWithChannels(
            new NotifyChannelConfig("webhook-ops", "https://ops.example.com"),
            new NotifyChannelConfig("webhook-dev", "https://dev.example.com"));
    ProfileContext.set(profile);

    NotifyChannelConfig result = profileContext.resolveNotifyChannel(null);
    assertThat(result.type()).isEqualTo("webhook-ops");
  }

  @Test
  void resolveNotifyChannelReturnsFirstWhenChannelBlank() {
    Profile profile =
        profileWithChannels(new NotifyChannelConfig("webhook-ops", "https://ops.example.com"));
    ProfileContext.set(profile);

    NotifyChannelConfig result = profileContext.resolveNotifyChannel("  ");
    assertThat(result.type()).isEqualTo("webhook-ops");
  }

  @Test
  void resolveNotifyChannelMatchesByType() {
    Profile profile =
        profileWithChannels(
            new NotifyChannelConfig("webhook-ops", "https://ops.example.com"),
            new NotifyChannelConfig("webhook-dev", "https://dev.example.com"));
    ProfileContext.set(profile);

    NotifyChannelConfig result = profileContext.resolveNotifyChannel("webhook-dev");
    assertThat(result.type()).isEqualTo("webhook-dev");
    assertThat(result.url()).isEqualTo("https://dev.example.com");
  }

  @Test
  void emptyNotifyChannelsThrowsException() {
    Profile profile = profileWithChannels();
    ProfileContext.set(profile);

    assertThrows(IllegalStateException.class, () -> profileContext.resolveNotifyChannel(null));
  }

  @Test
  void channelNotFoundThrowsException() {
    Profile profile =
        profileWithChannels(new NotifyChannelConfig("webhook-ops", "https://ops.example.com"));
    ProfileContext.set(profile);

    assertThrows(
        IllegalArgumentException.class, () -> profileContext.resolveNotifyChannel("nonexistent"));
  }

  private Profile profileWithChannels(NotifyChannelConfig... channels) {
    return new Profile(
        "test-agent",
        null,
        new Profile.Identity("test", "prompt"),
        new Profile.ProviderRef("deepseek", null, null),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(channels),
        List.of(),
        List.of(),
        new Profile.Settings(null, null));
  }
}
