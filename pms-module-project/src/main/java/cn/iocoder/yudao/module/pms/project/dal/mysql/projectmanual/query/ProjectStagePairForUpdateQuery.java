package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** Current and explicit graph-resolved target; null target never infers an edge from sort order. */
public record ProjectStagePairForUpdateQuery(Long tenantId, Long projectId, String currentStageCode,
                                           String targetStageCode) {
    public ProjectStagePairForUpdateQuery(Long tenantId, Long projectId, String currentStageCode) {
        this(tenantId, projectId, currentStageCode, null);
    }
}
