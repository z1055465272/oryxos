package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/** sessions 表数据访问. session_id 由 SessionManager 按三元组拼接，此处按精确主键查. */
public interface SessionRepository extends JpaRepository<SessionEntity, String> {}
