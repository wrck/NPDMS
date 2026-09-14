package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query;

public record ProjectReworkVersionUpdate(Long tenantId, Long projectId, Long planVersionId,
                                        Integer expectedVersion, String updater) { }
