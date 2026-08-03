package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

class ToolSchemaAdapterTest {

  private final ToolSchemaAdapter adapter = new ToolSchemaAdapter();

  @Test
  @DisplayName("schema字段与OryxTool对齐")
  void schemaFieldsAlignWithOryxTool() {
    OryxTool tool =
        mockTool(
            "http_get",
            "HTTP GET request",
            "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}");

    List<OpenAiApi.FunctionTool> tools = adapter.toSpringAiTools(List.of(tool));

    assertThat(tools).hasSize(1);
    var function = tools.get(0).getFunction();
    assertThat(function.getName()).isEqualTo("http_get");
    assertThat(function.getDescription()).isEqualTo("HTTP GET request");
    assertThat(function.getJsonSchema()).contains("\"url\"");
    assertThat(function.getJsonSchema()).contains("\"type\":\"string\"");
  }

  @Test
  @DisplayName("翻译产物不含执行逻辑——只生成 schema 描述")
  void translatedOutputContainsNoExecutionLogic() {
    OryxTool tool =
        mockTool(
            "shell",
            "Execute shell command",
            "{\"type\":\"object\",\"properties\":{\"cmd\":{\"type\":\"string\"}}}");

    List<OpenAiApi.FunctionTool> tools = adapter.toSpringAiTools(List.of(tool));

    assertThat(tools).hasSize(1);
    var function = tools.get(0).getFunction();
    assertThat(function.getName()).isEqualTo("shell");
    assertThat(function.getJsonSchema()).isNotNull();
    // FunctionTool is pure data — no execution callbacks
  }

  @Test
  @DisplayName("多个工具独立翻译，互不干扰")
  void multipleToolsTranslatedIndependently() {
    OryxTool tool1 = mockTool("http_get", "HTTP GET", "{\"type\":\"object\"}");
    OryxTool tool2 = mockTool("shell", "Shell exec", "{\"type\":\"object\"}");
    OryxTool tool3 = mockTool("read_file", "Read file", "{\"type\":\"object\"}");

    List<OpenAiApi.FunctionTool> tools = adapter.toSpringAiTools(List.of(tool1, tool2, tool3));

    assertThat(tools).hasSize(3);
    assertThat(tools.get(0).getFunction().getName()).isEqualTo("http_get");
    assertThat(tools.get(1).getFunction().getName()).isEqualTo("shell");
    assertThat(tools.get(2).getFunction().getName()).isEqualTo("read_file");
  }

  @Test
  @DisplayName("空工具列表返回空结果不抛异常")
  void emptyToolListReturnsEmpty() {
    List<OpenAiApi.FunctionTool> tools = adapter.toSpringAiTools(List.of());
    assertThat(tools).isEmpty();
  }

  @Test
  @DisplayName("null 工具列表返回空结果不抛异常")
  void nullToolListReturnsEmpty() {
    List<OpenAiApi.FunctionTool> tools = adapter.toSpringAiTools(null);
    assertThat(tools).isEmpty();
  }

  private static OryxTool mockTool(String name, String description, String inputSchema) {
    OryxTool tool = mock(OryxTool.class);
    when(tool.getName()).thenReturn(name);
    when(tool.getDescription()).thenReturn(description);
    when(tool.getInputSchema()).thenReturn(inputSchema);
    when(tool.execute(null)).thenReturn(ToolResult.ok("ok"));
    return tool;
  }
}
