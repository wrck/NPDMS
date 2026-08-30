package cn.iocoder.yudao.module.pms.cutover.service.taskv2.migration;

/** F-CUT-002旧任务前向转换的内部稳定失败。 */
public final class LegacyCutoverMigrationException extends RuntimeException {

    public LegacyCutoverMigrationException(String message) {
        super(message);
    }
}
