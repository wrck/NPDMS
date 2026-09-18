package cn.iocoder.yudao.module.pms.project.domain.template;

import java.util.Objects;

/** 订阅扫描位置，不是业务状态；库存游标与已提交变化序号不能相互替代。 */
public record ResultSubscriptionCheckpoint(Phase phase, long baselineSequence, String inventoryCursor,
                                           long processedSequence, Long throughSequence) {
    public enum Phase { INVENTORY, CHANGES, LIVE }

    public ResultSubscriptionCheckpoint {
        Objects.requireNonNull(phase, "subscription phase");
        if (baselineSequence < 0 || processedSequence < baselineSequence
                || inventoryCursor != null && (inventoryCursor.isBlank() || inventoryCursor.length() > 128)
                || (phase == Phase.CHANGES ? throughSequence == null || throughSequence < processedSequence
                                          : throughSequence != null)
                || phase == Phase.INVENTORY && processedSequence != baselineSequence) {
            throw new IllegalArgumentException("SUBSCRIPTION_CHECKPOINT_INVALID");
        }
    }

    public static ResultSubscriptionCheckpoint initial(long baselineSequence) {
        return new ResultSubscriptionCheckpoint(Phase.INVENTORY, baselineSequence, null, baselineSequence, null);
    }

    /** 一页未结束时必须提供新的不透明游标；不按字符串或数据库主键推断提交顺序。 */
    public ResultSubscriptionCheckpoint inventoryPage(String nextCursor) {
        require(Phase.INVENTORY);
        if (nextCursor == null || Objects.equals(inventoryCursor, nextCursor))
            throw new IllegalArgumentException("SUBSCRIPTION_INVENTORY_NOT_ADVANCING");
        return new ResultSubscriptionCheckpoint(phase, baselineSequence, nextCursor, processedSequence, null);
    }

    /** 完成库存后冻结补采上界；期间新增的结果由下一次变化扫描接续。 */
    public ResultSubscriptionCheckpoint inventoryComplete(long through) {
        require(Phase.INVENTORY);
        return new ResultSubscriptionCheckpoint(Phase.CHANGES, baselineSequence, inventoryCursor, processedSequence, through);
    }

    public ResultSubscriptionCheckpoint beginChanges(long through) {
        require(Phase.LIVE);
        return new ResultSubscriptionCheckpoint(Phase.CHANGES, baselineSequence, inventoryCursor, processedSequence, through);
    }

    /** 只在该页证据已持久化的事务中调用。失败时回滚位置，重试不能跳至新的上界。 */
    public ResultSubscriptionCheckpoint changesPage(long nextSequence) {
        require(Phase.CHANGES);
        if (nextSequence < processedSequence || nextSequence > throughSequence
                || nextSequence == processedSequence && nextSequence != throughSequence) {
            throw new IllegalArgumentException("SUBSCRIPTION_CHANGE_POSITION_INVALID");
        }
        return new ResultSubscriptionCheckpoint(nextSequence == throughSequence ? Phase.LIVE : Phase.CHANGES,
                baselineSequence, inventoryCursor, nextSequence, nextSequence == throughSequence ? null : throughSequence);
    }

    private void require(Phase expected) {
        if (phase != expected) throw new IllegalStateException("SUBSCRIPTION_PHASE_MISMATCH");
    }
}
