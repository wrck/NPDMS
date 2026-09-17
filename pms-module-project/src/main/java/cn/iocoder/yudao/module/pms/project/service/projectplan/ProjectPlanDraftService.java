package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateCompiler;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateDesignerDependencyValidator;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRulePublicationValidator;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class ProjectPlanDraftService {
    public static final String MANAGE_PERMISSION = "pms:project-plan:manage";
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final ProjectMasterMapper projectRows;
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectRuntimeGraphMapper graph;
    private final TemplateCompiler compiler;
    private final TemplateDesignerDependencyValidator dependencies;
    private final ProjectRulePublicationValidator ruleValidator;
    private final ProjectPlanImpactAnalyzer impacts;
    private final ProjectPlanExecutionPlanner executionPlanner;
    private final PlatformCommandExecutionApi commands;
    private final ProjectDeliverableInitializationApplicationService deliverables;
    private final ProjectPlanDeliverablePlanner deliverablePlanner;
    private final ProjectPlanMilestoneInstaller milestones;
    private final ProjectPlanGateInstaller gates;

    public record Definition(Long id, Integer revisionNo, Integer version, Long basePlanVersionId, TemplateDesignerDocument designer) { }
    public record State(Definition effective, Definition draft, boolean editable, Integer projectVersion) { }
    public record Create(Long projectId, Long expectedPlanVersionId) { }
    public record Save(Long projectId, Long draftId, Integer expectedVersion, TemplateDesignerDocument designer) { }
    public record Preview(Long basePlanVersionId, Long draftId, Integer draftVersion, Integer projectVersion,
                          List<ProjectPlanImpactAnalyzer.Change> changes, List<String> changedRuleKeys,
                          List<ProjectPlanExecutionPlanner.Change> executionChanges,
                          List<ProjectPlanDeliverablePlanner.Change> deliverableChanges,
                          List<ProjectPlanMilestoneInstaller.Change> milestoneChanges,
                          List<ProjectPlanGateInstaller.Change> gateChanges, List<Issue> issues) { }
    record Prepared(Preview preview, ProjectMasterDO project, ProjectPlanVersionDO effective, ProjectPlanVersionDO draft,
                    TemplateExecutionSnapshot before, TemplateExecutionSnapshot after,
                    List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO> rounds,
                    List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO> stages,
                    List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> tasks,
                    ProjectPlanExecutionPlanner.Plan runtime, ProjectPlanDeliverablePlanner.Plan deliverables,
                    ProjectPlanMilestoneInstaller.Plan milestones, ProjectPlanGateInstaller.Plan gates) { }

    public State get(Long projectId, Long actorId) {
        var scope = authorize(projectId, actorId);
        var project = projectRows.selectById(projectId);
        var effective = plans.selectEffective(scope);
        if (project == null || effective == null) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        return new State(definition(effective), definition(plans.selectDraft(scope)), "ACTIVE".equals(project.getLifecycleStatus()), project.getVersion());
    }

    public Definition create(Create command, Long actorId, String key) {
        var scope = authorize(command.projectId(), actorId);
        return execute("PROJECT_PLAN_DRAFT_CREATE", scope, actorId, key, command, () -> {
            var project = lockActive(scope);
            authorize(command.projectId(), actorId);
            var effective = plans.selectEffective(scope);
            if (effective == null || !Objects.equals(effective.getId(), command.expectedPlanVersionId())
                    || !Objects.equals(project.getActivePlanVersionId(), effective.getId())) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
            var draft = plans.selectDraft(scope);
            if (draft != null) {
                if (!Objects.equals(draft.getBasePlanVersionId(), effective.getId())) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
                return definition(draft);
            }
            draft = new ProjectPlanVersionDO();
            draft.setTenantId(scope.tenantId()); draft.setProjectId(scope.projectId()); draft.setStatus("DRAFT");
            draft.setRevisionNo(plans.selectNextRevisionNo(scope)); draft.setVersion(0);
            draft.setBasePlanVersionId(effective.getId()); draft.setSourceTemplateRevisionId(effective.getSourceTemplateRevisionId());
            draft.setDesignerDocument(effective.getDesignerDocument());
            if (plans.insert(draft) != 1) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
            return definition(draft);
        });
    }

    public Definition save(Save command, Long actorId, String key) {
        var scope = authorize(command.projectId(), actorId);
        if (command.designer() == null || !Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION).equals(command.designer().getSchemaVersion()))
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        return execute("PROJECT_PLAN_DRAFT_SAVE", scope, actorId, key, command, () -> {
            var project = lockActive(scope);
            authorize(command.projectId(), actorId);
            var draft = requireDraft(scope, command.draftId(), command.expectedVersion(), project.getActivePlanVersionId());
            TemplateDesignerDocument normalized;
            try { normalized = TemplateRuleCollection.forEditing(command.designer()); }
            catch (IllegalArgumentException invalid) { throw exception(PROJECT_PLAN_CHANGE_INVALID); }
            if (plans.saveDraftIfCurrent(new ProjectPlanVersionMapper.DraftUpdate(scope.tenantId(), scope.projectId(), draft.getId(),
                    draft.getVersion(), draft.getBasePlanVersionId(), JsonUtils.toJsonString(normalized), actorId.toString())) != 1)
                throw exception(PROJECT_PLAN_VERSION_CONFLICT);
            draft.setDesignerDocument(JsonUtils.toJsonString(normalized)); draft.setVersion(draft.getVersion()+1);
            return definition(draft);
        });
    }

    /** Locks provide one observation of the runtime. No audit, Outbox, workflow or rule evaluation is triggered. */
    @Transactional(rollbackFor = Exception.class)
    public Preview preview(Long projectId, Long draftId, Integer expectedVersion, Long actorId) {
        return prepare(projectId,draftId,expectedVersion,actorId,false).preview();
    }

    /** Shared observation for preview and activation; activation's command transaction retains all locks. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, rollbackFor = Exception.class)
    Prepared prepare(Long projectId, Long draftId, Integer expectedVersion, Long actorId, boolean publishing) {
        var scope = authorize(projectId, actorId);
        var project = lockActive(scope);
        var draft = requireDraft(scope, draftId, expectedVersion, project.getActivePlanVersionId());
        var effective = plans.selectEffective(scope);
        if (effective == null) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        var designer = definition(draft).designer();
        var compilation = compiler.compile(designer);
        List<Issue> issues = new ArrayList<>(compilation.issues());
        issues.addAll(dependencies.validateProjectChanges(definition(effective).designer(), designer, publishing));
        issues.addAll(ruleValidator.validate(designer));
        var changes = new ProjectPlanImpactAnalyzer.Impact(List.of(), List.of(), List.of());
        var runtime = new ProjectPlanExecutionPlanner.Plan(List.of(), List.of());
        var ownerPlan = new ProjectPlanDeliverablePlanner.Plan(List.of(), List.of(), List.of());
        var milestonePlan = new ProjectPlanMilestoneInstaller.Plan(List.of(), List.of(), List.of());
        var gatePlan = new ProjectPlanGateInstaller.Plan(List.of(), List.of(), List.of());
        TemplateExecutionSnapshot before = null;
        List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO> rounds = List.of();
        List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO> stageRows = List.of();
        List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> taskRows = List.of();
        if (compilation.valid()) {
            var query = new ProjectRuntimeGraphQuery(scope.tenantId(), projectId);
            before = JsonUtils.parseObject(effective.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
            rounds = executions.selectCurrentForUpdate(scope);
            stageRows = graph.selectStagesForUpdate(query);
            taskRows = graph.selectTasksForUpdate(query);
            changes = impacts.analyze(before, compilation.snapshot(), rounds, taskRows);
            runtime = executionPlanner.plan(effective.getId(), before, compilation.snapshot(), rounds, stageRows, taskRows);
            ownerPlan = deliverablePlanner.plan(projectId, before, compilation.snapshot(), deliverables.inspectPlanDefinitions(projectId));
            milestonePlan = milestones.inspect(scope, before, compilation.snapshot());
            gatePlan = gates.inspect(scope, before, compilation.snapshot());
            issues.addAll(changes.issues());
            issues.addAll(runtime.issues());
            issues.addAll(ownerPlan.issues());
            issues.addAll(milestonePlan.issues());
            issues.addAll(gatePlan.issues());
            changes = includeGateResultEffects(changes, gatePlan, compilation.snapshot());
        }
        var preview = new Preview(effective.getId(), draft.getId(), draft.getVersion(), project.getVersion(), changes.changes(),
                changes.changedRuleKeys(), runtime.changes(), ownerPlan.changes(), milestonePlan.changes(), gatePlan.changes(), issues.stream().distinct().toList());
        return new Prepared(preview,project,effective,draft,before,compilation.snapshot(),rounds,stageRows,taskRows,runtime,ownerPlan,milestonePlan,gatePlan);
    }

    private ProjectPlanImpactAnalyzer.Impact includeGateResultEffects(ProjectPlanImpactAnalyzer.Impact impact,
                                                                     ProjectPlanGateInstaller.Plan gates,
                                                                     TemplateExecutionSnapshot snapshot) {
        var changes = new ArrayList<>(impact.changes());
        for (var write : gates.writes()) {
            var change = write.change();
            if (!"UPDATE".equals(change.action()) || !change.reevaluationRequired()) continue;
            var prior = changes.stream().filter(row -> Objects.equals(row.nodeKey(), change.nodeKey())).findFirst().orElse(null);
            var effects = prior == null ? new ArrayList<String>() : new ArrayList<>(prior.effects());
            effects.add("门禁条件已变化：原判定结果保留在历史中，当前门禁将重新判定，不能沿用旧通过结果");
            var name = snapshot.getGates().stream().filter(node -> Objects.equals(node.getNodeKey(), change.nodeKey()))
                    .map(TemplateExecutionSnapshot.GateContract::getName).findFirst().orElse(change.toCode());
            if (prior != null) changes.remove(prior);
            changes.add(new ProjectPlanImpactAnalyzer.Change(change.nodeKey(), "GATE", name, "UPDATE",
                    !"PENDING".equals(write.previousStatus()), false, List.copyOf(effects)));
        }
        return new ProjectPlanImpactAnalyzer.Impact(List.copyOf(changes), impact.changedRuleKeys(), impact.issues());
    }

    ProjectPlanScopeQuery authorize(Long projectId, Long actorId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (!permissions.hasAnyPermissions(actorId, MANAGE_PERMISSION)) throw exception(PROJECT_PLAN_CHANGE_FORBIDDEN);
        var result = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorId, projectId, ProjectScopeApi.ACTION_EDIT));
        if (result == null || result.fullProjectIds() == null || !result.fullProjectIds().contains(projectId)) throw exception(PROJECT_PLAN_CHANGE_FORBIDDEN);
        return new ProjectPlanScopeQuery(tenantId, projectId);
    }
    private ProjectMasterDO lockActive(ProjectPlanScopeQuery scope) {
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(scope.tenantId(), scope.projectId()));
        if (project == null || !"ACTIVE".equals(project.getLifecycleStatus()) || project.getActivePlanVersionId() == null)
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        return project;
    }
    private ProjectPlanVersionDO requireDraft(ProjectPlanScopeQuery scope, Long id, Integer version, Long baseId) {
        var draft = plans.selectDraft(scope);
        if (draft == null || !Objects.equals(id, draft.getId()) || !Objects.equals(version, draft.getVersion())
                || !Objects.equals(baseId, draft.getBasePlanVersionId())) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        return draft;
    }
    private Definition definition(ProjectPlanVersionDO plan) {
        return plan == null ? null : new Definition(plan.getId(), plan.getRevisionNo(), plan.getVersion(), plan.getBasePlanVersionId(),
                JsonUtils.parseObject(plan.getDesignerDocument(), TemplateDesignerDocument.class));
    }
    private Definition execute(String action, ProjectPlanScopeQuery scope, Long actorId, String key, Object intent, Supplier<Definition> body) {
        if (key == null || key.isBlank()) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(scope.tenantId(), action, actorId, key),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(intent)), Definition.class, body,
                saved -> new PlatformCommandExecutionApi.SuccessFacts(action, "ProjectPlan", saved.id().toString(),
                        action + ":" + scope.projectId() + ":" + key,
                        JsonUtils.toJsonString(Map.of("projectId", scope.projectId(), "planVersionId", saved.id(), "draftVersion", saved.version())), List.of()));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return result.response();
    }
}
