package cn.iocoder.yudao.module.pms.project.api.systemqualification.dto;

/** 无用户主体内部命令的PROJ当前资格锁定请求；tenantId只取受信调用上下文。 */
public record ProjectSystemQualificationLockQuery(
        Long projectId,
        String requiredLifecycleStatus,
        String requiredCurrentStage) {
}
