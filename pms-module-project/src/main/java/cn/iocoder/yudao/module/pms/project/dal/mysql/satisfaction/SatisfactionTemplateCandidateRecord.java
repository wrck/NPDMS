package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import java.math.BigDecimal;

public record SatisfactionTemplateCandidateRecord(Long templateId, Long templateRevisionId,
        Integer templateVersion, String ruleVersion, BigDecimal threshold, Integer priority) {
}
