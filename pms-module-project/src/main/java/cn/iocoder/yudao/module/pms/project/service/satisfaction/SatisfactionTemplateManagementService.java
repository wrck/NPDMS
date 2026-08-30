package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTemplateApplicabilityQuery;
import cn.iocoder.yudao.module.pms.project.domain.satisfaction.SatisfactionQuestionnaireDefinition;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SatisfactionTemplateManagementService {

    private final SatisfactionQuestionnaireTemplateMapper templateMapper;
    private final SatisfactionQuestionnaireTemplateRevisionMapper revisionMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;

    public List<TemplateView> list(Long tenantId) {
        return templateMapper.selectListByTenant(tenantId).stream()
                .map(root -> new TemplateView(root.getId(), root.getTemplateCode(), root.getName(), root.getStatus(),
                        root.getCurrentRevisionId(), root.getVersion(), revisionMapper
                        .selectListByTemplate(tenantId, root.getId()).stream().map(this::view).toList()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateView create(Long tenantId, Long actorUserId, CreateTemplate command) {
        requireText(command.templateCode(), "templateCode");
        requireText(command.name(), "name");
        if (templateMapper.selectListByTenant(tenantId).stream()
                .anyMatch(row -> row.getTemplateCode().equals(command.templateCode()))) {
            throw new IllegalStateException("SATISFACTION_TEMPLATE_CODE_CONFLICT");
        }
        SatisfactionQuestionnaireTemplateDO row = new SatisfactionQuestionnaireTemplateDO();
        row.setId(IdWorker.getId()); row.setTenantId(tenantId); row.setTemplateCode(command.templateCode());
        row.setName(command.name()); row.setStatus("DRAFT"); row.setVersion(0);
        row.setCreator(String.valueOf(actorUserId)); row.setUpdater(String.valueOf(actorUserId));
        templateMapper.insert(row);
        return new TemplateView(row.getId(), row.getTemplateCode(), row.getName(), row.getStatus(), null, 0,
                List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public RevisionView createRevision(Long tenantId, Long actorUserId, Long templateId, CreateRevision command) {
        SatisfactionQuestionnaireTemplateDO root = requireRoot(tenantId, templateId);
        validateDraft(command);
        int revisionNo = revisionMapper.selectListByTemplate(tenantId, templateId).stream()
                .map(SatisfactionQuestionnaireTemplateRevisionDO::getRevisionNo).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        SatisfactionQuestionnaireTemplateRevisionDO row = new SatisfactionQuestionnaireTemplateRevisionDO();
        row.setId(IdWorker.getId()); row.setTenantId(tenantId); row.setTemplateId(root.getId());
        row.setRevisionNo(revisionNo); row.setProjectType(command.projectType());
        row.setSigningMode(command.signingMode()); row.setImplementationMode(command.implementationMode());
        row.setBusinessPurposeCode(command.businessPurposeCode());
        row.setApplicableTimingCode(command.applicableTimingCode()); row.setPriority(command.priority());
        row.setFrozenQuestionJson(command.questionnaireJson()); row.setFrozenThreshold(command.threshold());
        row.setRuleVersion(command.ruleVersion()); row.setRevisionStatus("DRAFT"); row.setVersion(0);
        row.setCreator(String.valueOf(actorUserId)); row.setUpdater(String.valueOf(actorUserId));
        revisionMapper.insert(row);
        return view(row);
    }

    public PublishResult publish(Long tenantId, Long actorUserId, Long templateId, Long revisionId,
                                 Integer expectedRevisionVersion, String operationId) {
        if (expectedRevisionVersion == null || expectedRevisionVersion < 0) {
            throw new IllegalArgumentException("SATISFACTION_TEMPLATE_VERSION_INVALID");
        }
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        tenantId, "ACC_SATISFACTION_TEMPLATE_PUBLISH", actorUserId, operationId),
                digest(templateId, revisionId, expectedRevisionVersion), PublishResult.class,
                () -> publishOnce(tenantId, actorUserId, templateId, revisionId, expectedRevisionVersion),
                result -> new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_TEMPLATE_PUBLISHED",
                        "SatisfactionQuestionnaireTemplate", String.valueOf(templateId), operationId,
                        JsonUtils.toJsonString(result), List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new IllegalStateException("SATISFACTION_TEMPLATE_IDEMPOTENCY_CONFLICT");
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null) {
            throw new IllegalStateException("SATISFACTION_TEMPLATE_PUBLISH_IN_PROGRESS");
        }
        PublishResult result = execution.response();
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new PublishResult(result.templateId(), result.revisionId(), result.revisionNo(), result.rootVersion(), true)
                : result;
    }

    @Transactional(rollbackFor = Exception.class)
    protected PublishResult publishOnce(Long tenantId, Long actorUserId, Long templateId, Long revisionId,
                                        Integer expectedRevisionVersion) {
        SatisfactionQuestionnaireTemplateDO root = requireRoot(tenantId, templateId);
        SatisfactionQuestionnaireTemplateRevisionDO revision = revisionMapper.selectByIdForUpdate(tenantId, revisionId);
        if (revision == null || !templateId.equals(revision.getTemplateId())
                || !"DRAFT".equals(revision.getRevisionStatus())
                || !expectedRevisionVersion.equals(revision.getVersion())) {
            throw new IllegalStateException("SATISFACTION_TEMPLATE_REVISION_CONFLICT");
        }
        var definition = SatisfactionQuestionnaireDefinition.parse(revision.getFrozenQuestionJson());
        if (!definition.ruleVersion().equals(revision.getRuleVersion())
                || definition.threshold().compareTo(revision.getFrozenThreshold()) != 0) {
            throw new IllegalStateException("SATISFACTION_TEMPLATE_PROJECTION_CONFLICT");
        }
        var applicability = new SatisfactionTemplateApplicabilityQuery(tenantId, revision.getProjectType(),
                revision.getSigningMode(), revision.getImplementationMode(), revision.getBusinessPurposeCode(),
                revision.getApplicableTimingCode(), revision.getPriority());
        if (revisionMapper.selectPublishedByApplicability(applicability).stream()
                .anyMatch(other -> !other.getId().equals(revisionId))) {
            throw new IllegalStateException("SATISFACTION_TEMPLATE_APPLICABILITY_AMBIGUOUS");
        }
        LocalDateTime now = LocalDateTime.now();
        if (root.getCurrentRevisionId() != null) {
            SatisfactionQuestionnaireTemplateRevisionDO previous = revisionMapper.selectByIdForUpdate(
                    tenantId, root.getCurrentRevisionId());
            if (previous != null && "PUBLISHED".equals(previous.getRevisionStatus())) {
                previous.setRevisionStatus("SUPERSEDED"); previous.setEffectiveTo(now);
                previous.setVersion(previous.getVersion() + 1); previous.setUpdater(String.valueOf(actorUserId));
                revisionMapper.updateById(previous);
            }
        }
        revision.setRevisionStatus("PUBLISHED"); revision.setEffectiveFrom(now);
        revision.setVersion(revision.getVersion() + 1); revision.setUpdater(String.valueOf(actorUserId));
        revisionMapper.updateById(revision);
        root.setStatus("PUBLISHED"); root.setCurrentRevisionId(revisionId); root.setVersion(root.getVersion() + 1);
        root.setUpdater(String.valueOf(actorUserId)); templateMapper.updateById(root);
        return new PublishResult(templateId, revisionId, revision.getRevisionNo(), root.getVersion(), false);
    }

    private SatisfactionQuestionnaireTemplateDO requireRoot(Long tenantId, Long templateId) {
        SatisfactionQuestionnaireTemplateDO root = templateMapper.selectByIdForUpdate(tenantId, templateId);
        if (root == null) throw new IllegalStateException("SATISFACTION_TEMPLATE_NOT_FOUND");
        return root;
    }

    private void validateDraft(CreateRevision command) {
        requireText(command.projectType(), "projectType"); requireText(command.signingMode(), "signingMode");
        requireText(command.implementationMode(), "implementationMode");
        requireText(command.businessPurposeCode(), "businessPurposeCode");
        requireText(command.applicableTimingCode(), "applicableTimingCode");
        requireText(command.questionnaireJson(), "questionnaireJson"); requireText(command.ruleVersion(), "ruleVersion");
        if (command.priority() == null || command.threshold() == null) {
            throw new IllegalArgumentException("SATISFACTION_TEMPLATE_DRAFT_INVALID");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private RevisionView view(SatisfactionQuestionnaireTemplateRevisionDO row) {
        return new RevisionView(row.getId(), row.getRevisionNo(), row.getProjectType(), row.getSigningMode(),
                row.getImplementationMode(), row.getBusinessPurposeCode(), row.getApplicableTimingCode(),
                row.getPriority(), row.getFrozenQuestionJson(), row.getFrozenThreshold(), row.getRuleVersion(),
                row.getRevisionStatus(), row.getEffectiveFrom(), row.getEffectiveTo(), row.getVersion());
    }

    private String digest(Long templateId, Long revisionId, Integer version) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (templateId + ":" + revisionId + ":" + version).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record CreateTemplate(String templateCode, String name) {}
    public record CreateRevision(String projectType, String signingMode, String implementationMode,
                                 String businessPurposeCode, String applicableTimingCode, Integer priority,
                                 String questionnaireJson, java.math.BigDecimal threshold, String ruleVersion) {}
    public record TemplateView(Long id, String templateCode, String name, String status, Long currentRevisionId,
                               Integer version, List<RevisionView> revisions) {}
    public record RevisionView(Long id, Integer revisionNo, String projectType, String signingMode,
                               String implementationMode, String businessPurposeCode, String applicableTimingCode,
                               Integer priority, String questionnaireJson, java.math.BigDecimal threshold,
                               String ruleVersion, String status, LocalDateTime effectiveFrom,
                               LocalDateTime effectiveTo, Integer version) {}
    public record PublishResult(Long templateId, Long revisionId, Integer revisionNo,
                                Integer rootVersion, boolean replayed) {}
}
