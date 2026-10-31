package cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.query;

/** 当前租户和项目中正在审批的施工计划。 */
public record StagePlanApprovalConflictQuery(Long tenantId, Long projectId) { }
