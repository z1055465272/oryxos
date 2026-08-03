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
@EnableJpaRepositories(basePackageClasses = ToolInvocationRepository.class)
@EntityScan(basePackageClasses = ToolInvocation.class)
class ToolInvocationRepositoryTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestConfig {}

  @Autowired private ToolInvocationRepository repository;

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("手工建表脚本创建的 tool_invocations 表能存能读，success 和 error_message 列真实存在")
  void saveAndRead_handWrittenSchemaCreatesTableCorrectly() throws Exception {
    ToolInvocation inv = new ToolInvocation();
    inv.setSessionId("s-test-1");
    inv.setToolName("http_get");
    inv.setInputJson("{\"city\":\"beijing\"}");
    inv.setResultJson("{\"temp\":-5}");
    inv.setSuccess(true);
    inv.setDurationMs(12L);
    inv.setCreatedAt(LocalDateTime.now());

    ToolInvocation saved = repository.save(inv);
    assertThat(saved.getId()).isNotNull();

    ToolInvocation found = repository.findById(saved.getId()).orElseThrow();
    assertThat(found.getSessionId()).isEqualTo("s-test-1");
    assertThat(found.getToolName()).isEqualTo("http_get");
    assertThat(found.getInputJson()).isEqualTo("{\"city\":\"beijing\"}");
    assertThat(found.getResultJson()).isEqualTo("{\"temp\":-5}");
    assertThat(found.getSuccess()).isTrue();
    assertThat(found.getErrorMessage()).isNull();
    assertThat(found.getDurationMs()).isEqualTo(12L);
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("工具失败时 success=false 且 error_message 记录失败原因")
  void failedCall_successFalseAndErrorMessageRecorded() {
    ToolInvocation inv = new ToolInvocation();
    inv.setSessionId("s-fail-1");
    inv.setToolName("http_get");
    inv.setInputJson("{}");
    inv.setSuccess(false);
    inv.setErrorMessage("连接超时");
    inv.setDurationMs(5L);
    inv.setCreatedAt(LocalDateTime.now());

    ToolInvocation saved = repository.save(inv);
    assertThat(saved.getId()).isNotNull();

    ToolInvocation found = repository.findById(saved.getId()).orElseThrow();
    assertThat(found.getSuccess()).isFalse();
    assertThat(found.getErrorMessage()).isEqualTo("连接超时");
  }

  @Test
  @DisplayName("tool_invocations 表包含 success 和 error_message 两列")
  void tableHasSuccessAndErrorMessageColumns() throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      DatabaseMetaData meta = conn.getMetaData();
      boolean hasSuccess = false;
      boolean hasErrorMessage = false;

      try (ResultSet rs = meta.getColumns(null, null, "tool_invocations", null)) {
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

      assertThat(hasSuccess).as("success column must exist in tool_invocations table").isTrue();
      assertThat(hasErrorMessage)
          .as("error_message column must exist in tool_invocations table")
          .isTrue();
    }
  }
}
