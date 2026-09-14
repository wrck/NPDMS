package cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query;

import java.util.Set;

public record PreviousBusinessAssociationsQuery(Long tenantId, Long projectId, Long taskId,
        Long currentExecutionId, String ownerContext, String objectType, Set<String> objectIds, Long stageId) {
    public PreviousBusinessAssociationsQuery(Long tenantId, Long projectId, Long taskId,
            Long currentExecutionId, String ownerContext, String objectType, Set<String> objectIds) {
        this(tenantId,projectId,taskId,currentExecutionId,ownerContext,objectType,objectIds,null);
    }
}
