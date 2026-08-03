package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

  private ProfileRegistry profileRegistry;
  private ReActLoop reActLoop;
  private SessionManager sessionManager;
  private AgentService agentService;
  private Profile profile;

  @BeforeEach
  void setUp() {
    profileRegistry = new ProfileRegistry();
    reActLoop = mock(ReActLoop.class);
    sessionManager = mock(SessionManager.class);
    agentService = new AgentService(profileRegistry, reActLoop, sessionManager);
    profile = profile();
    profileRegistry.register(profile);
  }

  @AfterEach
  void tearDown() {
    ProfileContext.clear();
  }

  @Test
  @DisplayName("处理期间ProfileContext可取到当前Profile_结束后清空")
  void setsProfileContext_duringProcessing() {
    doAnswer(
            invocation -> {
              // 循环执行期间工具/服务能取到"当前是哪个 Agent"
              assertThat(ProfileContext.current()).isEqualTo(profile);
              return "ok";
            })
        .when(reActLoop)
        .run(any(), any(), any());

    agentService.process(newSession(), "hi");

    assertNull(ProfileContext.current());
  }

  @Test
  @DisplayName("处理中抛异常_ProfileContext也必须被清掉")
  void clearsProfileContext_whenProcessingThrows() {
    doThrow(new RuntimeException("boom")).when(reActLoop).run(any(), any(), any());

    assertThrows(RuntimeException.class, () -> agentService.process(newSession(), "hi"));

    // finally 没清，下一个复用此线程的请求会拿到别人的 Profile
    assertNull(ProfileContext.current());
  }

  @Test
  @DisplayName("结束后Session被持久化")
  void persistsSession_afterProcessing() {
    when(reActLoop.run(any(), any(), any())).thenReturn("ok");
    Session session = newSession();

    agentService.process(session, "hi");

    verify(sessionManager).save(session);
  }

  private static Profile profile() {
    return new Profile(
        "test",
        "desc",
        new Profile.Identity("agent", "role"),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Settings(10, 20));
  }

  private static Session newSession() {
    return new Session("s-1", "test", "cli", "u-1");
  }
}
