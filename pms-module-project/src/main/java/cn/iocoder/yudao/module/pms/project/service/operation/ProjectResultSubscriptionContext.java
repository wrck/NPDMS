package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 锁顺序与订阅安装一致。旧消息只退休原订阅，不查找新的替代轮次。 */
@Service
@RequiredArgsConstructor
public class ProjectResultSubscriptionContext {
    private final ProjectMasterMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ResultSubscriptionMapper subscriptions;

    public record Locked(ResultSubscriptionDO subscription, ProjectMasterDO project, ProjectPlanVersionDO plan,
                         ProjectNodeExecutionDO execution, TemplateExecutionSnapshot snapshot) { }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Locked lock(ResultSubscriptionWakeup event) {
        if (event == null || !Objects.equals(event.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("SUBSCRIPTION_TENANT_MISMATCH");
        var project = projects.selectByIdForUpdate(event.projectId());
        if (project == null || !Objects.equals(event.tenantId(), project.getTenantId()) || !Objects.equals(event.projectId(), project.getId()))
            throw new IllegalArgumentException("SUBSCRIPTION_PROJECT_MISMATCH");
        var rounds = executions.selectCurrentForUpdate(new ProjectPlanScopeQuery(event.tenantId(), event.projectId()));
        var row = subscriptions.selectByIdForUpdate(new IdQuery(event.tenantId(), event.projectId(), event.subscriptionId()));
        if (row == null) return null;
        if (!event.matches(row)) throw new IllegalArgumentException("SUBSCRIPTION_WAKEUP_IDENTITY_MISMATCH");
        if ("RETIRED".equals(row.getPhase())) return null;
        if (rounds == null) throw new IllegalStateException("SUBSCRIPTION_EXECUTION_UNAVAILABLE");
        var matches = rounds.stream().filter(round -> round != null && Objects.equals(round.getId(), row.getExecutionId())).toList();
        if (matches.size() > 1) throw new IllegalStateException("SUBSCRIPTION_EXECUTION_AMBIGUOUS");
        if (matches.isEmpty()) return retire(row);
        var round = matches.getFirst();
        if (!Objects.equals(row.getTenantId(), round.getTenantId()) || !Objects.equals(row.getProjectId(), round.getProjectId()))
            throw new IllegalStateException("SUBSCRIPTION_EXECUTION_SCOPE_MISMATCH");
        if (!Integer.valueOf(1).equals(round.getCurrentMarker()) || !Objects.equals(row.getPlanVersionId(), round.getPlanVersionId())
                || !Objects.equals(row.getContractId(), round.getContractId()) || !Objects.equals(row.getNodeKind(), round.getNodeKind())
                || !Objects.equals(row.getNodeKey(), round.getNodeKey()) || !Objects.equals(row.getNodeId(), round.getNodeInstanceId())
                || round.getEndedAt() == null && !Objects.equals(project.getActivePlanVersionId(), row.getPlanVersionId())) return retire(row);
        var plan = plans.selectById(row.getPlanVersionId());
        if (plan == null || !Objects.equals(row.getTenantId(), plan.getTenantId()) || !Objects.equals(row.getProjectId(), plan.getProjectId())
                || !Objects.equals(row.getPlanVersionId(), plan.getId()) || !("EFFECTIVE".equals(plan.getStatus()) || "SUPERSEDED".equals(plan.getStatus())))
            throw new IllegalStateException("SUBSCRIPTION_PLAN_MISMATCH");
        var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        var nodes = ResultSubscriptionContract.nodes(snapshot).stream()
                .filter(node -> Objects.equals(node.kind(), row.getNodeKind()) && Objects.equals(node.key(), row.getNodeKey())).toList();
        if (nodes.size() != 1) throw new IllegalStateException("SUBSCRIPTION_CONFIGURATION_MISMATCH");
        var definitions = nodes.getFirst().subscriptions().stream()
                .filter(value -> row.getSubscriptionKey().equals(ResultSubscriptionContract.read(value).key())).toList();
        if (definitions.size() != 1 || !definitions.getFirst().equals(JsonUtils.parseTree(row.getConfiguration())))
            throw new IllegalStateException("SUBSCRIPTION_CONFIGURATION_MISMATCH");
        return new Locked(row, project, plan, round, snapshot);
    }

    private Locked retire(ResultSubscriptionDO row) {
        if (subscriptions.retire(new Retirement(row.getTenantId(), row.getProjectId(), row.getId(), row.getVersion())) != 1)
            throw new IllegalStateException("SUBSCRIPTION_RETIRE_CONFLICT");
        return null;
    }
}
