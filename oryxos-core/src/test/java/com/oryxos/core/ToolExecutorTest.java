package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ToolExecutorTest {

  private ToolRegistry toolRegistry;
  private ToolInvocationStore store;
  private ToolExecutor executor;

  @BeforeEach
  void setUp() {
    toolRegistry = mock(ToolRegistry.class);
    store = mock(ToolInvocationStore.class);
    executor = new ToolExecutor(toolRegistry, store);
  }

  @Test
  @DisplayName("工具成功_写审计success为true")
  void recordsAuditSuccess_whenToolSucceeds() {
    OryxTool tool = mock(OryxTool.class);
    when(tool.execute(any())).thenReturn(ToolResult.ok("{\"temp\":-5}"));
    when(toolRegistry.get("http_get")).thenReturn(Optional.of(tool));

    ToolResult result =
        executor.execute("s-1", new ToolCall("call-1", "http_get", "{\"city\":\"beijing\"}"));

    assertThat(result.success()).isTrue();
    ToolInvocationRecord record = capturedRecord();
    assertThat(record.sessionId()).isEqualTo("s-1");
    assertThat(record.toolName()).isEqualTo("http_get");
    assertThat(record.inputJson()).isEqualTo("{\"city\":\"beijing\"}");
    assertThat(record.resultJson()).isEqualTo("{\"temp\":-5}");
    assertThat(record.success()).isTrue();
    assertThat(record.errorMessage()).isNull();
    assertThat(record.durationMs()).isGreaterThanOrEqualTo(0);
    assertThat(record.createdAt()).isNotNull();
  }

  @Test
  @DisplayName("工具返回失败_写审计success为false带原因")
  void recordsAuditFailure_whenToolReturnsError() {
    OryxTool tool = mock(OryxTool.class);
    when(tool.execute(any())).thenReturn(ToolResult.fail("连接超时", false));
    when(toolRegistry.get("http_get")).thenReturn(Optional.of(tool));

    ToolResult result = executor.execute("s-1", new ToolCall("call-1", "http_get", "{}"));

    assertThat(result.success()).isFalse();
    ToolInvocationRecord record = capturedRecord();
    assertThat(record.success()).isFalse();
    assertThat(record.errorMessage()).contains("连接超时");
  }

  @Test
  @DisplayName("工具抛异常_审计后上抛不吞")
  void rethrowsException_afterRecordingFailureAudit() {
    OryxTool tool = mock(OryxTool.class);
    when(tool.execute(any())).thenThrow(new RuntimeException("boom"));
    when(toolRegistry.get("http_get")).thenReturn(Optional.of(tool));

    assertThrows(
        RuntimeException.class,
        () -> executor.execute("s-1", new ToolCall("call-1", "http_get", "{}")));

    ToolInvocationRecord record = capturedRecord();
    assertThat(record.success()).isFalse();
    assertThat(record.errorMessage()).contains("boom");
  }

  @Test
  @DisplayName("未知工具_写审计失败并返回失败结果")
  void unknownTool_writesFailureAuditAndReturnsError() {
    when(toolRegistry.get("nope")).thenReturn(Optional.empty());

    ToolResult result = executor.execute("s-1", new ToolCall("call-1", "nope", "{}"));

    assertThat(result.success()).isFalse();
    ToolInvocationRecord record = capturedRecord();
    assertThat(record.success()).isFalse();
    assertThat(record.errorMessage()).contains("unknown tool");
  }

  private ToolInvocationRecord capturedRecord() {
    ArgumentCaptor<ToolInvocationRecord> captor =
        ArgumentCaptor.forClass(ToolInvocationRecord.class);
    verify(store).save(captor.capture());
    return captor.getValue();
  }
}
