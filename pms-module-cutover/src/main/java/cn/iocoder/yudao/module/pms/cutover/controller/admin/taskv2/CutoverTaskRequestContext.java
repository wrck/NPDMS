package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

/** F-CUT-002 HTTP受信上下文；生产Owner齐备前仅允许测试显式提供。 */
public interface CutoverTaskRequestContext {

    TrustedContext current();

    record TrustedContext(Long tenantId, Long actorId, String correlationId,
                          boolean canSaveAssessment, boolean canSubmitAssessment,
                          boolean canSaveChecklist, boolean canRequestCollection,
                          boolean canSubmitChecklist) {
        public TrustedContext {
            if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0
                    || correlationId == null || correlationId.isBlank()
                    || !correlationId.equals(correlationId.trim()) || correlationId.length() > 128) {
                throw new IllegalArgumentException("invalid trusted cutover task context");
            }
        }
    }
}
