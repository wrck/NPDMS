package cn.iocoder.yudao.module.pms.project.service.projectattribute.command;

import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeOwnerSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;

import java.time.LocalDateTime;

/** 创建后属性变化的只读匹配影响历史写入命令。 */
public record ImpactMatchHistoryCommand(
        Long tenantId,
        Long projectId,
        String triggerType,
        ProjectAttributeSnapshot beforeAttributes,
        ProjectAttributeSnapshot attributes,
        ProjectAttributeOwnerSnapshot attributeOwners,
        TemplateMatchDecision decision,
        Long frozenTemplateRevisionId,
        String inputOrigin,
        MatchSourceMetadata source,
        Long operatorId,
        String changeReason,
        LocalDateTime occurredAt,
        String idempotencyKey,
        String requestDigest,
        String operationId,
        String traceId,
        Long auditLogId) {
}
