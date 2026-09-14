package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.BusinessFactEvidence;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskBusinessCompletionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves a version-local source against the current round under the caller's project command lock. */
@Component
@RequiredArgsConstructor
public class ProjectBusinessFactSourceService {
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectTaskExecutionContractMapper contracts;
    private final ProjectNodeExecutionApi contexts;
    private final ProjectTaskBusinessService business;

    public RuleFact resolve(ProjectMasterDO project, RuleProgram.Leaf leaf) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !Objects.equals(project.getTenantId(), TenantContextHolder.getTenantId()))
            return RuleFact.unknown("BUSINESS_SOURCE_CONTEXT_UNAVAILABLE");
        String source = leaf.parameters().path("sourceNodeKey").asText();
        if (source.isBlank()) return RuleFact.unknown("BUSINESS_SOURCE_NODE_REQUIRED");
        var scope = new ProjectPlanScopeQuery(project.getTenantId(), project.getId());
        var plan = plans.selectEffective(scope);
        if (plan == null || !Objects.equals(project.getActivePlanVersionId(), plan.getId()))
            return RuleFact.unknown("BUSINESS_SOURCE_PLAN_UNAVAILABLE");
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        var matches = executions.selectCurrentForUpdate(scope).stream()
                .filter(row -> source.equals(row.getNodeKey())).toList();
        if (matches.size() != 1) return RuleFact.unknown("BUSINESS_SOURCE_EXECUTION_UNAVAILABLE");
        var round = matches.getFirst();
        if (!Objects.equals(project.getTenantId(), round.getTenantId()) || !Objects.equals(project.getId(), round.getProjectId())
                || !Integer.valueOf(1).equals(round.getCurrentMarker()) || round.getId() == null)
            return RuleFact.unknown("BUSINESS_SOURCE_EXECUTION_UNAVAILABLE");
        var stages = snapshot.getStages().stream().filter(node -> source.equals(node.getNodeKey())).toList();
        var tasks = snapshot.getTasks().stream().filter(node -> source.equals(node.getNodeKey())).toList();
        if (("STAGE".equals(round.getNodeKind()) ? stages.size() != 1 || !tasks.isEmpty()
                : !"TASK".equals(round.getNodeKind()) || tasks.size() != 1 || !stages.isEmpty()))
            return RuleFact.unknown("BUSINESS_SOURCE_NODE_UNAVAILABLE");

        List<TaskBusinessLinkFact> links;
        if ("DONE".equals(round.getStatus())) {
            // Completed rounds intentionally retain their original plan ID after a plan revision.
            var result = round.getResultSnapshot() == null ? null : JsonUtils.parseTree(round.getResultSnapshot()).path("businessFacts");
            if (result == null || !result.isObject()) return RuleFact.unknown("BUSINESS_SOURCE_EVIDENCE_UNAVAILABLE");
            var evidence = JsonUtils.convertObject(result, BusinessFactEvidence.class);
            if (!Objects.equals(round.getId(), evidence.executionId()) || !Objects.equals(round.getPlanVersionId(), evidence.planVersionId()))
                return RuleFact.unknown("BUSINESS_SOURCE_EVIDENCE_STALE");
            links = evidence.results().stream().map(item -> new TaskBusinessLinkFact(item.associationId(), item.objectId(),
                    "业务判断结果", item.factVersion(), item.facts(), List.of(), Set.of())).toList();
        } else {
            if (!"ACTIVE".equals(round.getStatus()) || !Objects.equals(plan.getId(), round.getPlanVersionId()))
                return RuleFact.unknown("BUSINESS_SOURCE_ROUND_NOT_READY");
            TaskBusinessCompletionFacts result;
            if ("STAGE".equals(round.getNodeKind())) {
                var context = contexts.inspectStage(new ProjectStageExecutionQuery(project.getId(), round.getNodeInstanceId(), round.getContractId()));
                if (!context.writable() || !Objects.equals(round.getId(), context.executionId()))
                    return RuleFact.unknown("BUSINESS_SOURCE_EXECUTION_STALE");
                result = business.lockStageCompletionFacts(project.getTenantId(), context, stages.getFirst().getBinding());
            } else {
                var context = contexts.inspect(new ProjectTaskExecutionQuery(project.getId(), round.getNodeInstanceId(), round.getContractId()));
                if (!context.writable() || !Objects.equals(round.getId(), context.executionId()))
                    return RuleFact.unknown("BUSINESS_SOURCE_EXECUTION_STALE");
                var contract = contracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(project.getTenantId(), round.getNodeInstanceId()));
                if (contract == null || !Objects.equals(round.getContractId(), contract.getId()))
                    return RuleFact.unknown("BUSINESS_SOURCE_CONTRACT_STALE");
                result = business.lockCompletionFacts(project.getTenantId(), context, contract);
            }
            if (result == null || result.facts() == null) return RuleFact.unknown("BUSINESS_SOURCE_FACT_UNAVAILABLE");
            links = result.facts().links();
        }
        if (links.isEmpty()) return RuleFact.unknown("BUSINESS_SOURCE_FACT_UNAVAILABLE");
        return TaskBusinessCompletionEvaluator.businessFact(leaf, links, new ArrayList<>(), new ArrayList<>());
    }

    public static BusinessFactEvidence freeze(ProjectNodeExecutionDO round, List<TaskBusinessLinkFact> links) {
        return new BusinessFactEvidence(round.getId(), round.getPlanVersionId(), links.stream()
                .map(link -> new BusinessFactEvidence.Result(link.id(), link.objectId(), link.factVersion(), link.completionFacts())).toList());
    }
}
