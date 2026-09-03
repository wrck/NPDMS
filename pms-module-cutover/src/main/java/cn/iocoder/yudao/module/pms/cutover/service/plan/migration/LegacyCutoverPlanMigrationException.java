package cn.iocoder.yudao.module.pms.cutover.service.plan.migration;

/** F-CUT-004旧方案前向核对的稳定失败。 */
public class LegacyCutoverPlanMigrationException extends RuntimeException {

    public LegacyCutoverPlanMigrationException(String message) {
        super(message);
    }
}
