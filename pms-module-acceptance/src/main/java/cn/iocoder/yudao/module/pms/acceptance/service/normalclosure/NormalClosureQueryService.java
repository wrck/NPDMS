package cn.iocoder.yudao.module.pms.acceptance.service.normalclosure;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureCheckApi;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureContextApi;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.normalclosure.NormalClosureMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.acceptance.service.normalclosure.NormalClosureApplicationService.Actor;
import static cn.iocoder.yudao.module.pms.acceptance.service.normalclosure.NormalClosureErrors.failure;
import static cn.iocoder.yudao.module.pms.acceptance.service.normalclosure.NormalClosureViews.*;

@Service @RequiredArgsConstructor
public class NormalClosureQueryService {
    private final ProjectClosureContextApi contexts;
    private final ProjectClosureCheckApi checkApi;
    private final NormalClosureMapper mapper;
    private final PermissionApi permissions;

    @Transactional(rollbackFor = Exception.class)
    public Overview get(Long projectId, Actor actor) {
        var context = contexts.read(actor.tenantId(), projectId, actor.userId(), ProjectClosureContextApi.PERMISSION_QUERY);
        var query = new NormalClosureMapper.ProjectQuery(actor.tenantId(), projectId);
        var latest = mapper.selectLatestApplication(query);
        var snapshot = mapper.selectLatestSnapshot(query);
        List<Check> checks = new ArrayList<>();
        boolean policyAvailable = false;
        try {
            NormalClosurePolicy.parseFrozen(context.closurePolicySnapshot());
            policyAvailable = true;
            checks.add(new Check("CLOSURE_POLICY_FROZEN", true, null, projectId));
        } catch (ServiceException ex) {
            checks.add(new Check("CLOSURE_POLICY", false, ex.getMessage(), projectId));
        }
        boolean active = "ACTIVE".equals(context.lifecycleStatus());
        checks.add(new Check("PROJECT_ACTIVE", active, active ? null : "PROJECT_NOT_ACTIVE", projectId));
        if (policyAvailable && active) {
            try {
                var graph = checkApi.inspectGraph(actor.tenantId(), projectId);
                checks.add(new Check("TERMINAL_STAGE", graph.terminal(), graph.terminal() ? null : "NOT_TERMINAL", graph.currentStageId()));
                boolean completed = "SATISFIED".equals(graph.completionStatus());
                checks.add(new Check("STAGE_COMPLETION", completed, completed ? null : graph.completionStatus(), graph.currentStageId()));
            } catch (RuntimeException ex) {
                checks.add(new Check("RUNTIME_GRAPH", false, "RUNTIME_GRAPH_UNAVAILABLE", projectId));
            }
            checks.add(new Check("ALL_TASKS_DONE", graphDone(actor.tenantId(), projectId), graphDone(actor.tenantId(), projectId) ? null : "TASKS_NOT_DONE", projectId));
        }
        boolean snapshotCurrent = snapshot != null && Boolean.TRUE.equals(snapshot.getPassed())
                && Objects.equals(snapshot.getProjectVersion(), context.projectVersion())
                && Objects.equals(snapshot.getTreeVersion(), context.treeVersion())
                && Objects.equals(snapshot.getFromStage(), context.currentStage());
        checks.add(new Check("CHECK_SNAPSHOT", snapshotCurrent, snapshotCurrent ? "OWNER_FACTS_REVALIDATED_ON_SUBMIT" : "NEW_CHECK_REQUIRED", snapshot == null ? null : snapshot.getId()));
        List<String> actions = new ArrayList<>();
        if (policyAvailable && active && permissions.hasAnyPermissions(actor.userId(), ProjectClosureContextApi.PERMISSION_SUBMIT)
                && (latest == null || Set.of("REJECTED", "CANCELLED").contains(latest.getStatus()))) {
            actions.add("CHECK");
            if (snapshotCurrent && checks.stream().allMatch(Check::passed)
                    && (latest == null || !Objects.equals(latest.getSnapshotId(), snapshot.getId()))) actions.add("SUBMIT");
        }
        return new Overview(projectId, context.projectVersion(), context.treeVersion(), context.currentStage(), context.lifecycleStatus(),
                policyAvailable, List.copyOf(checks), snapshot, latest, List.copyOf(actions));
    }

    private boolean graphDone(Long tenantId, Long projectId) {
        return checkApi.inspectGraph(tenantId, projectId).allTasksDone();
    }

    /** Resolve only the tenant-bound identity; the existing application query owns authorization. */
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public ApplicationDetail processViewByApplicationId(Long applicationId, Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.userId() == null
                || !Objects.equals(actor.tenantId(), cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId()))
            throw failure("CLOSURE_PERMISSION_DENIED");
        if (applicationId == null || applicationId <= 0) throw failure("CLOSURE_APPLICATION_NOT_FOUND");
        Long projectId = mapper.selectApplicationProjectId(
                new NormalClosureMapper.ApplicationIdentityQuery(actor.tenantId(), applicationId));
        if (projectId == null) throw failure("CLOSURE_APPLICATION_NOT_FOUND");
        return application(projectId, applicationId, actor);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplicationDetail application(Long projectId, Long applicationId, Actor actor) {
        contexts.read(actor.tenantId(), projectId, actor.userId(), ProjectClosureContextApi.PERMISSION_QUERY);
        var query = new NormalClosureMapper.ApplicationQuery(actor.tenantId(), projectId, applicationId);
        var application = mapper.selectApplication(query);
        if (application == null) throw failure("CLOSURE_APPLICATION_NOT_FOUND");
        return new ApplicationDetail(projectId, application, mapper.selectSnapshot(
                new NormalClosureMapper.SnapshotQuery(actor.tenantId(), projectId, application.getSnapshotId())), mapper.selectReviews(query));
    }
}
