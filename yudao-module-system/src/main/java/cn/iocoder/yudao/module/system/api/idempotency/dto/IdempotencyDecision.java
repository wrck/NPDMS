package cn.iocoder.yudao.module.system.api.idempotency.dto;

/** 幂等命令的占有或重放判定。 */
public record IdempotencyDecision(Mode mode, long recordId, Long resourceId, String responseJson) {

    public enum Mode { OWNER, REPLAY }

    public boolean isOwner() {
        return mode == Mode.OWNER;
    }

    public boolean isReplay() {
        return mode == Mode.REPLAY;
    }
}
