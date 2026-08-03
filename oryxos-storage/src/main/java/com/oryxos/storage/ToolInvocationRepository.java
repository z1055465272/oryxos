package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/** ToolInvocation 的 Spring Data JPA Repository，核心阶段只做写入. */
public interface ToolInvocationRepository extends JpaRepository<ToolInvocation, Long> {}
