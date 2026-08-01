package com.oryxos.core;

/**
 * OryxOS 统一的 Tool 抽象。
 * <p>
 * 内置 Tool、{@code @Tool} 注解的 Plugin Tool、MCP Tool 都被包装成 {@code OryxTool} 实例
 * 注册到 ToolRegistry，ReAct 循环不感知具体 Tool 的来源。
 */
public interface OryxTool {

    /** Tool 名，ReAct 循环和 LLM 用它来引用 */
    String getName();

    /** 工具用途描述，供 LLM 理解何时调用 */
    String getDescription();

    /** 参数的 JSON Schema 字符串，由 Spring AI {@code @Tool} 注解或手写生成 */
    String getInputSchema();

    /** 执行工具，返回封装结果；失败时 error 非空、retryable 指示是否可重试 */
    ToolResult execute(String jsonInput);
}
