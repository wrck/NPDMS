package cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query;

/** 查询可供受控重开消费的最近异常关闭快照。 */
public record ProjectExceptionCloseSnapshotQuery(Long tenantId, Long projectId) {
}
