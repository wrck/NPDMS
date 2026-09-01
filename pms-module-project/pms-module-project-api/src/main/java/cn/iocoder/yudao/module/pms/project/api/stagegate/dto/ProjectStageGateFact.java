package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** 不复制Owner正文的稳定阶段门禁事实。 */
public record ProjectStageGateFact(
        String providerKey,
        String refType,
        String ownerObjectKey,
        String ownerBusinessVersion,
        String factVersion,
        ProjectStageGateOutcome outcome,
        String unmetCode) {
}
