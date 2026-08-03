package com.oryxos.storage;

import com.oryxos.core.ToolInvocationRecord;
import com.oryxos.core.ToolInvocationStore;

/**
 * ToolInvocationStore 的 JPA 实现（依赖倒置：契约在 core、实现在 storage，同 §8.5 ScheduledTaskStore 模式）. 把 core
 * 值对象映射为 tool_invocations 表实体后落库.
 */
public class JpaToolInvocationStore implements ToolInvocationStore {

  private final ToolInvocationRepository repository;

  public JpaToolInvocationStore(ToolInvocationRepository repository) {
    this.repository = repository;
  }

  @Override
  public void save(ToolInvocationRecord record) {
    ToolInvocation entity = new ToolInvocation();
    entity.setSessionId(record.sessionId());
    entity.setToolName(record.toolName());
    entity.setInputJson(record.inputJson());
    entity.setResultJson(record.resultJson());
    entity.setSuccess(record.success());
    entity.setErrorMessage(record.errorMessage());
    entity.setDurationMs(record.durationMs());
    entity.setCreatedAt(record.createdAt());
    repository.save(entity);
  }
}
