package com.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.Session;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/**
 * 会话契约 harness（课件验收）：同一三元组幂等返回同一 Session；三元组任一不同则不同 Session；session_id 拼接只此一处.
 *
 * <p>直连 SQLite 验 {@link JpaSessionManager} 真实实现（不 mock），与 LlmCallRepositoryTest 同模式。 关键回归测试
 * "同一三元组_历次getOrCreate都是同一个Session" 断言逐条保真，方法名译英文、课件原文进 {@code @DisplayName}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnableJpaRepositories(basePackageClasses = SessionRepository.class)
@EntityScan(basePackageClasses = SessionEntity.class)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:sqlite:target/test-sessions.db",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:schema.sql"
    })
class SessionManagerTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestConfig {}

  @Autowired private SessionRepository repository;

  @Test
  @DisplayName("同一三元组_历次getOrCreate都是同一个Session")
  void getOrCreate_sameTriple_returnsSameSession() {
    var sessionManager = new JpaSessionManager(repository);

    var first = sessionManager.getOrCreate("cli", "wang", "default");
    var second = sessionManager.getOrCreate("cli", "wang", "default");

    assertThat(first.id()).isEqualTo(second.id()); // 幂等：多轮对话靠它串起来
    assertThat(repository.count()).isEqualTo(1); // 只落了一行
  }

  @Test
  @DisplayName("channel_user_profile任一不同则是不同Session")
  void getOrCreate_differentTripleElement_returnsDifferentSession() {
    var sessionManager = new JpaSessionManager(repository);

    var cli = sessionManager.getOrCreate("cli", "wang", "default");
    var web = sessionManager.getOrCreate("web", "wang", "default"); // channel 不同
    var otherUser = sessionManager.getOrCreate("cli", "li", "default"); // user 不同
    var otherProfile = sessionManager.getOrCreate("cli", "wang", "weather"); // profile 不同

    assertThat(web.id()).isNotEqualTo(cli.id());
    assertThat(otherUser.id()).isNotEqualTo(cli.id());
    assertThat(otherProfile.id()).isNotEqualTo(cli.id());
    assertThat(repository.count()).isEqualTo(4);
  }

  @Test
  @DisplayName("session_id拼接只此一处_格式为channel:user:profile")
  void sessionId_concatenatedOnlyInSessionManager() {
    var sessionManager = new JpaSessionManager(repository);

    var session = sessionManager.getOrCreate("cli", "wang", "default");

    // 拼接只发生在 JpaSessionManager 内部，格式可精确断言；入口只传三元组、不自己拼
    assertThat(session.id()).isEqualTo(JpaSessionManager.buildSessionId("cli", "wang", "default"));
    assertThat(session.id()).isEqualTo("cli:wang:default");
  }

  @Test
  @DisplayName("save后get回读消息完整")
  void save_thenGet_readsBackMessages() {
    var sessionManager = new JpaSessionManager(repository);

    Session session = sessionManager.getOrCreate("cli", "wang", "default");
    session.appendUserMessage("今天北京天气怎么样");
    session.appendAssistant("建议穿羽绒服", java.util.List.of());
    sessionManager.save(session);

    Session reloaded = sessionManager.get(session.id()).orElseThrow();
    assertThat(reloaded.messages()).hasSize(2);
    assertThat(reloaded.messages().get(0)).isEqualTo(new Session.UserMessage("今天北京天气怎么样"));
    assertThat(reloaded.messages().get(1))
        .isEqualTo(new Session.AssistantMessage("建议穿羽绒服", List.of()));
  }
}
