package cn.iocoder.yudao.module.pms.project.service.template;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateTaskDefinitionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateApplicability;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateSnapshot;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateTaskDefinitionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.ProjectTemplateRevisionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateCandidateResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCriteria;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CANDIDATE_AMBIGUOUS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CANDIDATE_CHANGED;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CANDIDATE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CRITERIA_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_ENABLED;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_SNAPSHOT_INVALID;

/** PM-01 / PM-03 已发布模板候选与受控预览。 */
@Service
@Validated
public class ProjectTemplateCandidateServiceImpl implements ProjectTemplateCandidateService {

    private static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    private static final String TASK_NATIVE = "TASK_NATIVE";

    private final ProjectTemplateMapper templateMapper;
    private final ProjectTemplateTaskDefinitionMapper taskDefinitionMapper;

    public ProjectTemplateCandidateServiceImpl(ProjectTemplateMapper templateMapper,
                                               ProjectTemplateTaskDefinitionMapper taskDefinitionMapper) {
        this.templateMapper = templateMapper;
        this.taskDefinitionMapper = taskDefinitionMapper;
    }

    @Override
    public TemplateCandidateResult findCandidates(long tenantId, long actorId, TemplateMatchCriteria criteria) {
        validateRequestContext(tenantId, actorId, criteria);
        List<ProjectTemplateDO> revisions = readCandidates(tenantId, criteria);
        return new TemplateCandidateResult(revisions.stream().map(this::toCandidate).toList(), watermark(revisions));
    }

    @Override
    public ProjectTemplateRevisionSnapshot getPreview(long tenantId, long actorId, long revisionId,
                                                      TemplateMatchCriteria criteria) {
        validateRequestContext(tenantId, actorId, criteria);
        ProjectTemplateDO revision = readCandidates(tenantId, criteria).stream()
                .filter(item -> Objects.equals(item.getId(), revisionId))
                .findFirst().orElseThrow(() -> exception(PROJECT_TEMPLATE_NOT_ENABLED));
        return toPreview(tenantId, revision);
    }

    @Override
    public ProjectTemplateRevisionSnapshot resolveForCreate(long tenantId, long actorId, Long selectedRevisionId,
                                                             TemplateMatchCriteria criteria,
                                                             String candidateWatermark) {
        validateRequestContext(tenantId, actorId, criteria);
        List<ProjectTemplateDO> revisions = readCandidates(tenantId, criteria);
        if (!Objects.equals(watermark(revisions), candidateWatermark)) {
            throw exception(PROJECT_TEMPLATE_CANDIDATE_CHANGED);
        }
        ProjectTemplateDO selected;
        if (selectedRevisionId != null) {
            selected = revisions.stream().filter(item -> Objects.equals(item.getId(), selectedRevisionId))
                    .findFirst().orElseThrow(() -> exception(PROJECT_TEMPLATE_NOT_ENABLED));
        } else {
            List<ProjectTemplateDO> defaults = highestPriorityDefaults(revisions);
            if (defaults.isEmpty()) {
                throw exception(PROJECT_TEMPLATE_CANDIDATE_NOT_FOUND);
            }
            if (defaults.size() > 1) {
                throw exception(PROJECT_TEMPLATE_CANDIDATE_AMBIGUOUS);
            }
            selected = defaults.getFirst();
        }
        return toPreview(tenantId, selected);
    }

    private List<ProjectTemplateDO> readCandidates(long tenantId, TemplateMatchCriteria criteria) {
        return templateMapper.selectPublishedCandidates(tenantId, criteria.businessSceneCode(), LocalDateTime.now())
                .stream()
                .filter(revision -> Objects.equals(revision.getTenantId(), tenantId))
                .filter(revision -> matches(revision.getApplicabilitySnapshot(), criteria))
                .toList();
    }

    private boolean matches(TemplateApplicability applicability, TemplateMatchCriteria criteria) {
        if (applicability == null || !Integer.valueOf(1).equals(applicability.getSchemaVersion())) {
            return false;
        }
        String majorLevel = criteria.majorProjectLevelCode() == null
                || criteria.majorProjectLevelCode().isBlank() ? NOT_APPLICABLE : criteria.majorProjectLevelCode();
        return contains(applicability.getSigningMethodCodes(), criteria.signingMethodCode())
                && contains(applicability.getProjectCategoryCodes(), criteria.projectCategoryCode())
                && contains(applicability.getImplementationModeCodes(), criteria.implementationModeCode())
                && contains(applicability.getMajorProjectLevelCodes(), majorLevel);
    }

    private boolean contains(Set<String> values, String value) {
        return values != null && values.contains(value);
    }

    private List<ProjectTemplateDO> highestPriorityDefaults(List<ProjectTemplateDO> revisions) {
        int priority = revisions.stream().filter(item -> Boolean.TRUE.equals(item.getDefaultFlag()))
                .map(ProjectTemplateDO::getMatchPriority).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(Integer.MIN_VALUE);
        return revisions.stream().filter(item -> Boolean.TRUE.equals(item.getDefaultFlag()))
                .filter(item -> Objects.equals(item.getMatchPriority(), priority)).toList();
    }

    private TemplateCandidate toCandidate(ProjectTemplateDO revision) {
        return new TemplateCandidate(revision.getId(), revision.getTemplateId(), revision.getCode(),
                revision.getRevisionNo(), revision.getName(), revision.getMatchPriority(),
                Boolean.TRUE.equals(revision.getDefaultFlag()), revision.getContentSha256());
    }

    private ProjectTemplateRevisionSnapshot toPreview(long tenantId, ProjectTemplateDO revision) {
        TemplateSnapshot snapshot = revision.getSnapshotJson();
        List<ProjectTemplateTaskDefinitionDO> tasks = taskDefinitionMapper.selectByRevisionId(tenantId, revision.getId());
        validateSnapshot(snapshot, tasks);
        Map<String, List<ProjectTemplateRevisionSnapshot.TaskSummary>> tasksByStage = new HashMap<>();
        for (ProjectTemplateTaskDefinitionDO task : tasks) {
            tasksByStage.computeIfAbsent(task.getStageDefinitionKey(), ignored -> new ArrayList<>()).add(
                    new ProjectTemplateRevisionSnapshot.TaskSummary(task.getTaskDefinitionKey(), task.getName(),
                            task.getParentTaskDefinitionKey(), valueOrZero(task.getSortOrder()),
                            task.getWorkBindingTypeCode()));
        }
        List<ProjectTemplateRevisionSnapshot.StageSummary> stages = snapshot.getStages().stream()
                .sorted(Comparator.comparingInt(stage -> valueOrZero(stage.getSortOrder())))
                .map(stage -> new ProjectTemplateRevisionSnapshot.StageSummary(stage.getStageCode(),
                        stage.getStageName(), valueOrZero(stage.getSortOrder()),
                        tasksByStage.getOrDefault(stage.getStageCode(), List.of())))
                .toList();
        List<ProjectTemplateRevisionSnapshot.MilestoneSummary> milestones = safe(snapshot.getMilestones()).stream()
                .map(item -> new ProjectTemplateRevisionSnapshot.MilestoneSummary(item.getMilestoneKey(),
                        item.getMilestoneName(), item.getStageCode())).toList();
        List<ProjectTemplateRevisionSnapshot.DeliverableSummary> deliverables = safe(snapshot.getDeliverables()).stream()
                .map(item -> new ProjectTemplateRevisionSnapshot.DeliverableSummary(item.getRequirementKey(),
                        item.getDeliverableName(), item.getStageCode(), Boolean.TRUE.equals(item.getRequired()))).toList();
        List<ProjectTemplateRevisionSnapshot.GateSummary> gates = safe(snapshot.getGates()).stream()
                .map(item -> new ProjectTemplateRevisionSnapshot.GateSummary(item.getGateKey(), item.getGateName(),
                        item.getStageCode())).toList();
        return new ProjectTemplateRevisionSnapshot(revision.getId(), revision.getTemplateId(), revision.getCode(),
                revision.getRevisionNo(), revision.getName(), revision.getWorkflowDefinitionKey(),
                revision.getWorkflowDefinitionVersion(), stages, milestones, deliverables, gates);
    }

    private void validateSnapshot(TemplateSnapshot snapshot, List<ProjectTemplateTaskDefinitionDO> tasks) {
        if (snapshot == null || !Integer.valueOf(1).equals(snapshot.getSchemaVersion())
                || snapshot.getStages() == null || snapshot.getStages().isEmpty()) {
            invalid("仅支持包含Stage定义的schemaVersion=1快照");
        }
        Set<String> stageKeys = unique(snapshot.getStages().stream().map(TemplateSnapshot.StageDef::getStageCode).toList(),
                "Stage编码");
        if (!stageKeys.contains("S0")) {
            invalid("缺少S0阶段");
        }
        Set<String> gateKeys = unique(safe(snapshot.getGates()).stream().map(TemplateSnapshot.GateDef::getGateKey).toList(),
                "GateRef");
        unique(safe(snapshot.getMilestones()).stream().map(TemplateSnapshot.MilestoneDef::getMilestoneKey).toList(),
                "里程碑编码");
        unique(safe(snapshot.getDeliverables()).stream().map(TemplateSnapshot.DeliverableDef::getRequirementKey).toList(),
                "交付件编码");
        Set<String> taskKeys = unique(tasks.stream().map(ProjectTemplateTaskDefinitionDO::getTaskDefinitionKey).toList(),
                "任务编码");
        if (taskKeys.isEmpty()) {
            invalid("至少需要一个任务定义");
        }
        for (TemplateSnapshot.MilestoneDef milestone : safe(snapshot.getMilestones())) {
            validateStageReference(stageKeys, milestone.getStageCode(), "里程碑", milestone.getMilestoneKey());
        }
        for (TemplateSnapshot.DeliverableDef deliverable : safe(snapshot.getDeliverables())) {
            validateStageReference(stageKeys, deliverable.getStageCode(), "交付件", deliverable.getRequirementKey());
        }
        for (TemplateSnapshot.GateDef gate : safe(snapshot.getGates())) {
            validateStageReference(stageKeys, gate.getStageCode(), "门禁", gate.getGateKey());
        }
        for (ProjectTemplateTaskDefinitionDO task : tasks) {
            if (!stageKeys.contains(task.getStageDefinitionKey())) {
                invalid("任务引用不存在的Stage：" + task.getTaskDefinitionKey());
            }
            if (task.getParentTaskDefinitionKey() != null && !taskKeys.contains(task.getParentTaskDefinitionKey())) {
                invalid("任务引用不存在的父任务：" + task.getTaskDefinitionKey());
            }
            if (isBlank(task.getPermissionPolicyRef()) || isBlank(task.getCompletionRuleTypeCode())
                    || task.getCompletionRuleConfig() == null || !task.getCompletionRuleConfig().isObject()) {
                invalid("任务权限策略或完成规则不可解析：" + task.getTaskDefinitionKey());
            }
            if (TASK_NATIVE.equals(task.getWorkBindingTypeCode())) {
                if (!isBlank(task.getTargetContextCode()) || !isBlank(task.getTargetObjectType())
                        || !isBlank(task.getTargetObjectKey())) {
                    invalid("TASK_NATIVE不得配置外部目标：" + task.getTaskDefinitionKey());
                }
            } else if (isBlank(task.getWorkBindingTypeCode()) || isBlank(task.getTargetContextCode())
                    || isBlank(task.getTargetObjectType()) || isBlank(task.getTargetObjectKey())) {
                invalid("非TASK_NATIVE任务必须配置受控目标：" + task.getTaskDefinitionKey());
            }
            if (!isBlank(task.getGateRef()) && !gateKeys.contains(task.getGateRef())) {
                invalid("任务GateRef失效：" + task.getTaskDefinitionKey());
            }
        }
    }

    private void validateStageReference(Set<String> stageKeys, String stageCode, String type, String key) {
        if (!stageKeys.contains(stageCode)) {
            invalid(type + "引用不存在的Stage：" + key);
        }
    }

    private Set<String> unique(List<String> values, String label) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (isBlank(value) || !result.add(value)) {
                invalid(label + "为空或重复");
            }
        }
        return result;
    }

    private void validateRequestContext(long tenantId, long actorId, TemplateMatchCriteria criteria) {
        if (tenantId <= 0 || actorId <= 0 || criteria == null || isBlank(criteria.signingMethodCode())
                || isBlank(criteria.projectCategoryCode()) || isBlank(criteria.implementationModeCode())
                || isBlank(criteria.businessSceneCode()) || criteria.customerId() <= 0 || criteria.officeId() <= 0
                || criteria.implementationLocationId() <= 0) {
            throw exception(PROJECT_TEMPLATE_CRITERIA_INVALID, "tenant、actor、四维条件、场景和范围ID均必须有效");
        }
    }

    private String watermark(List<ProjectTemplateDO> revisions) {
        String source = revisions.stream().sorted(Comparator.comparing(ProjectTemplateDO::getId))
                .map(item -> item.getId() + ":" + item.getContentSha256()).reduce("", String::concat);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void invalid(String reason) {
        throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, reason);
    }
}
