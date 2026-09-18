package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.Objects;

/** Installs frozen subscriptions in the same authorized project-plan/round transaction. */
@Service
@RequiredArgsConstructor
public class ProjectResultSubscriptionInstaller {
    private final ProjectMasterMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ResultSubscriptionMapper subscriptions;
    private final ProjectBusinessResultSources sources;
    private final ProjectBusinessResultJournal journal;
    private final PlatformBusinessEventApi outbox;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void synchronize(Long projectId, Long planVersionId, TemplateExecutionSnapshot supplied) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var project = projects.selectByIdForUpdate(projectId);
        var scope = new ProjectPlanScopeQuery(tenant, projectId);
        var plan = plans.selectEffective(scope);
        if (project == null || plan == null || !Objects.equals(tenant,project.getTenantId())
                || !Objects.equals(projectId,project.getId()) || !Objects.equals(tenant,plan.getTenantId())
                || !Objects.equals(projectId,plan.getProjectId()) || !"EFFECTIVE".equals(plan.getStatus())
                || !Objects.equals(planVersionId,plan.getId()) || !Objects.equals(planVersionId,project.getActivePlanVersionId()))
            throw new IllegalArgumentException("SUBSCRIPTION_PLAN_MISMATCH");
        var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        if (!snapshot.equals(supplied)) throw new IllegalArgumentException("SUBSCRIPTION_SNAPSHOT_MISMATCH");
        var rounds = executions.selectCurrentForUpdate(scope);
        if (rounds == null || rounds.stream().anyMatch(round -> round == null
                || !Objects.equals(tenant, round.getTenantId()) || !Objects.equals(projectId, round.getProjectId())
                || !Integer.valueOf(1).equals(round.getCurrentMarker())))
            throw new IllegalArgumentException("SUBSCRIPTION_EXECUTION_MISMATCH");
        for (var node : ResultSubscriptionContract.nodes(snapshot)) {
            var matches = rounds.stream().filter(round -> Objects.equals(planVersionId,round.getPlanVersionId())
                    && Objects.equals(node.kind(),round.getNodeKind()) && Objects.equals(node.key(),round.getNodeKey())).toList();
            // Completed nodes may retain their previous plan and subscription; never recreate their history.
            if (matches.isEmpty() && rounds.stream().anyMatch(round -> Objects.equals(node.kind(),round.getNodeKind())
                    && Objects.equals(node.key(),round.getNodeKey()) && round.getEndedAt() != null)) continue;
            if (matches.size() != 1) throw new IllegalArgumentException("SUBSCRIPTION_EXECUTION_MISMATCH");
            var round = matches.getFirst();
            if (!Objects.equals(tenant,round.getTenantId()) || !Objects.equals(projectId,round.getProjectId())
                    || !Integer.valueOf(1).equals(round.getCurrentMarker()) || !positive(round.getId())
                    || !positive(round.getNodeInstanceId()) || !positive(round.getContractId()))
                throw new IllegalArgumentException("SUBSCRIPTION_EXECUTION_MISMATCH");
            for (var configuration : node.subscriptions()) install(tenant,projectId,planVersionId,round,configuration);
        }
    }

    private void install(Long tenant, Long project, Long plan, ProjectNodeExecutionDO round, JsonNode configuration) {
        var definition = ResultSubscriptionContract.read(configuration);
        var type = ResultSubscriptionContract.type(definition);
        var descriptor = sources.descriptor(type);
        if (descriptor == null || !sources.changeSupported(type)
                || ("PINNED_RESULT".equals(definition.policy().acquisition()) ? !descriptor.exactLookup() : !sources.inventorySupported(type))
                || "HISTORICAL_FACT".equals(definition.policy().validity()) && !descriptor.historicalLookup())
            throw new IllegalArgumentException("SUBSCRIPTION_SOURCE_UNAVAILABLE");
        var identity = new Identity(tenant,project,plan,round.getId(),definition.key());
        var previous = subscriptions.selectIdentityForUpdate(identity);
        if (previous != null) {
            requireIdentity(previous,round,tenant,project,plan,definition.key());
            if (!configuration.equals(JsonUtils.parseTree(previous.getConfiguration())))
                throw new IllegalStateException("SUBSCRIPTION_VERSION_IMMUTABLE");
            return;
        }
        // All rebases of this exact execution/key inherit its first boundary; read one bounded origin.
        var ancestor = subscriptions.selectOrigin(new Origin(tenant,project,round.getId(),definition.key()));
        var boundary = journal.capture(tenant,project,type);
        long baseline = boundary.sequence();
        if (ancestor != null) {
            if (!Objects.equals(tenant,ancestor.getTenantId()) || !Objects.equals(project,ancestor.getProjectId())
                    || !Objects.equals(round.getId(),ancestor.getExecutionId()) || !definition.key().equals(ancestor.getSubscriptionKey())
                    || !Objects.equals(boundary.channel().id(),ancestor.getChannelId()) || ancestor.getBaselineSequence() == null
                    || ancestor.getBaselineSequence() < 0 || ancestor.getBaselineSequence() > boundary.sequence())
                throw new IllegalStateException("SUBSCRIPTION_ROUND_BOUNDARY_MISMATCH");
            baseline = ancestor.getBaselineSequence();
        } else if (round.getStartedAt() != null || round.getEndedAt() != null) {
            throw new IllegalArgumentException("SUBSCRIPTION_NEW_SOURCE_REQUIRES_NEW_ROUND");
        }
        var row = new ResultSubscriptionDO();
        row.setId(IdWorker.getId()); row.setTenantId(tenant); row.setProjectId(project); row.setPlanVersionId(plan);
        row.setExecutionId(round.getId()); row.setNodeKind(round.getNodeKind()); row.setNodeId(round.getNodeInstanceId());
        row.setNodeKey(round.getNodeKey()); row.setContractId(round.getContractId()); row.setSubscriptionKey(definition.key());
        row.setChannelId(boundary.channel().id()); row.setConfiguration(JsonUtils.toJsonString(configuration));
        row.setBaselineSequence(baseline); row.setProcessedSequence(baseline);
        row.setPhase("INVENTORY"); row.setVersion(0);
        if (subscriptions.insert(row) != 1) throw new IllegalStateException("SUBSCRIPTION_INSTALL_CONFLICT");
        var event = ResultSubscriptionWakeup.create(row);
        outbox.append("ResultSubscription",row.getId().toString(),new BusinessEvent(event.eventId(),ResultSubscriptionWakeup.EVENT_TYPE,JsonUtils.toJsonString(event)));
    }

    private void requireIdentity(ResultSubscriptionDO row, ProjectNodeExecutionDO round, Long tenant, Long project, Long plan, String key) {
        if (!Objects.equals(tenant,row.getTenantId()) || !Objects.equals(project,row.getProjectId()) || !Objects.equals(plan,row.getPlanVersionId())
                || !Objects.equals(round.getId(),row.getExecutionId()) || !Objects.equals(round.getContractId(),row.getContractId())
                || !Objects.equals(round.getNodeKind(),row.getNodeKind()) || !Objects.equals(round.getNodeKey(),row.getNodeKey())
                || !Objects.equals(round.getNodeInstanceId(),row.getNodeId()) || !key.equals(row.getSubscriptionKey()))
            throw new IllegalStateException("SUBSCRIPTION_IDENTITY_MISMATCH");
    }
    private boolean positive(Long id) { return id != null && id > 0; }
}
