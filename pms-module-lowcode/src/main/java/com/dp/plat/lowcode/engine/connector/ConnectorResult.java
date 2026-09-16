package com.dp.plat.lowcode.engine.connector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 连接器执行结果 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorResult {
    private int status;
    private Object data;
    private Map<String, String> headers;
    private String errorMessage;
    private boolean success;

    public static ConnectorResult ok(Object data) {
        return ConnectorResult.builder().status(200).data(data).success(true).build();
    }

    public static ConnectorResult error(int status, String message) {
        return ConnectorResult.builder().status(status).errorMessage(message).success(false).build();
    }
}
