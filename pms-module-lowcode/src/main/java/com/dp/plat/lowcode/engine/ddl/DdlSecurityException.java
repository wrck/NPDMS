package com.dp.plat.lowcode.engine.ddl;

/**
 * DDL 安全异常 — 当 DDL 违反安全策略时抛出（如 DROP TABLE）
 */
public class DdlSecurityException extends RuntimeException {

    public DdlSecurityException(String message) {
        super(message);
    }

    public DdlSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
