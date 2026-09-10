package cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query;

/** PM-03: trusted tenant/project boundary for frozen graph reads. */
public record ProjectRuntimeGraphQuery(Long tenantId, Long projectId) { }
