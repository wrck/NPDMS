package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

/** F-CUT-006 HTTP受信上下文；生产Owner接通前仅由测试显式提供。 */
public interface CutoverClosureRequestContext {
    TrustedContext current();

    record TrustedContext(Long tenantId, Long actorId, String correlationId,
                          boolean canSave, boolean canRequestCollection, boolean canSubmit) {
        public TrustedContext {
            if (tenantId == null || tenantId <= 0 || actorId == null || actorId <= 0
                    || correlationId == null || correlationId.isBlank()
                    || !correlationId.equals(correlationId.trim()) || correlationId.length() > 128) {
                throw new IllegalArgumentException("invalid trusted cutover closure context");
            }
        }
    }
}
