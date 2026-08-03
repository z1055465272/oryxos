package com.oryxos.core;

import java.time.LocalDateTime;

/**
 * 一次工具调用的审计值对象（跨模块契约，放 core）. storage 侧映射为 tool_invocations 表的 JPA 实体.
 *
 * @param sessionId 关联会话
 * @param toolName 工具名
 * @param inputJson 调用参数 JSON
 * @param resultJson 执行结果 JSON（失败时为 null）
 * @param success 是否成功
 * @param errorMessage 失败原因（成功时为 null）
 * @param durationMs 执行耗时 ms
 * @param createdAt 调用时间
 */
public record ToolInvocationRecord(
    String sessionId,
    String toolName,
    String inputJson,
    String resultJson,
    boolean success,
    String errorMessage,
    long durationMs,
    LocalDateTime createdAt) {}
