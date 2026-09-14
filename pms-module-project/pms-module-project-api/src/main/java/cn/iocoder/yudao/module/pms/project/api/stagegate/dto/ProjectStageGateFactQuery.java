package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** PROJ在推进事务中按冻结Gate Reference构造的Owner重验查询。 */
public record ProjectStageGateFactQuery(
        Long tenantId,
        Long projectId,
        String currentStageCode,
        Long gateId,
        String gateCode,
        Integer gateVersion,
        Long gateReferenceId,
        Integer gateReferenceVersion,
        String refType,
        String refCode,
        java.time.Instant processStartedNotBefore) {

    /** BPM facts require the current stage round's lower bound; non-process Owners do not use it. */
    public ProjectStageGateFactQuery forStageRound(java.time.Instant startedNotBefore) {
        return new ProjectStageGateFactQuery(tenantId, projectId, currentStageCode, gateId, gateCode,
                gateVersion, gateReferenceId, gateReferenceVersion, refType, refCode, startedNotBefore);
    }
}
