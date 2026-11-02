package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

public record ProjectContractProjectionUpdate(Long tenantId, Long projectId, Long expectedVersion,
                                              String expectedContractNo, String contractNo) { }
