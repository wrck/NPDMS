package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleReevaluation;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class ProjectStageSubmissionService {
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final PlatformCommandExecutionApi commands;
    private final ProjectRuntimeCoordinator coordinator;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper projectRows;

    public record Command(Long projectId, Long executionId, Integer expectedVersion, String note) { }
    public record Submitted(Long executionId, Integer roundNo, String status, Integer version) { }
    public record ExecutionView(Long id, Long planVersionId, String nodeKey, String nodeKind, String nodeCode, String name,
                                Integer roundNo, String status, Integer version, boolean canSubmit) { }

    public List<ExecutionView> list(Long projectId, Long actorId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireScope(tenantId, projectId, actorId, ProjectScopeApi.ACTION_VIEW);
        var scope = new ProjectPlanScopeQuery(tenantId, projectId);
        var plan = plans.selectEffective(scope);
        if (plan == null) return List.of();
        var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        var project = projectRows.selectById(projectId);
        boolean editable = project != null && "ACTIVE".equals(project.getLifecycleStatus())
                && permissions.hasAnyPermissions(actorId, "pms:project-task:execute")
                && hasScope(tenantId, projectId, actorId, ProjectScopeApi.ACTION_EDIT);
        return executions.selectCurrent(scope).stream().map(round -> {
            var definition = snapshot.getStages().stream().filter(node -> round.getNodeKey().equals(node.getNodeKey())).findFirst().orElse(null);
            boolean nativeStage = definition != null && definition.getBinding() != null && "STAGE_NATIVE".equals(definition.getBinding().getType());
            return new ExecutionView(round.getId(), round.getPlanVersionId(), round.getNodeKey(), round.getNodeKind(),
                    definition == null ? round.getNodeKey() : definition.getCode(), definition == null ? round.getNodeKey() : definition.getName(), round.getRoundNo(), round.getStatus(), round.getVersion(),
                    editable && nativeStage && "ACTIVE".equals(round.getStatus()) && round.getSubmittedAt() == null);
        }).toList();
    }

    public Submitted submit(Command command, Long actorId, String idempotencyKey) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (command == null || command.projectId() == null || command.executionId() == null || command.expectedVersion() == null
                || command.note() == null || command.note().isBlank() || command.note().length() > 2000
                || idempotencyKey == null || idempotencyKey.isBlank()) throw exception(PROJECT_TASK_COMMAND_INVALID);
        if (!permissions.hasAnyPermissions(actorId, "pms:project-task:execute")) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        requireScope(tenantId, command.projectId(), actorId, ProjectScopeApi.ACTION_EDIT);
        String correlation = "STAGE_SUBMIT:" + command.executionId() + ":" + idempotencyKey;
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenantId, "PROJECT_STAGE_SUBMIT", actorId, idempotencyKey),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(command)), Submitted.class, () -> submitOnce(command, tenantId, actorId, correlation),
                submitted -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_STAGE_SUBMITTED", "ProjectNodeExecution", submitted.executionId().toString(),
                        correlation, JsonUtils.toJsonString(Map.of("executionId", submitted.executionId(), "roundNo", submitted.roundNo())),
                        List.of(new ProjectRuleReevaluation(tenantId, command.projectId(), actorId, correlation)
                                .event())));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return result.response();
    }

    private Submitted submitOnce(Command command, Long tenantId, Long actorId, String correlation) {
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId, command.projectId()));
        if (project == null || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_TASK_COMMAND_INVALID);
        requireScope(tenantId, command.projectId(), actorId, ProjectScopeApi.ACTION_EDIT);
        var scope = new ProjectPlanScopeQuery(tenantId, project.getId());
        var plan = plans.selectEffective(scope);
        var round = executions.selectCurrentForUpdate(scope).stream().filter(item -> command.executionId().equals(item.getId())).findFirst()
                .orElseThrow(() -> exception(PROJECT_TASK_VERSION_CONFLICT));
        if (plan == null || !plan.getId().equals(round.getPlanVersionId()) || !"STAGE".equals(round.getNodeKind())
                || !"ACTIVE".equals(round.getStatus()) || round.getSubmittedAt() != null || !Objects.equals(round.getVersion(), command.expectedVersion()))
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        var definition = snapshot.getStages().stream().filter(node -> round.getNodeKey().equals(node.getNodeKey())).findFirst()
                .orElseThrow(() -> exception(PROJECT_TASK_COMMAND_INVALID));
        if (definition.getBinding() == null || !"STAGE_NATIVE".equals(definition.getBinding().getType()))
            throw exception(PROJECT_TASK_COMMAND_INVALID); // Owner business/approval outcomes cannot be replaced by a manual claim.
        if (executions.submitIfCurrent(new ProjectNodeExecutionMapper.Submission(tenantId, project.getId(), round.getId(), round.getVersion(),
                actorId, LocalDateTime.now(), command.note().trim())) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        coordinator.reevaluate(project.getId(), actorId, correlation);
        var latest = executions.selectById(round.getId());
        return new Submitted(latest.getId(), latest.getRoundNo(), latest.getStatus(), latest.getVersion());
    }

    private boolean hasScope(Long tenantId, Long projectId, Long actorId, String action) {
        var result = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorId, projectId, action));
        return result != null && result.fullProjectIds() != null && result.fullProjectIds().contains(projectId);
    }
    private void requireScope(Long tenantId, Long projectId, Long actorId, String action) {
        if (!hasScope(tenantId, projectId, actorId, action)) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
    }
}
