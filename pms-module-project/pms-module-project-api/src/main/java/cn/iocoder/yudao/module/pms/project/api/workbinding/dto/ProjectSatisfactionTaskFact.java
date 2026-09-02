package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

import java.math.BigDecimal;

public record ProjectSatisfactionTaskFact(Long projectId, Long projectTaskId, String taskCode,
        Integer projectTaskVersion, String satisfactionTiming, Long templateId, Long templateRevisionId,
        Integer templateVersion, String ruleVersion, BigDecimal threshold, Long currentAssigneeUserId) {
}
