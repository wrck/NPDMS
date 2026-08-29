package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

public record ProjectStageStatusUpdate(Long tenantId, Long projectId, Long stageId,
                                       Integer expectedVersion, String expectedStatus,
                                       String targetStatus, String updater) {
}
