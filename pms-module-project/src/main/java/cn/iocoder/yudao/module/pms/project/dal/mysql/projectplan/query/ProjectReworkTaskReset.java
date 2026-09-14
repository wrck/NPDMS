package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query;

public record ProjectReworkTaskReset(Long tenantId, Long projectId, Long taskId, Integer expectedVersion,
                                    String expectedStatus, String initialStatus, String updater) { }
