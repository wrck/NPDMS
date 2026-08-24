package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectattribute.ProjectTemplateMatchHistoryDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.ProjectTemplateMatchHistoryMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeOwnerSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ImpactMatchHistoryCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.InitialMatchHistoryCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.MatchSourceMetadata;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/** PM-07历史唯一写入服务；只追加，不提供更新或删除能力。 */
@Service
public class ProjectTemplateMatchHistoryService {

    public static final String INPUT_MANUAL = "MANUAL";
    public static final String INPUT_SOURCE = "SOURCE";

    @Resource
    private ProjectTemplateMatchHistoryMapper historyMapper;

    public ProjectTemplateMatchHistoryDO appendInitial(InitialMatchHistoryCommand command) {
        validateCommon(command == null ? null : command.tenantId(), command == null ? null : command.projectId(),
                command == null ? null : command.operatorId(), command == null ? null : command.changeReason(),
                command == null ? null : command.occurredAt(), command == null ? null : command.idempotencyKey(),
                command == null ? null : command.requestDigest(), command == null ? null : command.operationId());
        if (command.attributes() == null || command.attributeOwners() == null
                || command.frozenTemplateRevisionId() == null
                || !Set.of(INPUT_MANUAL, INPUT_SOURCE).contains(command.inputOrigin())) {
            throw new IllegalArgumentException("首次模板匹配历史命令不完整");
        }
        ProjectAttributeSnapshot attributes = INPUT_MANUAL.equals(command.inputOrigin())
                ? TemplateMatchDecisionRules.requireManualCreationAttributes(command.attributes())
                : TemplateMatchDecisionRules.requireCommonAttributes(command.attributes());
        TemplateMatchDecisionRules.requireOwners(command.attributeOwners());
        TemplateMatchDecisionRules.validateInitialDecision(command.decision());
        if (!command.frozenTemplateRevisionId().equals(command.decision().matchedTemplateRevisionId())) {
            throw new IllegalArgumentException("首次匹配决策与冻结模板修订不一致");
        }
        validateSource(command.inputOrigin(), command.source());

        ProjectTemplateMatchHistoryDO row = baseRow(command.tenantId(), command.projectId(),
                attributes, command.attributeOwners(), command.decision(),
                command.frozenTemplateRevisionId(), command.inputOrigin(), command.source(),
                command.operatorId(), command.changeReason(), command.occurredAt(), command.idempotencyKey(),
                command.requestDigest(), command.operationId(), command.traceId(), command.auditLogId());
        row.setTriggerType(TemplateMatchDecisionRules.TRIGGER_INITIAL);
        row.setRecordPurpose(TemplateMatchDecisionRules.PURPOSE_CREATE);
        row.setImpactResult(TemplateMatchDecisionRules.IMPACT_NOT_APPLICABLE);
        row.setDecisionMode(command.decision().decisionMode());
        historyMapper.insert(row);
        return row;
    }

    public ProjectTemplateMatchHistoryDO appendImpact(ImpactMatchHistoryCommand command) {
        validateCommon(command == null ? null : command.tenantId(), command == null ? null : command.projectId(),
                command == null ? null : command.operatorId(), command == null ? null : command.changeReason(),
                command == null ? null : command.occurredAt(), command == null ? null : command.idempotencyKey(),
                command == null ? null : command.requestDigest(), command == null ? null : command.operationId());
        if (command.beforeAttributes() == null || command.attributes() == null
                || command.attributeOwners() == null || command.frozenTemplateRevisionId() == null
                || !Set.of(TemplateMatchDecisionRules.TRIGGER_SOURCE,
                        TemplateMatchDecisionRules.TRIGGER_MANUAL).contains(command.triggerType())) {
            throw new IllegalArgumentException("模板影响历史命令不完整");
        }
        ProjectAttributeSnapshot attributes = TemplateMatchDecisionRules.requireCommonAttributes(command.attributes());
        TemplateMatchDecisionRules.requireOwners(command.attributeOwners());
        if (TemplateMatchDecisionRules.TRIGGER_SOURCE.equals(command.triggerType())
                != INPUT_SOURCE.equals(command.inputOrigin())) {
            throw new IllegalArgumentException("影响历史触发类型与输入来源不一致");
        }
        TemplateMatchDecisionRules.validateImpactDecision(command.decision());
        validateSource(command.inputOrigin(), command.source());

        ProjectTemplateMatchHistoryDO row = baseRow(command.tenantId(), command.projectId(),
                attributes, command.attributeOwners(), command.decision(),
                command.frozenTemplateRevisionId(), command.inputOrigin(), command.source(),
                command.operatorId(), command.changeReason(), command.occurredAt(), command.idempotencyKey(),
                command.requestDigest(), command.operationId(), command.traceId(), command.auditLogId());
        row.setTriggerType(command.triggerType());
        row.setRecordPurpose(TemplateMatchDecisionRules.PURPOSE_IMPACT);
        row.setBeforeAttributeSnapshot(JsonUtils.toJsonString(command.beforeAttributes()));
        row.setDecisionMode(null);
        row.setImpactResult(TemplateMatchDecisionRules.impactResult(
                command.decision(), command.frozenTemplateRevisionId()));
        historyMapper.insert(row);
        return row;
    }

    private ProjectTemplateMatchHistoryDO baseRow(
            Long tenantId, Long projectId,
            ProjectAttributeSnapshot attributes, ProjectAttributeOwnerSnapshot attributeOwners,
            TemplateMatchDecision decision,
            Long frozenTemplateRevisionId, String inputOrigin, MatchSourceMetadata source,
            Long operatorId, String changeReason, LocalDateTime occurredAt,
            String idempotencyKey, String requestDigest, String operationId,
            String traceId, Long auditLogId) {
        ProjectTemplateMatchHistoryDO row = new ProjectTemplateMatchHistoryDO();
        row.setTenantId(tenantId);
        row.setProjectId(projectId);
        row.setInputOrigin(inputOrigin);
        row.setSnapshotSchemaVersion(TemplateMatchDecisionRules.SNAPSHOT_SCHEMA_VERSION);
        row.setAttributeSnapshot(JsonUtils.toJsonString(attributes));
        row.setAttributeOwnerSnapshot(JsonUtils.toJsonString(attributeOwners));
        applySource(row, inputOrigin, source);
        row.setMatcherVersion(decision.matcherVersion());
        row.setMatchResult(decision.matchResult());
        row.setCandidateDigest(decision.candidateDigest());
        row.setMatchedTemplateId(decision.matchedTemplateId());
        row.setMatchedTemplateRevisionId(decision.matchedTemplateRevisionId());
        row.setFrozenTemplateRevisionId(frozenTemplateRevisionId);
        row.setOperatorId(operatorId);
        row.setChangeReason(TemplateMatchDecisionRules.requireReason(changeReason));
        row.setOccurredAt(occurredAt);
        row.setRecordedAt(LocalDateTime.now());
        row.setIdempotencyKey(idempotencyKey);
        row.setRequestDigest(requestDigest);
        row.setOperationId(operationId);
        row.setTraceId(blankToNull(traceId));
        row.setAuditLogId(auditLogId);
        return row;
    }

    private void applySource(ProjectTemplateMatchHistoryDO row, String inputOrigin, MatchSourceMetadata source) {
        if (INPUT_MANUAL.equals(inputOrigin)) {
            row.setSourceSystem(INPUT_MANUAL);
            return;
        }
        row.setSourceOwner(source.sourceOwner());
        row.setSourceSystem(source.sourceSystem());
        row.setSourceKey(source.sourceKey());
        row.setSourceEventId(source.sourceEventId());
        row.setSourceVersion(source.sourceVersion());
        row.setSourceOccurredAt(source.sourceOccurredAt());
        row.setSourceValueDigest(source.sourceValueDigest());
        row.setMappingVersion(source.mappingVersion());
    }

    private void validateSource(String inputOrigin, MatchSourceMetadata source) {
        if (INPUT_MANUAL.equals(inputOrigin)) {
            if (source != null) {
                throw new IllegalArgumentException("手工输入不得携带来源系统证据");
            }
            return;
        }
        if (!INPUT_SOURCE.equals(inputOrigin) || source == null || isBlank(source.sourceOwner())
                || isBlank(source.sourceSystem()) || isBlank(source.sourceKey())
                || isBlank(source.sourceEventId()) || isBlank(source.sourceVersion())
                || source.sourceOccurredAt() == null || isBlank(source.sourceValueDigest())
                || isBlank(source.mappingVersion())) {
            throw new IllegalArgumentException("来源匹配证据不完整");
        }
    }

    private void validateCommon(Long tenantId, Long projectId, Long operatorId, String reason,
                                LocalDateTime occurredAt, String idempotencyKey,
                                String requestDigest, String operationId) {
        if (tenantId == null || projectId == null || operatorId == null || occurredAt == null
                || isBlank(idempotencyKey) || isBlank(requestDigest) || isBlank(operationId)) {
            throw new IllegalArgumentException("模板匹配历史公共证据不完整");
        }
        TemplateMatchDecisionRules.requireReason(reason);
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
