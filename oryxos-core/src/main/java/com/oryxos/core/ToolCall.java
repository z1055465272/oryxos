package com.oryxos.core;

/**
 * 工具调用请求值对象：Session 的 assistant 消息与 ProviderService 的 {@link Response} 共用.
 *
 * @param id 工具调用标识（模型生成，供 tool 结果按 tool_call_id 配对）
 * @param name 工具名
 * @param arguments 调用参数 JSON 字符串
 */
public record ToolCall(String id, String name, String arguments) {}
