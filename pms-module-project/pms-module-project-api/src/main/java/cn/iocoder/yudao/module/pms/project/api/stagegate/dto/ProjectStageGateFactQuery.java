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
        String refCode) {
}
