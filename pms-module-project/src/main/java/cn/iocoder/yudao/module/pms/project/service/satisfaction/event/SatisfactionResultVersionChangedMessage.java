package cn.iocoder.yudao.module.pms.project.service.satisfaction.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SatisfactionResultVersionChangedMessage(
        String eventId, String changeType, Long tenantId, Long projectId, Long projectTaskId,
        Integer projectTaskVersion, String taskCode, String collectionKey, Integer taskRevisionNo,
        Long taskId, Long questionnaireId, Long responseId, Long resultId, Integer resultVersion,
        Integer resultFactVersion, Long templateRevisionId, String ruleVersion, BigDecimal threshold,
        String sourceOwnerContext, String sourceObjectType, String sourceObjectId, Long sourceObjectVersion,
        Boolean passed, String resultStatus, Long archiveActorUserId, String invalidationReasonCode,
        Long invalidatedByUserId, LocalDateTime invalidatedAt, List<FileFact> files) {

    public record FileFact(String role, Integer sequence, Long artifactId, Integer versionNo,
            String referenceKey, Integer artifactVersion, Integer referenceVersion,
            Integer availabilityVersion, Long scopeVersion, String sha256) {
    }
}
