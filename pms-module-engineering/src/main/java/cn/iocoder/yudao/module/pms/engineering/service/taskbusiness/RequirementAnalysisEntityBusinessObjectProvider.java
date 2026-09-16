package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.RequirementAnalysisEntityFactApi;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.RequirementAnalysisEntityFactApi.Fact;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.RequirementAnalysisEntityFactApi.Query;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityQueryService.View;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityQueryService.Workspace;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityQueryService;
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

/** Task and stage integration for the independent requirement-analysis business revisions. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisEntityBusinessObjectProvider implements TaskBusinessObjectProvider, StageBusinessViewProvider {
    public static final String COMPLETED_FACT = "REQUIREMENT_ANALYSIS_COMPLETED";
    private static final ProjectWorkBindingTarget TARGET = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
    private static final Set<String> WRITE_ACTIONS = Set.of("CREATE_INITIAL_DRAFT", "CREATE_DRAFT", "PATCH_FORM", "COMPLETE");
    private final RequirementAnalysisEntityQueryService queryService;
    private final RequirementAnalysisMapper rootMapper;
    private final RequirementAnalysisEntityFactApi facts;
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.execution() == null
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || objectId == null || !objectId.matches("[1-9][0-9]*"))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        executions.lockAndRevalidate(context.execution());
        return lockedResult(context.tenantId(),context.execution().projectId(),objectId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockStageCompletionFact(StageCompletionContext context, String objectId) {
        if (context == null || context.execution() == null
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || objectId == null || !objectId.matches("[1-9][0-9]*"))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        executions.lockAndRevalidateStage(context.execution());
        return lockedResult(context.tenantId(),context.execution().projectId(),objectId);
    }

    private CompletionFact lockedResult(Long tenantId, Long projectId, String objectId) {
        var root = rootMapper.lockRevision(new RequirementRevisionQuery(tenantId, Long.valueOf(objectId)));
        if (root == null || !Objects.equals(root.getProjectId(), projectId)
                || root.getVersion() == null
                || !Set.of("DRAFT", "FROZEN").contains(root.getRevisionState()))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        boolean completed = "FROZEN".equals(root.getRevisionState());
        // The SOL completion command already validated and froze the form/files atomically.
        // Its immutable result is the fact; do not load private form content as a simulated user.
        if (completed && (root.getFrozenAt() == null || root.getFrozenBy() == null))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return new CompletionFact(objectId, "SOL_REQUIREMENT_RESULT:" + root.getId() + ":"
                + root.getVersion() + ":" + root.getRevisionNo() + ":" + root.getRevisionState(), completed,
                Map.of(COMPLETED_FACT, completed));
    }

    @Override
    public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || !TARGET.targetObjectKey().equals(context.targetObjectKey()) || pageSize < 1 || pageSize > 100)
            throw exception(FORBIDDEN);
        if (afterObjectId != null) return List.of();
        var project = new cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementProjectQuery(context.tenantId(), context.projectId());
        var current = rootMapper.selectDraft(project);
        if (current == null) current = rootMapper.selectEffective(project);
        if (current == null) return List.of();
        var frozen = JsonUtils.parseObject(current.getExecutionSnapshot(),
                cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisExecutionAccess.Frozen.class);
        var parameters = JsonUtils.parseTree(context.bindingParameters());
        if (frozen == null || frozen.binding() == null || parameters == null)
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        if (!Objects.equals(parameters.path("dynamicFormTemplateRevisionId").asText(),
                String.valueOf(frozen.binding().dynamicFormTemplateRevisionId()))) return List.of();
        return List.of(new AssociationCandidate(current.getId().toString(),
                "SOL_REQUIREMENT_ASSOCIATION:" + current.getId() + ":" + current.getVersion()));
    }

    @Override public String ownerContext() { return TARGET.targetContextCode(); }
    @Override public String objectType() { return TARGET.targetObjectType(); }
    @Override public Set<String> completionFactCodes() { return Set.of(COMPLETED_FACT); }
    @Override public boolean supportsStageCompletionFacts() { return true; }
    @Override public Map<String, String> completionFactLabels() { return Map.of(COMPLETED_FACT, "需求分析版本已完成"); }

    @Override
    public Set<String> inspectContext(TaskBusinessObjectProvider.Context context) {
        requireTask(context);
        return actions(workspace(context));
    }

    @Override
    public StageBusinessViewProvider.Result inspectStage(StageBusinessViewProvider.Context context) {
        trusted(context.tenantId(), context.actorId(), context.projectId());
        if (context.stageId() == null || context.stageId() <= 0 || !TARGET.targetObjectKey().equals(context.targetObjectKey())
                || !Set.of("REFERENCE_EXISTING", "CREATE_ON_FIRST_ACTION", "CREATE_ON_ENTER", "READ_ONLY_AGGREGATE")
                .contains(context.instanceResolutionStrategy())) throw exception(FORBIDDEN);
        var workspace = queryService.workspace(context.projectId(),
                new EntityActor(context.tenantId(), context.actorId(), null), context.stageId(), null);
        // Rendering never creates a draft, including CREATE_ON_ENTER. Only the Owner's explicit command may do that.
        var allowed = actions(workspace);
        var execution = context.execution();
        if (execution == null || !execution.writable()
                || !Objects.equals(context.projectId(), execution.projectId()) || !Objects.equals(context.stageId(), execution.stageId()))
            return new StageBusinessViewProvider.Result(allowed.contains("QUERY") ? Set.of("QUERY") : Set.of());
        return new StageBusinessViewProvider.Result(allowed);
    }

    @Override
    public List<BusinessObjectFact> candidates(TaskBusinessObjectProvider.Context context) {
        requireTask(context);
        var workspace = workspace(context);
        List<BusinessObjectFact> result = new ArrayList<>();
        if (workspace.draft() != null) result.add(toFact(context, workspace.draft(), actions(workspace)));
        if (workspace.currentEffective() != null) result.add(toFact(context, workspace.currentEffective(), actions(workspace)));
        return List.copyOf(result);
    }

    @Override
    public BusinessObjectFact inspect(TaskBusinessObjectProvider.Context context, String objectId) {
        requireTask(context);
        var detail = detail(context, objectId);
        return toFact(context, detail, actions(workspace(context)));
    }

    @Override
    public BusinessObjectInspection inspectContextAndObjects(TaskBusinessObjectProvider.Context context, List<String> objectIds) {
        requireTask(context);
        var current = workspace(context);
        var allowed = actions(current);
        var versions = new HashMap<String, View>();
        if (current.draft() != null) versions.put(current.draft().revision().ref().revisionId().toString(), current.draft());
        if (current.currentEffective() != null)
            versions.put(current.currentEffective().revision().ref().revisionId().toString(), current.currentEffective());
        var objects = objectIds.stream().map(id -> {
            // Workspace already authorizes and fully assembles current versions.
            // Historical links still use the exact-version read and its visibility check.
            var selected = versions.get(id);
            return toFact(context, selected == null ? detail(context, id) : selected, allowed);
        }).toList();
        return new BusinessObjectInspection(allowed, objects);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BusinessObjectFact lockAndRevalidate(TaskBusinessObjectProvider.Context context, String objectId, String expectedVersion) {
        requireTask(context);
        var inspected = detail(context, objectId);
        if ("FROZEN".equals(inspected.revision().state().name())) {
            // Reuse the existing PROJ -> SOL -> PLT/file lock-and-proof contract, not a status-only completion check.
            var fact = requireCompletedFact(context.projectId(), inspected.revision().ref().revisionId(),
                    facts.inspect(new Query(context.projectId(), inspected.revision().ref().entity().entityId(), inspected.revision().ref().revisionId())));
            var locked = requireCompletedFact(context.projectId(), inspected.revision().ref().revisionId(), facts.lockAndRevalidate(fact));
            if (!Objects.equals(fact, locked)) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        } else {
            var row = rootMapper.lockRevision(new RequirementRevisionQuery(context.tenantId(), inspected.revision().ref().revisionId()));
            if (row == null || !Objects.equals(row.getProjectId(), context.projectId())) throw exception(REQUIREMENT_NOT_EXISTS);
        }
        var current = inspect(context, objectId);
        if (!Objects.equals(expectedVersion, current.factVersion())) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return current;
    }

    private View detail(TaskBusinessObjectProvider.Context context, String objectId) {
        Long id;
        try {
            if (objectId == null || !objectId.matches("[1-9][0-9]*")) throw exception(REQUIREMENT_NOT_EXISTS);
            id = Long.valueOf(objectId);
        } catch (NumberFormatException invalid) { throw exception(REQUIREMENT_NOT_EXISTS); }
        var result = queryService.revision(id, new EntityActor(context.tenantId(), context.actorId(), null));
        if (result == null || !Objects.equals(id, result.revision().ref().revisionId()) || !Objects.equals(context.projectId(), result.projectId()))
            throw exception(REQUIREMENT_NOT_EXISTS);
        return result;
    }

    private BusinessObjectFact toFact(TaskBusinessObjectProvider.Context context, View detail, Set<String> contextActions) {
        if (!Objects.equals(context.projectId(), detail.projectId()) || detail.revision().ref().revisionId() == null)
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        boolean completed = "FROZEN".equals(detail.revision().state().name());
        if (completed) requireCompletedFact(context.projectId(), detail.revision().ref().revisionId(),
                facts.inspect(new Query(context.projectId(), detail.revision().ref().entity().entityId(), detail.revision().ref().revisionId())));
        Set<String> actions = new LinkedHashSet<>();
        actions.add("QUERY");
        actions.addAll(detail.allowedActions());
        if (contextActions.stream().anyMatch(WRITE_ACTIONS::contains)) actions.addAll(Set.of("LINK", "UNLINK"));
        List<FileArtifactVersionFact> files = new ArrayList<>();
        detail.attachments().forEach(set -> files.addAll(set.activeFacts()));
        files.sort(Comparator.comparing(FileArtifactVersionFact::referenceKey));
        // The existing relationship contract stores at most 256 characters. Digest the full
        // file/version vector instead of enlarging that shared schema or truncating stale-write evidence.
        String version = "SOL_REQUIREMENT_ANALYSIS:v1:" + DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(detail.revision().version(), detail.form() == null ? 0 : detail.form().binding().version(),
                detail.extensionValueVersion(), detail.revision().state().name(), files.stream().map(file ->
                        List.of(file.artifactId(), file.versionNo(), file.referenceKey(), file.fileFactVersion(), file.scopeVersion())).toList())));
        var artifacts = files.stream().map(file -> new BusinessArtifact(file.artifactId().toString(), file.versionNo(),
                file.referenceKey(), file.name(), version)).toList();
        return new BusinessObjectFact(detail.revision().ref().revisionId().toString(), "需求分析 V" + detail.revision().revisionNo(),
                version, actions, Map.of(COMPLETED_FACT, completed), artifacts);
    }

    private Set<String> actions(Workspace workspace) {
        Set<String> actions = new LinkedHashSet<>();
        actions.add("QUERY");
        actions.addAll(workspace.allowedActions());
        if (!workspace.allowedActions().isEmpty()) actions.add("CREATE");
        if (workspace.draft() != null) actions.addAll(workspace.draft().allowedActions());
        if (workspace.currentEffective() != null) actions.addAll(workspace.currentEffective().allowedActions());
        return Set.copyOf(actions);
    }
    private Fact requireCompletedFact(Long projectId, Long revisionId, Fact fact) {
        if (fact == null || !Objects.equals(fact.projectId(), projectId) || !Objects.equals(fact.revisionId(), revisionId)
                || fact.frozenAt() == null || fact.frozenBy() == null)
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return fact;
    }

    private Workspace workspace(TaskBusinessObjectProvider.Context context) {
        trusted(context.tenantId(), context.actorId(), context.projectId());
        return queryService.workspace(context.projectId(),
                new EntityActor(context.tenantId(), context.actorId(), null), null, context.taskId());
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
