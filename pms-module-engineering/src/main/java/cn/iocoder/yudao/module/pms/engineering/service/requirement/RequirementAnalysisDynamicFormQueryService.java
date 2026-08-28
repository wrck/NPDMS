package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationCursorPageRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisCompareRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisCompletionBlockerRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisVersionRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisWorkspaceRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisHistoryQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormOwnerKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_NOT_EXISTS;

/** PRE-04动态表单组合查询；正文、Schema和文件事实只从PLATFORM读取。 */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisDynamicFormQueryService {

    private static final DynamicFormProviderKey PROVIDER = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String FILE_PURPOSE_PREFIX = "FORM_FIELD_ATTACHMENT/";

    private final RequirementAnalysisRootMapper rootMapper;
    private final DynamicFormBusinessInstanceApi dynamicFormApi;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;

    public boolean owns(Long preparationId, Long tenantId) {
        return preparationId != null && tenantId != null
                && rootMapper.selectById(new RequirementAnalysisRowQuery(tenantId, preparationId)) != null;
    }

    public RequirementAnalysisWorkspaceRespVO getWorkspace(Long projectId, Actor actor) {
        requireRead(actor, projectId);
        RequirementAnalysisProjectQuery query = new RequirementAnalysisProjectQuery(actor.tenantId(), projectId);
        PreparationDO effective = rootMapper.selectEffective(query);
        PreparationDO draft = rootMapper.selectDraft(query);
        boolean manager = isManager(actor, projectId, false);
        RequirementAnalysisWorkspaceRespVO response = new RequirementAnalysisWorkspaceRespVO();
        response.setProjectId(projectId);
        response.setCurrentEffective(effective == null ? null : toVersion(effective, actor));
        response.setDraft(draft == null || !manager ? null : toVersion(draft, actor));
        ProjectWorkBindingFact binding = currentBinding(projectId);
        boolean bindingUsable = effective == null ? binding != null : projectBindingMatches(effective, binding);
        boolean canCreate = manager && bindingUsable && (effective != null || isManager(actor, projectId, true));
        response.setAllowedActions(canCreate && draft == null
                ? List.of(effective == null ? "CREATE_INITIAL_DRAFT" : "CREATE_DRAFT") : List.of());
        return response;
    }

    public RequirementAnalysisVersionRespVO getDetail(Long preparationId, Actor actor) {
        PreparationDO root = rootMapper.selectById(new RequirementAnalysisRowQuery(actor.tenantId(), preparationId));
        requireVisible(root, actor);
        if ("DRAFT".equals(root.getStatusCode()) && !isManager(actor, root.getProjectId(), false)) {
            throw exception(FORBIDDEN);
        }
        return toVersion(root, actor);
    }

    public PreparationCursorPageRespVO<RequirementAnalysisVersionRespVO> getHistory(
            Long projectId, PreparationPageReqVO request, Actor actor) {
        requireRead(actor, projectId);
        Cursor cursor = parseCursor(request == null ? null : request.getCursor());
        int size = pageSize(request == null ? null : request.getPageSize());
        List<PreparationDO> fetched = rootMapper.selectCompletedHistory(new RequirementAnalysisHistoryQuery(
                actor.tenantId(), projectId, cursor.businessVersion(), cursor.id(), size + 1));
        boolean hasMore = fetched.size() > size;
        List<PreparationDO> page = hasMore ? fetched.subList(0, size) : fetched;
        return new PreparationCursorPageRespVO<>(page.stream().map(this::toHistorySummary).toList(),
                hasMore ? cursor(page.getLast()) : null, hasMore);
    }

    public RequirementAnalysisCompareRespVO compare(Long sourceId, Long targetId, Actor actor) {
        PreparationDO source = rootMapper.selectById(new RequirementAnalysisRowQuery(actor.tenantId(), sourceId));
        PreparationDO target = rootMapper.selectById(new RequirementAnalysisRowQuery(actor.tenantId(), targetId));
        requireVisible(source, actor);
        requireVisible(target, actor);
        if (Objects.equals(sourceId, targetId) || !Objects.equals(source.getProjectId(), target.getProjectId())) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
        boolean bothCompleted = "COMPLETED".equals(source.getStatusCode())
                && "COMPLETED".equals(target.getStatusCode());
        boolean sourceDraftFromTarget = "DRAFT".equals(source.getStatusCode())
                && "COMPLETED".equals(target.getStatusCode())
                && Objects.equals(source.getSourcePreparationId(), target.getId());
        boolean targetDraftFromSource = "DRAFT".equals(target.getStatusCode())
                && "COMPLETED".equals(source.getStatusCode())
                && Objects.equals(target.getSourcePreparationId(), source.getId());
        if (!bothCompleted && !sourceDraftFromTarget && !targetDraftFromSource) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
        if (("DRAFT".equals(source.getStatusCode()) || "DRAFT".equals(target.getStatusCode()))
                && !isManager(actor, source.getProjectId(), false)) throw exception(FORBIDDEN);
        DynamicFormInstanceFact left = inspect(source, actor, DynamicFormBusinessAction.READ);
        DynamicFormInstanceFact right = inspect(target, actor, DynamicFormBusinessAction.READ);
        Map<String, List<FileArtifactVersionFact>> leftFiles = files(left);
        Map<String, List<FileArtifactVersionFact>> rightFiles = files(right);
        Set<String> keys = new LinkedHashSet<>();
        left.fields().forEach(field -> keys.add(field.fieldKey()));
        right.fields().forEach(field -> keys.add(field.fieldKey()));
        List<RequirementAnalysisCompareRespVO.FieldDifference> differences = keys.stream().sorted().map(key -> {
            boolean leftPresent = left.fields().stream().anyMatch(field -> key.equals(field.fieldKey()));
            boolean rightPresent = right.fields().stream().anyMatch(field -> key.equals(field.fieldKey()));
            Object leftValue = left.ordinaryValues().get(key);
            Object rightValue = right.ordinaryValues().get(key);
            boolean fileChanged = !Objects.equals(leftFiles.getOrDefault(key, List.of()),
                    rightFiles.getOrDefault(key, List.of()));
            String change = !leftPresent ? "ADDED" : !rightPresent ? "REMOVED"
                    : Objects.equals(leftValue, rightValue) && !fileChanged ? "UNCHANGED" : "CHANGED";
            return new RequirementAnalysisCompareRespVO.FieldDifference(key, key, change,
                    leftValue, rightValue, fileChanged);
        }).toList();
        RequirementAnalysisCompareRespVO response = new RequirementAnalysisCompareRespVO();
        response.setSourcePreparationId(sourceId);
        response.setSourceBusinessVersion(source.getBusinessVersion());
        response.setTargetPreparationId(targetId);
        response.setTargetBusinessVersion(target.getBusinessVersion());
        response.setFields(differences);
        response.setSections(List.of());
        return response;
    }

    private RequirementAnalysisVersionRespVO toVersion(PreparationDO root, Actor actor) {
        DynamicFormInstanceFact form = inspect(root, actor, DynamicFormBusinessAction.READ);
        boolean manager = isManager(actor, root.getProjectId(), false);
        boolean draft = manager && "DRAFT".equals(root.getStatusCode())
                && Integer.valueOf(1).equals(root.getDraftMarker());
        List<RequirementAnalysisCompletionBlockerRespVO> blockers = blockers(form);
        List<String> actions = new ArrayList<>();
        if (draft) {
            actions.add("PATCH_FORM");
            boolean completionStageAllowed = !Integer.valueOf(1).equals(root.getBusinessVersion())
                    || isManager(actor, root.getProjectId(), true);
            if (blockers.isEmpty() && completionStageAllowed
                    && bindingMatches(root, form, currentBinding(root.getProjectId()))) actions.add("COMPLETE");
        } else if (manager && "COMPLETED".equals(root.getStatusCode())
                && Integer.valueOf(1).equals(root.getEffectiveMarker())) {
            actions.add("CREATE_DRAFT");
        }
        RequirementAnalysisVersionRespVO response = new RequirementAnalysisVersionRespVO();
        response.setPreparationId(root.getId());
        response.setProjectId(root.getProjectId());
        response.setBusinessVersion(root.getBusinessVersion());
        response.setSourcePreparationId(root.getSourcePreparationId());
        response.setStatus(root.getStatusCode());
        response.setCurrentDraft(Integer.valueOf(1).equals(root.getDraftMarker()));
        response.setCurrentEffective(Integer.valueOf(1).equals(root.getEffectiveMarker()));
        response.setContentVersion(root.getContentVersion());
        response.setVersion(root.getVersion());
        response.setTemplateId(form.templateId());
        response.setTemplateRevisionId(form.templateRevisionId());
        response.setDynamicFormInstanceId(form.instanceId());
        response.setDynamicFormInstanceVersion(form.instanceVersion());
        response.setDynamicFormRevisionNo(form.templateRevisionNo());
        response.setEngineCode(form.engineCode());
        response.setDesignerVersion(form.designerVersion());
        response.setRendererVersion(form.rendererVersion());
        response.setFormConfJson(JsonUtils.parseObject(form.formConfJson(), Map.class));
        response.setFormRulesJson(JsonUtils.parseArray(form.formRulesJson(), Map.class));
        response.setValues(form.ordinaryValues());
        response.setControlledFiles(files(form));
        response.setDeclarativeValidationResult(form.validationFact() == null
                ? "UNKNOWN" : form.validationFact().result());
        response.setCompletedBy(root.getCompletedBy());
        response.setCompletedAt(root.getCompletedAt());
        response.setCreatedAt(root.getCreateTime());
        response.setAllowedActions(List.copyOf(actions));
        response.setCompletionBlockers(blockers);
        response.setSections(List.of());
        return response;
    }

    private RequirementAnalysisVersionRespVO toHistorySummary(PreparationDO root) {
        RequirementAnalysisVersionRespVO response = new RequirementAnalysisVersionRespVO();
        response.setPreparationId(root.getId());
        response.setProjectId(root.getProjectId());
        response.setBusinessVersion(root.getBusinessVersion());
        response.setSourcePreparationId(root.getSourcePreparationId());
        response.setStatus(root.getStatusCode());
        response.setCurrentDraft(Integer.valueOf(1).equals(root.getDraftMarker()));
        response.setCurrentEffective(Integer.valueOf(1).equals(root.getEffectiveMarker()));
        response.setContentVersion(root.getContentVersion());
        response.setVersion(root.getVersion());
        response.setDynamicFormInstanceId(root.getDynamicFormInstanceId());
        response.setCompletedBy(root.getCompletedBy());
        response.setCompletedAt(root.getCompletedAt());
        response.setCreatedAt(root.getCreateTime());
        response.setAllowedActions(List.of());
        response.setCompletionBlockers(List.of());
        response.setSections(List.of());
        return response;
    }

    private DynamicFormInstanceFact inspect(PreparationDO root, Actor actor, DynamicFormBusinessAction action) {
        if (root.getDynamicFormInstanceId() == null) throw exception(REQUIREMENT_NOT_EXISTS);
        return dynamicFormApi.inspectInstance(new DynamicFormInstanceQuery(actor.tenantId(), actor.actorId(),
                PROVIDER, new DynamicFormOwnerKey(PROVIDER.ownerContext(), PROVIDER.objectType(),
                String.valueOf(root.getId())), root.getDynamicFormInstanceId(), action));
    }

    private Map<String, List<FileArtifactVersionFact>> files(DynamicFormInstanceFact fact) {
        Map<String, List<FileArtifactVersionFact>> result = new LinkedHashMap<>();
        for (FileReferenceSetFact set : fact.controlledFileFacts()) {
            String purpose = set.key().purposeCode();
            String key = purpose.startsWith(FILE_PURPOSE_PREFIX)
                    ? purpose.substring(FILE_PURPOSE_PREFIX.length()) : purpose;
            result.put(key, set.activeFacts());
        }
        return Map.copyOf(result);
    }

    private List<RequirementAnalysisCompletionBlockerRespVO> blockers(DynamicFormInstanceFact form) {
        if (form.validationFact() == null) {
            return List.of(new RequirementAnalysisCompletionBlockerRespVO(
                    "FACT_PROVIDER_UNAVAILABLE", null, "动态表单事实不可用"));
        }
        return form.validationFact().blockerCodes().stream().map(raw -> {
            int separator = raw.indexOf(':');
            String code = separator < 0 ? raw : raw.substring(0, separator);
            String field = separator < 0 ? null : raw.substring(separator + 1);
            return new RequirementAnalysisCompletionBlockerRespVO(code, field, null);
        }).toList();
    }

    private void requireVisible(PreparationDO root, Actor actor) {
        if (root == null) throw exception(REQUIREMENT_NOT_EXISTS);
        requireRead(actor, root.getProjectId());
    }

    private void requireRead(Actor actor, Long projectId) {
        if (actor == null || !permissionApi.hasAnyPermissions(actor.actorId(),
                RequirementAnalysisQueryService.PERMISSION_QUERY, RequirementAnalysisQueryService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw exception(FORBIDDEN);
        }
    }

    private boolean isManager(Actor actor, Long projectId, boolean requireS1) {
        try {
            if (!permissionApi.hasAnyPermissions(actor.actorId(), RequirementAnalysisQueryService.PERMISSION_MANAGE)) {
                return false;
            }
            var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE));
            ProjectParticipantFact participant = participantFactApi.inspect(new ProjectParticipantFactQuery(
                    projectId, actor.actorId(), Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                    LocalDateTime.now()));
            return scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(projectId)
                    && participant != null && "ACTIVE".equals(participant.lifecycleStatus())
                    && (!requireS1 || "S1".equals(participant.currentStage()))
                    && participant.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private ProjectWorkBindingFact currentBinding(Long projectId) {
        try {
            ProjectWorkBindingFact binding = workBindingFactApi.inspect(new ProjectWorkBindingFactQuery(
                    projectId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
            return binding != null
                    && ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.workBindingTypeCode()
                    .equals(binding.workBindingTypeCode())
                    && ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetContextCode()
                    .equals(binding.targetContextCode())
                    && ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectType()
                    .equals(binding.targetObjectType())
                    && ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectKey()
                    .equals(binding.targetObjectKey()) ? binding : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private boolean projectBindingMatches(PreparationDO root, ProjectWorkBindingFact binding) {
        return binding != null && Objects.equals(root.getTemplateId(), binding.templateTaskDefinitionId())
                && Objects.equals(root.getTemplateRevisionId(), binding.templateRevisionId());
    }

    private boolean bindingMatches(PreparationDO root, DynamicFormInstanceFact form,
                                   ProjectWorkBindingFact binding) {
        return projectBindingMatches(root, binding)
                && Objects.equals(form.templateId(), binding.dynamicFormTemplateId())
                && Objects.equals(form.templateRevisionId(), binding.dynamicFormTemplateRevisionId())
                && Objects.equals(form.templateRevisionNo(), binding.dynamicFormRevisionNo())
                && Objects.equals(form.revisionFactVersion(), binding.dynamicFormRevisionFactVersion());
    }

    private Cursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new Cursor(null, null);
        String[] parts = cursor.split(":", -1);
        try {
            if (parts.length != 2) throw new IllegalArgumentException();
            int version = Integer.parseInt(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (version <= 0 || id <= 0) throw new IllegalArgumentException();
            return new Cursor(version, id);
        } catch (IllegalArgumentException failure) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
    }

    private int pageSize(Integer value) {
        if (value == null) return DEFAULT_PAGE_SIZE;
        if (value < 1 || value > MAX_PAGE_SIZE) throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        return value;
    }

    private String cursor(PreparationDO row) {
        return row.getBusinessVersion() + ":" + row.getId();
    }

    private record Cursor(Integer businessVersion, Long id) {}
    public record Actor(Long tenantId, Long actorId) {}
}
