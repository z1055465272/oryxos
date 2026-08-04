package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.oryxos.core.NotifyChannelConfig;
import com.oryxos.core.Profile;
import com.oryxos.core.ProfileContext;
import com.oryxos.core.ToolResult;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotifyToolsTest {

  @Mock private Sandbox sandbox;

  @Mock private NotifyChannelAdapter adapter;

  private ProfileContext profileContext;
  private NotifyTools notifyTools;

  @BeforeEach
  void setUp() {
    profileContext = new ProfileContext();
    notifyTools = new NotifyTools(sandbox, adapter, profileContext);
  }

  @AfterEach
  void tearDown() {
    ProfileContext.clear();
  }

  @Test
  @DisplayName("notify_channels 未配置时明确报错，不是静默失败")
  void notifyChannelsNotConfiguredReturnsError() {
    Profile profile = profileWithChannels();
    ProfileContext.set(profile);

    assertThrows(IllegalStateException.class, () -> notifyTools.notify("hello", null));
    verifyNoInteractions(adapter);
  }

  @Test
  @DisplayName("channel 参数缺省时取第一个渠道")
  void channelParameterDefaultToFirstChannel() {
    Profile profile =
        profileWithChannels(
            new NotifyChannelConfig("webhook-ops", "https://ops.example.com"),
            new NotifyChannelConfig("webhook-dev", "https://dev.example.com"));
    ProfileContext.set(profile);

    notifyTools.notify("hello", null);

    verify(adapter)
        .send(
            argThat(
                target ->
                    target.channelType().equals("webhook-ops")
                        && target.config().get("url").equals("https://ops.example.com")),
            eq("hello"));
  }

  @Test
  @DisplayName("发送前必须先过白名单校验——enforce 先于 send 被调用")
  void enforceCalledBeforeSendInOrder() {
    Profile profile =
        profileWithChannels(new NotifyChannelConfig("webhook", "https://hooks.example.com/ops"));
    ProfileContext.set(profile);

    notifyTools.notify("hello", "webhook");

    InOrder inOrder = inOrder(sandbox, adapter);
    inOrder.verify(sandbox).enforce(argThat(a -> a.type() == ActionType.HTTP_REQUEST));
    inOrder.verify(adapter).send(any(), eq("hello"));
  }

  @Test
  @DisplayName("notify 成功返回 ToolResult.ok")
  void notifyReturnsOkOnSuccess() {
    Profile profile =
        profileWithChannels(new NotifyChannelConfig("webhook", "https://hooks.example.com/ops"));
    ProfileContext.set(profile);

    ToolResult result = notifyTools.notify("hello", null);

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("已推送");
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
