package cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query;

/** 在项目行锁保护下取得指定阶段的下一快照序号。 */
public record ProjectStageSnapshotSequenceQuery(
        Long tenantId,
        Long projectId,
        String stageCode) {
}
