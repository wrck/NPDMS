package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 项目当前阶段精确CAS。 */
public record ProjectStageAdvanceUpdate(Long tenantId, Long projectId, Integer expectedVersion,
                                        String expectedCurrentStage, String targetStage, String updater) {
}
