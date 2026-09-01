package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 阶段状态精确CAS。 */
public record ProjectStageStatusUpdate(Long tenantId, Long stageId, Integer expectedVersion,
                                       String expectedStatus, String targetStatus, String updater) {
}
