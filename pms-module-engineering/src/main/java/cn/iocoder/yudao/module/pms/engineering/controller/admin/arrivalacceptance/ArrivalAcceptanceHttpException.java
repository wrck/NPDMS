package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance.vo.ArrivalAcceptanceRespVO;

/** Task 12 Owner 适配器可使用的稳定 HTTP 错误分类，不改变全局 Yudao 异常处理。 */
public final class ArrivalAcceptanceHttpException extends RuntimeException {

    private final int status;
    private final int code;
    private final ArrivalAcceptanceRespVO.ErrorData data;

    public ArrivalAcceptanceHttpException(int status, int code, String message,
                                          ArrivalAcceptanceRespVO.ErrorData data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    public int status() {
        return status;
    }

    public int code() {
        return code;
    }

    public ArrivalAcceptanceRespVO.ErrorData data() {
        return data;
    }
}
