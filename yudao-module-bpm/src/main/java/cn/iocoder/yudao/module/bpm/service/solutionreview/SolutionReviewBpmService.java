package cn.iocoder.yudao.module.bpm.service.solutionreview;

import cn.iocoder.yudao.module.bpm.api.solutionreview.SolutionReviewBpmApi;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmAutoApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectApprovalProcessCreationApi;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.util.*;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.TASK_VARIABLE_REASON;
import static cn.iocoder.yudao.module.bpm.service.solutionreview.SolutionReviewBpmGuard.*;

@Service
@RequiredArgsConstructor
public class SolutionReviewBpmService implements SolutionReviewBpmApi {
    private final RepositoryService repository;
    private final HistoryService history;
    private final BpmProcessDefinitionService definitions;
    private final ProjectApprovalProcessCreationApi creation;
    private final SolutionReviewBpmGuard guard;

    @Override public Definition definition(Long tenant) {
        requireTenant(tenant);
        var definition = definitions.getActiveProcessDefinition(DEFINITION_KEY);
        if (definition == null) throw new IllegalArgumentException("方案分级审核流程尚未发布");
        return inspectDefinition(tenant, definition.getId());
    }

    private Definition inspectDefinition(Long tenant, String id) {
        var definition = repository.getProcessDefinition(id);
        if (definition == null || !DEFINITION_KEY.equals(definition.getKey())
                || !tenant.toString().equals(definition.getTenantId())) throw new IllegalArgumentException("方案审批定义不匹配");
        var info = definitions.getProcessDefinitionInfo(id);
        if (info == null || !Objects.equals(info.getAutoApprovalType(), BpmAutoApproveTypeEnum.NONE.getType()))
            throw new IllegalArgumentException("方案分级审批不允许自动通过");
        var process = repository.getBpmnModel(id).getMainProcess();
        if (process.getExecutionListeners().stream().noneMatch(listener -> "start".equals(listener.getEvent())
                && "${solutionReviewBpmGuard.freeze(execution)}".equals(listener.getImplementation())))
            throw new IllegalArgumentException("方案流程缺少业务身份冻结监听器");
        List<Node> nodes = process.findFlowElementsOfType(UserTask.class).stream().map(task -> {
            String role = responsibility(task);
            if (!Set.of("SERVICE_MANAGER", "ENGINEERING_MANAGEMENT").contains(role))
                throw new IllegalArgumentException("方案节点缺少受支持的审核职责");
            for (String event : List.of("create", "complete", "delete"))
                if (task.getTaskListeners().stream().noneMatch(listener -> event.equals(listener.getEvent())
                        && "${solutionReviewBpmGuard.validate(task)}".equals(listener.getImplementation())))
                    throw new IllegalArgumentException("方案节点缺少候选人重验监听器");
            return new Node(task.getId(), task.getName(), role);
        }).toList();
        if (nodes.stream().noneMatch(node -> "SERVICE_MANAGER".equals(node.responsibility()))
                || nodes.stream().noneMatch(node -> "ENGINEERING_MANAGEMENT".equals(node.responsibility())))
            throw new IllegalArgumentException("重大方案流程须包含服务经理和工程管理部审核");
        requireBranchGateway(process);
        return new Definition(id, definition.getKey(), nodes);
    }

    /** 服务经理审核之后必须按提交时冻结的审核级别分支：重大进入工程管理部复审，普通直接结束。 */
    private void requireBranchGateway(org.flowable.bpmn.model.Process process) {
        var serviceManagerTask = process.findFlowElementsOfType(UserTask.class).stream()
                .filter(task -> "SERVICE_MANAGER".equals(responsibility(task))).findFirst().orElse(null);
        var engineeringTask = process.findFlowElementsOfType(UserTask.class).stream()
                .filter(task -> "ENGINEERING_MANAGEMENT".equals(responsibility(task))).findFirst().orElse(null);
        if (serviceManagerTask == null || engineeringTask == null) throw new IllegalArgumentException("方案节点职责缺失");
        var gateway = serviceManagerTask.getOutgoingFlows().stream()
                .map(flow -> flow.getTargetFlowElement()).filter(ExclusiveGateway.class::isInstance)
                .map(ExclusiveGateway.class::cast).findFirst().orElse(null);
        if (gateway == null || gateway.getOutgoingFlows().size() != 2
                || gateway.getOutgoingFlows().stream().noneMatch(flow -> flow.getTargetFlowElement() == engineeringTask)
                || gateway.getOutgoingFlows().stream().noneMatch(flow -> flow.getTargetFlowElement() instanceof EndEvent))
            throw new IllegalArgumentException("方案流程须在服务经理审核后按审核级别分支");
        var majorFlow = gateway.getOutgoingFlows().stream()
                .filter(flow -> flow.getTargetFlowElement() == engineeringTask).findFirst().orElse(null);
        if (majorFlow == null || majorFlow.getConditionExpression() == null
                || !majorFlow.getConditionExpression().contains("solutionReviewLevel"))
            throw new IllegalArgumentException("工程管理部复审分支缺少审核级别条件");
    }

    static String responsibility(BaseElement task) {
        var values = task.getExtensionElements().get("pmsReviewResponsibility");
        return values == null || values.size() != 1 ? "" : values.getFirst().getElementText();
    }

    @Override @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Started start(Start command) {
        requireTenant(command.tenantId());
        var definition = inspectDefinition(command.tenantId(), command.definitionId());
        // 候选人由系统按节点职责解析：服务经理节点取本项目唯一有效服务经理，工程管理部节点取
        // major-review 显式授权的唯一持有人；提交人不指定审批人，也不排除申请人本人
        Map<String, Long> candidates = new LinkedHashMap<>();
        for (var node : definition.nodes()) {
            // 分支网关只在重大方案（审核级别 1）路由到工程管理部复审；普通路径该节点不执行，
            // 不解析、不冻结其候选人，授权未配置不得阻塞普通方案提交
            if ("ENGINEERING_MANAGEMENT".equals(node.responsibility()) && command.reviewLevel() != 1) continue;
            Long candidate = "SERVICE_MANAGER".equals(node.responsibility())
                    ? guard.resolveServiceManager(command.tenantId(), command.projectId())
                    : guard.resolveEngineeringReviewer(command.tenantId());
            guard.authorize(command.tenantId(), candidate, command.projectId(), node.responsibility());
            candidates.put(node.key(), candidate);
        }
        if (history.createHistoricProcessInstanceQuery().processInstanceTenantId(command.tenantId().toString())
                .processDefinitionKey(DEFINITION_KEY).processInstanceBusinessKey(command.businessKey()).count() != 0)
            throw new IllegalArgumentException("此方案版本已经发起审批");
        Map<String, Object> variables = new HashMap<>();
        variables.put("projectId", command.projectId());
        // 分支网关在服务经理审核后按提交时冻结的审核级别路由：重大走工程管理部复审，普通直接结束
        variables.put("solutionReviewLevel", command.reviewLevel());
        variables.put("solutionReviewCandidates", Map.copyOf(candidates));
        String id = guard.starting(command, candidates, () -> creation.create(new ProjectApprovalProcessCreationApi.Command(
                command.tenantId(), command.actorId(), definition.key(), definition.id(), command.businessKey(), variables, Map.of())));
        return new Started(id, definition.id(), Map.copyOf(candidates));
    }

    @Override public Result result(Long tenant, String instanceId) {
        requireTenant(tenant);
        var process = history.createHistoricProcessInstanceQuery().processInstanceTenantId(tenant.toString())
                .processInstanceId(instanceId).includeProcessVariables().singleResult();
        if (process == null || !DEFINITION_KEY.equals(process.getProcessDefinitionKey()))
            throw new IllegalArgumentException("方案审批实例不存在");
        var definition = inspectDefinition(tenant, process.getProcessDefinitionId());
        var reviews = history.createHistoricTaskInstanceQuery().processInstanceId(instanceId).taskTenantId(tenant.toString())
                .finished().includeTaskLocalVariables().orderByHistoricTaskInstanceEndTime().asc().list().stream().map(task -> {
                    var state = BpmTaskStatusEnum.valueOf(FlowableUtils.getTaskStatus(task));
                    return new Review(task.getTaskDefinitionKey(), task.getAssignee() == null ? null : Long.valueOf(task.getAssignee()),
                            state == null ? "UNKNOWN" : state.name(), (String) task.getTaskLocalVariables().get(TASK_VARIABLE_REASON),
                            task.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                }).toList();
        var state = BpmProcessInstanceStatusEnum.valueOf(FlowableUtils.getProcessInstanceStatus(process));
        String status = process.getEndTime() == null ? "RUNNING" : state == null ? "UNKNOWN" : state.name();
        if ("APPROVE".equals(status)) {
            var links = history.getHistoricIdentityLinksForProcessInstance(instanceId);
            for (var node : definition.nodes()) {
                // 分支网关未路由到的节点不产生审核记录；服务经理审核是所有路径的必经节点
                boolean executed = history.createHistoricActivityInstanceQuery().processInstanceId(instanceId)
                        .activityId(node.key()).finished().count() > 0;
                if (!executed) continue;
                var candidates = links.stream().filter(link -> (PREFIX + node.key()).equals(link.getType())).toList();
                if (candidates.size() != 1 || reviews.stream().noneMatch(review -> node.key().equals(review.nodeKey())
                        && "APPROVE".equals(review.decision()) && review.userId() != null
                        && candidates.getFirst().getUserId().equals(review.userId().toString())))
                    throw new IllegalStateException("方案未完成实际流程要求的全部人工审核");
            }
            boolean serviceManagerReviewed = definition.nodes().stream()
                    .filter(node -> "SERVICE_MANAGER".equals(node.responsibility()))
                    .anyMatch(node -> history.createHistoricActivityInstanceQuery().processInstanceId(instanceId)
                            .activityId(node.key()).finished().count() > 0);
            if (!serviceManagerReviewed) throw new IllegalStateException("方案缺少服务经理审核记录");
        }
        return new Result(instanceId, process.getProcessDefinitionId(), process.getBusinessKey(), status, reviews);
    }
}
