package cn.iocoder.yudao.module.pms.engineering.api.arrival.event;

/** 同一 ACC 回执事件正在处理，可由调用方稍后重试。 */
public class ArtifactCallbackInProgressException extends RuntimeException {

    public ArtifactCallbackInProgressException() {
        super("artifact callback event is already in progress");
    }
}
