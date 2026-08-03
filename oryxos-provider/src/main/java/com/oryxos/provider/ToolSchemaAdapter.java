package com.oryxos.provider;

import com.oryxos.core.OryxTool;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.openai.api.OpenAiApi;

/** 把 OryxTool 的参数 schema 翻译为 OpenAI 工具格式，只翻译 schema 不执行. */
public class ToolSchemaAdapter {

  /** 将 OryxTool 列表翻译为 OpenAI FunctionTool 列表，只生成 schema 描述. */
  public List<OpenAiApi.FunctionTool> toSpringAiTools(List<OryxTool> tools) {
    if (tools == null || tools.isEmpty()) {
      return Collections.emptyList();
    }
    return tools.stream().map(this::toFunctionTool).toList();
  }

  private OpenAiApi.FunctionTool toFunctionTool(OryxTool tool) {
    var function =
        new OpenAiApi.FunctionTool.Function(
            tool.getDescription(), tool.getName(), tool.getInputSchema());
    // 构造器第三个参数不直接赋值 jsonSchema 字段，通过 setter 显式设置
    function.setJsonSchema(tool.getInputSchema());
    return new OpenAiApi.FunctionTool(function);
  }
}
