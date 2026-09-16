package cn.iocoder.yudao.module.pms.integration.extsystem.model.fp;

import lombok.Data;

/**
 * Generic FP API response.
 *
 * @param <T> the data payload type
 */
@Data
public class FpResponse<T> {

    private String code;

    private String message;

    private T data;

    /**
     * Whether the FP call is considered successful (code "0" or "200").
     */
    public boolean isSuccess() {
        return "0".equals(code) || "200".equals(code);
    }
}
