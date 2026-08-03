package com.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.Session;
import com.oryxos.core.ToolCall;
import com.oryxos.core.ToolResult;
import java.time.LocalDateTime;
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

/** sessions 表持久化 harness（课件验收）：手工建表脚本建出的表能存能读；messages_json 序列化回读后消息完整；模拟"重启"历史还在. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnableJpaRepositories(basePackageClasses = SessionRepository.class)
@EntityScan(basePackageClasses = SessionEntity.class)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:sqlite:target/test-sessions-repo.db",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:schema.sql"
    })
class SessionRepositoryTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestConfig {}

  @Autowired private SessionRepository repository;

  @Test
  @DisplayName("手工建表脚本建出的sessions表能存能读")
  void saveAndRead_handWrittenSchemaCreatesTableCorrectly() {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId("cli:wang:default");
    entity.setProfileName("default");
    entity.setChannel("cli");
    entity.setUserId("wang");
    entity.setMessagesJson("[]");
    entity.setStatus("active");
    entity.setCreatedAt(LocalDateTime.now());
    entity.setLastActiveAt(LocalDateTime.now());

    repository.save(entity);

    SessionEntity found = repository.findById("cli:wang:default").orElseThrow();
    assertThat(found.getSessionId()).isEqualTo("cli:wang:default");
    assertThat(found.getProfileName()).isEqualTo("default");
    assertThat(found.getChannel()).isEqualTo("cli");
    assertThat(found.getUserId()).isEqualTo("wang");
    assertThat(found.getStatus()).isEqualTo("active");
    assertThat(found.getMessagesJson()).isEqualTo("[]");
  }

  @Test
  @DisplayName("messages_json序列化回读后消息完整")
  void encodeDecode_messagesRoundTripComplete() {
    Session session = new Session("cli:wang:default", "default", "cli", "wang");
    session.appendUserMessage("你好");
    session.appendAssistant("你好，有什么可以帮你", List.of());
    session.appendUserMessage("查一下天气");
    session.appendAssistant(
        "我来查", List.of(new ToolCall("call-1", "http_get", "{\"city\":\"beijing\"}")));
    session.appendToolResult(
        new ToolCall("call-1", "http_get", "{\"city\":\"beijing\"}"),
        ToolResult.ok("{\"temp\":-5}"));

    String json = SessionEntity.encodeMessages(session.messages());
    List<Session.Message> decoded = SessionEntity.decodeMessages(json);

    assertThat(decoded).hasSize(5);
    // 逐条断言三型消息内容与顺序
    assertThat(decoded.get(0)).isEqualTo(new Session.UserMessage("你好"));
    assertThat(decoded.get(1)).isEqualTo(new Session.AssistantMessage("你好，有什么可以帮你", List.of()));
    assertThat(decoded.get(2)).isEqualTo(new Session.UserMessage("查一下天气"));
    assertThat(decoded.get(3))
        .isEqualTo(
            new Session.AssistantMessage(
                "我来查", List.of(new ToolCall("call-1", "http_get", "{\"city\":\"beijing\"}"))));
    assertThat(decoded.get(4))
        .isEqualTo(new Session.ToolResultMessage("call-1", "http_get", "{\"temp\":-5}"));
  }

  @Test
  @DisplayName("首轮对话空历史_序列化回读为空列表不抛异常")
  void encodeDecode_emptyHistory_roundTripsToEmpty() {
    Session session = new Session("cli:wang:default", "default", "cli", "wang");

    String json = SessionEntity.encodeMessages(session.messages());
    assertThat(json).isEqualTo("[]");
    assertThat(SessionEntity.decodeMessages(json)).isEmpty();
  }

  @Test
  @DisplayName("模拟重启_同一SQLite文件新建context重查历史还在")
  void simulateRestart_newContextStilFindsHistory() {
    // 第一次"运行"：写入并 save
    Session session = new Session("cli:wang:default", "default", "cli", "wang");
    session.appendUserMessage("记一下：明天要开会");
    SessionEntity entity =
        SessionEntity.fromSession(session, SessionEntity.encodeMessages(session.messages()));
    entity.setCreatedAt(LocalDateTime.now());
    entity.setLastActiveAt(LocalDateTime.now());
    repository.save(entity);

    // 模拟"重启"：同一 SQLite 文件（同一 @DataJpaTest context 下的同一 DataSource），重新查同一主键
    SessionEntity reloaded = repository.findById("cli:wang:default").orElseThrow();
    Session restored = SessionEntity.toSession(reloaded);

    assertThat(restored.id()).isEqualTo("cli:wang:default");
    assertThat(restored.messages()).hasSize(1);
    assertThat(restored.messages().get(0)).isEqualTo(new Session.UserMessage("记一下：明天要开会"));
  }
}
