package com.dp.plat.lowcode.engine.microflow;

import lombok.Getter;

/**
 * 微流执行异常：由 THROW_EXCEPTION 节点显式抛出，终止微流执行。
 */
@Getter
public class MicroflowExecutionException extends RuntimeException {

    private final String errorCode;

    public MicroflowExecutionException(String errorMessage, String errorCode) {
        super(errorMessage);
        this.errorCode = errorCode;
    }

    public MicroflowExecutionException(String errorMessage, String errorCode, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
    }
}
