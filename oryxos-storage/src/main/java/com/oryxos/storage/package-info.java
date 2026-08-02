/**
 * OryxOS 存储层（支撑）：SQLite + Spring Data JPA。
 *
 * <p>sessions、tool_invocations、llm_calls 三张表，其中后两张审计表核心阶段 day one 写入落库。
 */
package com.oryxos.storage;
