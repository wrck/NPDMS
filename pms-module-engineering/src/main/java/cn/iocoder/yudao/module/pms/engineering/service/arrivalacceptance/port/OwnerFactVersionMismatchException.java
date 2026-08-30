package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

/** Owner事实与调用方期望版本不一致；调用方可重新读取当前事实并返回STALE。 */
public class OwnerFactVersionMismatchException extends RuntimeException {

    public OwnerFactVersionMismatchException(String message) {
        super(message);
    }
}
