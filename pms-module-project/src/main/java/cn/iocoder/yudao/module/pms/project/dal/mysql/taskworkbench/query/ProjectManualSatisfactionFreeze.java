package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.math.BigDecimal;

public record ProjectManualSatisfactionFreeze(Long tenantId, Long projectId, Long projectTaskId,
        Integer expectedVersion, Long templateId, Long revisionId, Integer templateVersion,
        String ruleVersion, BigDecimal threshold, String updater) { }
