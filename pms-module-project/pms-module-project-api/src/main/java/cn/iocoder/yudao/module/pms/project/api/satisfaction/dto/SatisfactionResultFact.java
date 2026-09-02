package cn.iocoder.yudao.module.pms.project.api.satisfaction.dto;

import java.math.BigDecimal;

public record SatisfactionResultFact(String outcome, String collectionKey, Long taskId, Integer taskRevisionNo,
        Long questionnaireId, Long responseId, Long resultId, Integer resultVersion, Long templateRevisionId,
        String ruleVersion, BigDecimal threshold, String sourceOwnerContext, String sourceObjectType,
        String sourceObjectId, Long sourceObjectVersion, boolean passed, String resultStatus,
        String archiveStatus, Integer factVersion) {
}
