package cn.iocoder.yudao.module.pms.cutover.service.checklist.context;

/** Checklist 业务调用的受信身份与权限上下文，由服务端解析。 */
public record CutoverChecklistContext(Long tenantId, Long actorId, String correlationId) {
    public CutoverChecklistContext {
        if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0
                || correlationId == null || correlationId.isBlank()
                || !correlationId.equals(correlationId.trim()) || correlationId.length() > 128) {
            throw new IllegalArgumentException("invalid trusted cutover checklist context");
        }
    }
}
