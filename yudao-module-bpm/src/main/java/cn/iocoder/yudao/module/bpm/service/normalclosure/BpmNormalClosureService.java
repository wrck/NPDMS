package cn.iocoder.yudao.module.bpm.service.normalclosure;

import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmAutoApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.*;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.*;
import static cn.iocoder.yudao.module.bpm.service.normalclosure.BpmNormalClosureGuard.*;

@Service
@RequiredArgsConstructor
public class BpmNormalClosureService implements BpmNormalClosureApi {
    public static final String BPMN_RESOURCE = "bpmn/normalclosure/PMS_MINIMAL_NORMAL_CLOSURE.bpmn";
    public static final String MATERIAL_PERMISSION = "pms:acc-project-closure:audit";
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final BpmProcessDefinitionService definitionService;
    private final BpmProcessInstanceService instanceService;
    private final AdminUserApi adminUserApi;
    private final ExplicitPermissionApi explicitPermissionApi;
    private final BpmNormalClosureGuard guard;

    @Override
    public Definition inspectDefinition(Long tenantId, String key) {
        requireTenant(tenantId);
        requireKey(key);
        ProcessDefinition definition = definitionService.getActiveProcessDefinition(key);
        if (definition == null || !tenantId.toString().equals(definition.getTenantId())) {
            throw new IllegalArgumentException("No active tenant NORMAL definition has been explicitly deployed");
        }
        validateDefinition(definition);
        var model = repositoryService.getBpmnModel(definition.getId());
        List<Node> nodes = List.of(MANAGER_NODE, MATERIAL_NODE).stream().map(nodeKey -> {
            UserTask task = (UserTask) model.getFlowElement(nodeKey);
            // Labels are candidate responsibilities, not newly created fixed system roles.
            return new Node(task.getId(), task.getName(), MANAGER_NODE.equals(nodeKey)
                    ? "SERVICE_MANAGER" : "MATERIAL_REVIEWER");
        }).toList();
        return new Definition(definition.getId(), definition.getKey(), nodes);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Started start(StartCommand command) {
        Objects.requireNonNull(command, "command");
        requireTenant(command.tenantId());
        requireKey(command.processDefinitionKey());
        requireText(command.businessKey(), "businessKey");
        if (command.actorId() == null || command.projectId() == null || command.serviceManagerUserId() == null
                || command.materialReviewerUserId() == null) {
            throw new IllegalArgumentException("Actor, project and both authorized candidates are required");
        }
        // Existing System API applies tenant filtering and enabled-user validation. Owner still owns project scope.
        adminUserApi.validateUserList(new HashSet<>(List.of(command.actorId(), command.serviceManagerUserId(),
                command.materialReviewerUserId())));
        if (!explicitPermissionApi.lockAndCheck(command.tenantId(), command.materialReviewerUserId(), MATERIAL_PERMISSION)) {
            throw new IllegalArgumentException("Material reviewer has no explicit closure audit grant");
        }
        Definition definition = inspectDefinition(command.tenantId(), command.processDefinitionKey());
        if (historyService.createHistoricProcessInstanceQuery().processInstanceTenantId(command.tenantId().toString())
                .processDefinitionKey(PROCESS_DEFINITION_KEY).processInstanceBusinessKey(command.businessKey()).count() != 0) {
            throw new IllegalArgumentException("NORMAL closure business key has already been used; reapply with a new instance key");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put(MANAGER_VARIABLE, command.serviceManagerUserId());
        variables.put(MATERIAL_VARIABLE, command.materialReviewerUserId());
        variables.put("projectId", command.projectId());
        String id = guard.withAuthorizedStart(command, () -> instanceService.createProcessInstance(command.actorId(),
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
                        .setBusinessKey(command.businessKey()).setVariables(variables)));
        var instance = runtimeService.createProcessInstanceQuery().processInstanceId(id)
                .processInstanceTenantId(command.tenantId().toString()).singleResult();
        if (instance == null || !definition.actualDefinitionId().equals(instance.getProcessDefinitionId())) {
            throw new IllegalStateException("NORMAL deployed definition changed during start");
        }
        var links = runtimeService.getIdentityLinksForProcessInstance(id);
        List<AssignedNode> assigned = definition.nodes().stream().map(node -> {
            var candidates = links.stream().filter(link -> (LINK_PREFIX + node.taskDefinitionKey()).equals(link.getType())).toList();
            if (candidates.size() != 1) {
                throw new IllegalStateException("NORMAL candidate was not frozen");
            }
            return new AssignedNode(node.taskDefinitionKey(), Long.valueOf(candidates.getFirst().getUserId()));
        }).toList();
        return new Started(id, instance.getProcessDefinitionId(), assigned);
    }

    @Override
    public Result inspectResult(Long tenantId, String processInstanceId) {
        requireTenant(tenantId);
        requireText(processInstanceId, "processInstanceId");
        var process = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId)
                .processInstanceTenantId(tenantId.toString()).includeProcessVariables().singleResult();
        if (process == null || !PROCESS_DEFINITION_KEY.equals(process.getProcessDefinitionKey())) {
            throw new IllegalArgumentException("NORMAL instance not found in this tenant");
        }
        validateDefinition(repositoryService.getProcessDefinition(process.getProcessDefinitionId()));
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId).taskTenantId(tenantId.toString()).finished()
                .includeTaskLocalVariables().orderByHistoricTaskInstanceEndTime().asc()
                .orderByTaskId().asc().list();
        List<Review> reviews = tasks.stream().map(task -> {
            BpmTaskStatusEnum state = BpmTaskStatusEnum.valueOf(FlowableUtils.getTaskStatus(task));
            return new Review(task.getId(), task.getTaskDefinitionKey(),
                    task.getAssignee() == null ? null : Long.valueOf(task.getAssignee()),
                    state == null ? "UNKNOWN" : state.name(),
                    (String) task.getTaskLocalVariables().get(TASK_VARIABLE_REASON),
                    task.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }).toList();
        BpmProcessInstanceStatusEnum state = BpmProcessInstanceStatusEnum.valueOf(FlowableUtils.getProcessInstanceStatus(process));
        String status = state == null ? "UNKNOWN" : state.name();
        // Neither a spoofed process status nor a mere engine end is sufficient evidence of two manual approvals.
        if (process.getEndTime() == null) {
            status = BpmProcessInstanceStatusEnum.RUNNING.name();
        } else if (state == BpmProcessInstanceStatusEnum.APPROVE) {
            var frozen = historyService.getHistoricIdentityLinksForProcessInstance(processInstanceId);
            boolean approved = reviews.size() == 2;
            for (String node : List.of(MANAGER_NODE, MATERIAL_NODE)) {
                var candidates = frozen.stream().filter(link -> (LINK_PREFIX + node).equals(link.getType())).toList();
                approved &= candidates.size() == 1 && reviews.stream().filter(review -> node.equals(review.taskDefinitionKey())
                        && "APPROVE".equals(review.decision()) && review.assigneeUserId() != null
                        && candidates.getFirst().getUserId().equals(review.assigneeUserId().toString())).count() == 1;
            }
            if (!approved) {
                throw new IllegalStateException("Ended NORMAL instance lacks its two frozen-candidate approval histories");
            }
        }
        return new Result(process.getId(), process.getProcessDefinitionId(), process.getBusinessKey(), status, reviews);
    }

    private void validateDefinition(ProcessDefinition definition) {
        requireKey(definition.getKey());
        BpmProcessDefinitionInfoDO info = definitionService.getProcessDefinitionInfo(definition.getId());
        if (info == null || !Objects.equals(BpmAutoApproveTypeEnum.NONE.getType(), info.getAutoApprovalType())) {
            throw new IllegalStateException("NORMAL requires registered BPM metadata with automatic approval disabled");
        }
        // A matching key alone must not admit a differently authored or auto-approving workflow.
        try (InputStream deployed = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName())) {
            if (!Arrays.equals(resourceBytes(), deployed.readAllBytes())) {
                throw new IllegalStateException("NORMAL definition is not the approved packaged manual-review template");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot verify NORMAL deployed resource", ex);
        }
    }

    static byte[] resourceBytes() {
        try (InputStream stream = new ClassPathResource(BPMN_RESOURCE).getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Missing NORMAL BPMN resource", ex);
        }
    }

    private static void requireKey(String key) {
        if (!PROCESS_DEFINITION_KEY.equals(key)) {
            throw new IllegalArgumentException("Only PMS_MINIMAL_NORMAL_CLOSURE is approved by this API");
        }
    }

    private static void requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
