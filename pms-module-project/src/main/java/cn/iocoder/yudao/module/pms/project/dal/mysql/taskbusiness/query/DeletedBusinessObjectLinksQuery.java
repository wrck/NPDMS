package cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query;

public record DeletedBusinessObjectLinksQuery(Long tenantId, Long projectId,
        String ownerContext, String objectType, String objectId) { }
