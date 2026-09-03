package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

public record SatisfactionTaskTriggerLockQuery(Long tenantId, Long projectTaskId, String triggerOwnerContext,
        String triggerObjectType, String triggerFactId, Long triggerFactVersion) {
}
