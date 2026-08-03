package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/** LlmCall 的 Spring Data JPA Repository，核心阶段只做写入. */
public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {}
