package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.RequirementAnalysisFactApi;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFact;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisVersionRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisWorkspaceRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisDynamicFormQueryService;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE;

/** PRE-04: sol_preparation + published PLT composition only; never pms_eng_requirement or fixed sections. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisBusinessObjectProvider implements TaskBusinessObjectProvider, StageBusinessViewProvider {
    public static final String COMPLETED_FACT = "REQUIREMENT_ANALYSIS_COMPLETED";
    private static final ProjectWorkBindingTarget TARGET = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
    private static final Set<String> WRITE_ACTIONS = Set.of("CREATE_INITIAL_DRAFT", "CREATE_DRAFT", "PATCH_FORM", "COMPLETE");
    private final RequirementAnalysisDynamicFormQueryService queryService;
    private final RequirementAnalysisRootMapper rootMapper;
    private final RequirementAnalysisFactApi facts;

    @Override public String ownerContext() { return TARGET.targetContextCode(); }
    @Override public String objectType() { return TARGET.targetObjectType(); }
    @Override public Set<String> completionFactCodes() { return Set.of(COMPLETED_FACT); }
    @Override public Map<String, String> completionFactLabels() { return Map.of(COMPLETED_FACT, "需求分析版本已完成"); }

    @Override
    public Set<String> inspectContext(TaskBusinessObjectProvider.Context context) {
        requireTask(context);
        return actions(workspace(context.tenantId(), context.actorId(), context.projectId()));
    }

    @Override
    public StageBusinessViewProvider.Result inspectStage(StageBusinessViewProvider.Context context) {
        trusted(context.tenantId(), context.actorId(), context.projectId());
        if (context.stageId() == null || context.stageId() <= 0 || !TARGET.targetObjectKey().equals(context.targetObjectKey())
                || !Set.of("REFERENCE_EXISTING", "CREATE_ON_FIRST_ACTION", "CREATE_ON_ENTER", "READ_ONLY_AGGREGATE")
                .contains(context.instanceResolutionStrategy())) throw exception(FORBIDDEN);
        var workspace = workspace(context.tenantId(), context.actorId(), context.projectId());
        // Rendering never creates a draft, including CREATE_ON_ENTER. Only the Owner's explicit command may do that.
        return new StageBusinessViewProvider.Result(actions(workspace));
    }

    @Override
    public List<BusinessObjectFact> candidates(TaskBusinessObjectProvider.Context context) {
        requireTask(context);
        var workspace = workspace(context.tenantId(), context.actorId(), context.projectId());
        List<BusinessObjectFact> result = new ArrayList<>();
        if (workspace.getDraft() != null) result.add(toFact(context, workspace.getDraft(), actions(workspace)));
        if (workspace.getCurrentEffective() != null) result.add(toFact(context, workspace.getCurrentEffective(), actions(workspace)));
        return List.copyOf(result);
    }

    @Override
    public BusinessObjectFact inspect(TaskBusinessObjectProvider.Context context, String objectId) {
        requireTask(context);
        var detail = detail(context, objectId);
        return toFact(context, detail, actions(workspace(context.tenantId(), context.actorId(), context.projectId())));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BusinessObjectFact lockAndRevalidate(TaskBusinessObjectProvider.Context context, String objectId, String expectedVersion) {
        requireTask(context);
        var inspected = detail(context, objectId);
        if ("COMPLETED".equals(inspected.getStatus())) {
            // Reuse the existing PROJ -> SOL -> PLT/file lock-and-proof contract, not a status-only completion check.
            var fact = requireCompletedFact(context.projectId(), inspected.getPreparationId(),
                    facts.inspect(new RequirementAnalysisFactQuery(context.projectId(), inspected.getPreparationId())));
            var locked = facts.lockAndRevalidate(new RequirementAnalysisFactRevalidationQuery(context.projectId(), fact.preparationId(),
                    fact.businessVersion(), fact.contentVersion(), fact.projectVersion(), fact.templateRevision(), fact.factVector()));
            requireCompletedFact(context.projectId(), inspected.getPreparationId(), locked);
            if (!Objects.equals(fact.factVector(), locked.factVector())) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        } else {
            var row = rootMapper.selectForUpdate(new RequirementAnalysisRowQuery(context.tenantId(), inspected.getPreparationId()));
            if (row == null || !Objects.equals(row.getProjectId(), context.projectId())) throw exception(REQUIREMENT_NOT_EXISTS);
        }
        var current = inspect(context, objectId);
        if (!Objects.equals(expectedVersion, current.factVersion())) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return current;
    }

    private RequirementAnalysisVersionRespVO detail(TaskBusinessObjectProvider.Context context, String objectId) {
        Long id;
        try {
            if (objectId == null || !objectId.matches("[1-9][0-9]*")) throw exception(REQUIREMENT_NOT_EXISTS);
            id = Long.valueOf(objectId);
        } catch (NumberFormatException invalid) { throw exception(REQUIREMENT_NOT_EXISTS); }
        var result = queryService.getDetail(id, new RequirementAnalysisDynamicFormQueryService.Actor(context.tenantId(), context.actorId()));
        if (result == null || !Objects.equals(id, result.getPreparationId()) || !Objects.equals(context.projectId(), result.getProjectId()))
            throw exception(REQUIREMENT_NOT_EXISTS);
        return result;
    }

    private BusinessObjectFact toFact(TaskBusinessObjectProvider.Context context, RequirementAnalysisVersionRespVO detail, Set<String> contextActions) {
        if (!Objects.equals(context.projectId(), detail.getProjectId()) || detail.getPreparationId() == null
                || detail.getVersion() == null || detail.getContentVersion() == null || detail.getDynamicFormInstanceVersion() == null)
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        boolean completed = "COMPLETED".equals(detail.getStatus());
        if (completed) requireCompletedFact(context.projectId(), detail.getPreparationId(),
                facts.inspect(new RequirementAnalysisFactQuery(context.projectId(), detail.getPreparationId())));
        Set<String> actions = new LinkedHashSet<>();
        actions.add("QUERY");
        actions.addAll(detail.getAllowedActions());
        if (contextActions.stream().anyMatch(WRITE_ACTIONS::contains)) actions.addAll(Set.of("LINK", "UNLINK"));
        List<FileArtifactVersionFact> files = new ArrayList<>();
        for (var value : detail.getControlledFiles().values()) {
            if (!(value instanceof List<?> entries)) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
            for (var entry : entries) {
                if (!(entry instanceof FileArtifactVersionFact file)) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
                files.add(file);
            }
        }
        files.sort(Comparator.comparing(FileArtifactVersionFact::referenceKey));
        // The existing relationship contract stores at most 256 characters. Digest the full
        // file/version vector instead of enlarging that shared schema or truncating stale-write evidence.
        String version = "SOL_REQUIREMENT_ANALYSIS:v1:" + DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(detail.getVersion(), detail.getContentVersion(),
                detail.getDynamicFormInstanceVersion(), detail.getStatus(), files.stream().map(file ->
                        List.of(file.artifactId(), file.versionNo(), file.referenceKey(), file.fileFactVersion(), file.scopeVersion())).toList())));
        var artifacts = files.stream().map(file -> new BusinessArtifact(file.artifactId().toString(), file.versionNo(),
                file.referenceKey(), file.name(), version)).toList();
        return new BusinessObjectFact(detail.getPreparationId().toString(), "需求分析 V" + detail.getBusinessVersion(),
                version, actions, Map.of(COMPLETED_FACT, completed), artifacts);
    }

    private Set<String> actions(RequirementAnalysisWorkspaceRespVO workspace) {
        Set<String> actions = new LinkedHashSet<>();
        actions.add("QUERY");
        actions.addAll(workspace.getAllowedActions());
        if (!workspace.getAllowedActions().isEmpty()) actions.add("CREATE");
        if (workspace.getDraft() != null) actions.addAll(workspace.getDraft().getAllowedActions());
        if (workspace.getCurrentEffective() != null) actions.addAll(workspace.getCurrentEffective().getAllowedActions());
        return Set.copyOf(actions);
    }
    private RequirementAnalysisFact requireCompletedFact(Long projectId, Long preparationId, RequirementAnalysisFact fact) {
        if (fact == null || !Objects.equals(fact.projectId(), projectId) || !Objects.equals(fact.preparationId(), preparationId)
                || !"COMPLETED".equals(fact.status()) || fact.factVector() == null)
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return fact;
    }

    private RequirementAnalysisWorkspaceRespVO workspace(Long tenantId, Long actorId, Long projectId) {
        trusted(tenantId, actorId, projectId);
        return queryService.getWorkspace(projectId, new RequirementAnalysisDynamicFormQueryService.Actor(tenantId, actorId));
    }
    private void requireTask(TaskBusinessObjectProvider.Context context) {
        if (context == null || context.taskId() == null || context.taskId() <= 0) throw exception(FORBIDDEN);
        trusted(context.tenantId(), context.actorId(), context.projectId());
    }
    private void trusted(Long tenantId, Long actorId, Long projectId) {
        if (tenantId == null || actorId == null || projectId == null || projectId <= 0
                || !Objects.equals(tenantId, TenantContextHolder.getTenantId())
                || !Objects.equals(actorId, SecurityFrameworkUtils.getLoginUserId())) throw exception(FORBIDDEN);
    }
}
