package cn.iocoder.yudao.module.pms.project.api.satisfaction.dto;

import java.math.BigDecimal;

public record SatisfactionTemplateFact(String outcome, Long templateId, Long templateRevisionId,
        Integer templateVersion, String ruleVersion, BigDecimal threshold) {
}
