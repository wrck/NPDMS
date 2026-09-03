package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SatisfactionResultViewRecord(
        Long resultId, Long projectId, Long projectTaskId, Long collectionTaskId, Integer taskRevisionNo,
        Long questionnaireId, Long responseId, Integer resultVersion, Integer factVersion,
        BigDecimal score, BigDecimal threshold, Boolean passed, String ruleVersion, String resultStatus,
        String archiveStatus, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
}
