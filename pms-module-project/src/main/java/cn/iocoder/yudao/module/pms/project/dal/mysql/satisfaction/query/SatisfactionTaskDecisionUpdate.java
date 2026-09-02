package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

public record SatisfactionTaskDecisionUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                             String updater) {}
