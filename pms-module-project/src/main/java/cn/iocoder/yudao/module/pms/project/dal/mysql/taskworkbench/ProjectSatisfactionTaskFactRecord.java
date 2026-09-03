package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import java.math.BigDecimal;

/** PROJ锁定满意度任务后返回的冻结事实。 */
public record ProjectSatisfactionTaskFactRecord(
        Long tenantId,
        Long projectId,
        Long projectTaskId,
        String taskCode,
        Integer projectTaskVersion,
        String satisfactionTiming,
        Long templateId,
        Long templateRevisionId,
        Integer templateVersion,
        String ruleVersion,
        BigDecimal threshold,
        Long currentAssigneeUserId) {
}
