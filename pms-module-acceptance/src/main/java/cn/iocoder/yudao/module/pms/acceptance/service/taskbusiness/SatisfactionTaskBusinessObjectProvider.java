package cn.iocoder.yudao.module.pms.acceptance.service.taskbusiness;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.*;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.SatisfactionBusinessResultSource;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.SatisfactionTaskManagementService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** ACC supplies signed, passed and archived evidence; PROJ owns task progression. */
@Service
@RequiredArgsConstructor
public class SatisfactionTaskBusinessObjectProvider implements TaskBusinessObjectProvider {
    public static final String COMPLETE = "SIGNED_PASSED_ARCHIVED";
    private final SatisfactionCollectionTaskMapper tasks;
    private final SatisfactionResultMapper results;
    private final SatisfactionResultFileMapper files;
    private final SatisfactionTaskManagementService queries;
    private final SatisfactionBusinessResultSource resultSource;
    private final ProjectNodeExecutionApi executions;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;

    @Override public String ownerContext() { return "ACC"; }
    @Override public String objectType() { return "SATISFACTION"; }
    @Override public java.time.LocalDateTime handlingCreatedAt(AssociationContext context, String objectId) {
        if (context == null) throw unavailable();
        requireTenant(context.tenantId());
        var row = tasks.selectById(id(objectId));
        requireOwner(new OwnerContext(context.tenantId(), context.projectId()), row);
        return row.getCreateTime();
    }
    @Override public Set<String> completionFactCodes() { return Set.of(COMPLETE); }
    @Override public Map<String, String> completionFactLabels() {
        return Map.of(COMPLETE, "客户签字、评分达标且归档成功");
    }
    @Override public boolean supportsStageCompletionFacts() { return true; }

    @Override public Set<String> inspectContext(Context context) {
        requireActor(context);
        Set<String> actions = new HashSet<>(Set.of("QUERY"));
        if (permissions.hasAnyPermissions(context.actorId(), "pms:acceptance:satisfaction:manage")
                && inScope(context, ProjectScopeApi.ACTION_EDIT)) actions.add("MANAGE");
        if (actions.contains("MANAGE") && inScope(context, ProjectScopeApi.ACTION_MANAGE))
            actions.addAll(Set.of("LINK", "UNLINK"));
        return Set.copyOf(actions);
    }

    @Override public List<BusinessObjectFact> candidates(Context context) {
        requireActor(context);
        return queries.list(context.tenantId(), context.actorId(), context.projectId()).stream()
                .map(task -> inspect(context, task.id().toString())).toList();
    }

    @Override public BusinessObjectFact inspect(Context context, String objectId) {
        var actions = inspectContext(context);
        var visible = queries.get(context.tenantId(), context.actorId(), id(objectId));
        if (!context.projectId().equals(visible.projectId())) throw unavailable();
        return fact(new OwnerContext(context.tenantId(), context.projectId()), objectId, false, actions);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion) {
        var actions = inspectContext(context);
        var visible = queries.get(context.tenantId(), context.actorId(), id(objectId));
        if (!context.projectId().equals(visible.projectId())) throw unavailable();
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(context.tenantId())
                .subjectUserId(context.actorId()).anchorProjectId(context.projectId()).actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || scope.treeVersion() == null) throw unavailable();
        var lockedScope = scopes.lockAndRevalidate(cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery.builder()
                .tenantId(context.tenantId()).subjectUserId(context.actorId()).anchorProjectId(context.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).expectedScopeVersion(scope.treeVersion()).build());
        if (lockedScope == null || !Objects.equals(scope.treeVersion(), lockedScope.treeVersion())
                || lockedScope.fullProjectIds() == null || !lockedScope.fullProjectIds().contains(context.projectId())) throw unavailable();
        var fact = fact(new OwnerContext(context.tenantId(), context.projectId()), objectId, true, actions);
        if (!Objects.equals(expectedVersion, fact.factVersion()))
            throw new IllegalStateException("SATISFACTION_OBJECT_VERSION_CONFLICT");
        return fact;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.execution() == null) throw unavailable();
        requireTenant(context.tenantId());
        var execution = executions.lockAndRevalidate(context.execution());
        var fact = fact(new OwnerContext(context.tenantId(), execution.projectId()), objectId, true, Set.of());
        return new CompletionFact(fact.objectId(), fact.factVersion(), fact.completionFacts().get(COMPLETE), fact.completionFacts());
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockStageCompletionFact(StageCompletionContext context, String objectId) {
        if (context == null || context.execution() == null) throw unavailable();
        requireTenant(context.tenantId());
        var execution = executions.lockAndRevalidateStage(context.execution());
        var fact = fact(new OwnerContext(context.tenantId(), execution.projectId()), objectId, true, Set.of());
        return new CompletionFact(fact.objectId(), fact.factVersion(), fact.completionFacts().get(COMPLETE), fact.completionFacts());
    }

    @Override public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || context.projectId() == null || context.projectId() <= 0
                || !"PROJECT_SATISFACTION".equals(context.targetObjectKey()) || pageSize < 1 || pageSize > 100)
            throw unavailable();
        requireTenant(context.tenantId());
        return tasks.selectAssociationCandidates(new SatisfactionAssociationQuery(context.tenantId(), context.projectId(),
                        afterObjectId == null ? null : id(afterObjectId), pageSize)).stream()
                .map(task -> {
                    requireOwner(new OwnerContext(context.tenantId(), context.projectId()), task);
                    return new AssociationCandidate(task.getId().toString(), "ACC_SAT:" + task.getId() + ":" + task.getVersion());
                }).toList();
    }

    private BusinessObjectFact fact(OwnerContext context, String objectId, boolean lock, Set<String> actions) {
        var task = lock ? tasks.selectByIdForUpdate(context.tenantId(), id(objectId)) : tasks.selectById(id(objectId));
        requireOwner(context, task);
        var result = task.getResultId() == null ? null : lock
                ? results.selectByIdForUpdate(context.tenantId(), task.getResultId()) : results.selectById(task.getResultId());
        if (result != null && (!Objects.equals(result.getTenantId(), context.tenantId())
                || !Objects.equals(result.getCollectionTaskId(), task.getId())))
            throw unavailable();
        var evidence = result == null ? List.<SatisfactionResultFileDO>of()
                : files.selectListByResult(new SatisfactionResultFilesQuery(context.tenantId(), result.getId()));
        boolean complete = result != null && Objects.equals(task.getResultId(), result.getId())
                && "EFFECTIVE".equals(result.getResultStatus()) && Boolean.TRUE.equals(result.getPassed())
                && result.getEffectiveTo() == null && "ARCHIVED".equals(result.getArchiveStatus())
                && evidence.stream().allMatch(file -> Objects.equals(context.tenantId(), file.getTenantId())
                    && Objects.equals(result.getId(), file.getResultId()))
                && evidence.stream().anyMatch(file -> "SIGNATURE".equals(file.getFileRole()))
                && evidence.stream().anyMatch(file -> "RESULT_DOCUMENT".equals(file.getFileRole()));
        if (complete && lock) {
            var observed = resultSource.lockAndInspect(new BusinessResultSource.Query(context.tenantId(), context.projectId(),
                    SatisfactionBusinessResultSource.TYPE, task.getId().toString(), result.getId().toString()));
            complete = observed != null && observed.result() != null
                    && observed.result().validity() == BusinessResultSource.Validity.CURRENT;
        }
        String version = "ACC_SAT:" + DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(task, result, evidence, complete)));
        return new BusinessObjectFact(task.getId().toString(), "满意度调查 " + task.getCollectionKey() + " / 第" + task.getTaskRevisionNo() + "轮",
                version, actions, Map.of(COMPLETE, complete), List.of());
    }

    private record OwnerContext(Long tenantId, Long projectId) { }
    private void requireOwner(OwnerContext context, SatisfactionCollectionTaskDO task) {
        requireTenant(context.tenantId());
        if (task == null || !Objects.equals(context.tenantId(), task.getTenantId())
                || !Objects.equals(context.projectId(), task.getProjectId()) || Boolean.TRUE.equals(task.getDeleted())
                || task.getVersion() == null) throw unavailable();
    }
    private void requireActor(Context context) {
        if (context == null || context.projectId() == null || context.projectId() <= 0 || context.actorId() == null
                || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())) throw unavailable();
        requireTenant(context.tenantId());
        if (!permissions.hasAnyPermissions(context.actorId(), "pms:acceptance:satisfaction:query")
                || !inScope(context, ProjectScopeApi.ACTION_VIEW)) throw unavailable();
    }
    private boolean inScope(Context context, String action) {
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(context.tenantId())
                .subjectUserId(context.actorId()).anchorProjectId(context.projectId()).actionCode(action).build());
        return scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(context.projectId());
    }
    private void requireTenant(Long tenantId) {
        if (tenantId == null || !Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())) throw unavailable();
    }
    private Long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) throw unavailable();
        try { return Long.valueOf(value); } catch (NumberFormatException invalid) { throw unavailable(); }
    }
    private IllegalStateException unavailable() { return new IllegalStateException("SATISFACTION_OBJECT_UNAVAILABLE"); }
}
