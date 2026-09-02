package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import java.math.BigDecimal;

public record SatisfactionResultFactRecord(Long tenantId, String collectionKey, Long taskId,
        Integer taskRevisionNo, Long questionnaireId, Long responseId, Long resultId, Integer resultVersion,
        Long templateRevisionId, String ruleVersion, BigDecimal threshold, String sourceOwnerContext,
        String sourceObjectType, String sourceObjectId, Long sourceObjectVersion, Boolean passed,
        String resultStatus, String archiveStatus, Integer factVersion) {
}
