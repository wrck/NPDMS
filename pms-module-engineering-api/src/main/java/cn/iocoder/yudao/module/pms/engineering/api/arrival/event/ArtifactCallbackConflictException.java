package cn.iocoder.yudao.module.pms.engineering.api.arrival.event;

/** 同一 ACC 回执事件编号对应不同载荷，需永久隔离并人工对账。 */
public class ArtifactCallbackConflictException extends RuntimeException {

    public ArtifactCallbackConflictException() {
        super("artifact callback event id was reused with a different payload");
    }
}
