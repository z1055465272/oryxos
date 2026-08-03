package com.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import javax.sql.DataSource;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnableJpaRepositories(basePackageClasses = LlmCallRepository.class)
@EntityScan(basePackageClasses = LlmCall.class)
class LlmCallRepositoryTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestConfig {}

  @Autowired private LlmCallRepository repository;

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("手工建表脚本创建的 llm_calls 表能存能读，success 和 error_message 列真实存在")
  void saveAndRead_handWrittenSchemaCreatesTableCorrectly() throws Exception {
    LlmCall call = new LlmCall();
    call.setSessionId("s-test-1");
    call.setProvider("deepseek");
    call.setModel("deepseek-chat");
    call.setPromptTokens(100);
    call.setCompletionTokens(50);
    call.setTotalTokens(150);
    call.setDurationMs(1200L);
    call.setSuccess(true);
    call.setErrorMessage(null);
    call.setCreatedAt(LocalDateTime.now());

    LlmCall saved = repository.save(call);
    assertThat(saved.getId()).isNotNull();

    LlmCall found = repository.findById(saved.getId()).orElseThrow();
    assertThat(found.getSessionId()).isEqualTo("s-test-1");
    assertThat(found.getProvider()).isEqualTo("deepseek");
    assertThat(found.getModel()).isEqualTo("deepseek-chat");
    assertThat(found.getPromptTokens()).isEqualTo(100);
    assertThat(found.getCompletionTokens()).isEqualTo(50);
    assertThat(found.getTotalTokens()).isEqualTo(150);
    assertThat(found.getDurationMs()).isEqualTo(1200L);
    assertThat(found.getSuccess()).isTrue();
    assertThat(found.getErrorMessage()).isNull();
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("调用失败时 success=false 且 error_message 记录失败原因")
  void failedCall_successFalseAndErrorMessageRecorded() {
    LlmCall call = new LlmCall();
    call.setSessionId("s-fail-1");
    call.setProvider("kimi");
    call.setModel("kimi-latest");
    call.setSuccess(false);
    call.setErrorMessage("connect timeout after 30s");
    call.setCreatedAt(LocalDateTime.now());

    LlmCall saved = repository.save(call);
    assertThat(saved.getId()).isNotNull();

    LlmCall found = repository.findById(saved.getId()).orElseThrow();
    assertThat(found.getSuccess()).isFalse();
    assertThat(found.getErrorMessage()).isEqualTo("connect timeout after 30s");
  }

  @Test
  @DisplayName("llm_calls 表包含 success 和 error_message 两列")
  void tableHasSuccessAndErrorMessageColumns() throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      DatabaseMetaData meta = conn.getMetaData();
      boolean hasSuccess = false;
      boolean hasErrorMessage = false;

      try (ResultSet rs = meta.getColumns(null, null, "llm_calls", null)) {
        while (rs.next()) {
          String colName = rs.getString("COLUMN_NAME").toLowerCase();
          if ("success".equals(colName)) {
            hasSuccess = true;
          }
          if ("error_message".equals(colName)) {
            hasErrorMessage = true;
          }
        }
      }

      assertThat(hasSuccess).as("success column must exist in llm_calls table").isTrue();
      assertThat(hasErrorMessage).as("error_message column must exist in llm_calls table").isTrue();
    }
  }
}
