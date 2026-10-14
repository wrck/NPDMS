package cn.iocoder.yudao.module.bpm.service.cutover;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmModelConvert;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.*;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.yudao.module.bpm.enums.definition.*;
import cn.iocoder.yudao.module.bpm.service.definition.*;
import cn.iocoder.yudao.module.bpm.service.task.*;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectApprovalProcessCreationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cn.iocoder.yudao.module.bpm.service.cutover.CutoverBpmGuard.*;

@Service
@RequiredArgsConstructor
public class CutoverWorkflowService implements CutoverWorkflowApi {
    private final RepositoryService repository;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final TaskService tasks;
    private final BpmTaskService taskService;
    private final BpmProcessInstanceService instances;
    private final BpmProcessDefinitionService definitions;
    private final BpmModelService models;
    private final ProjectApprovalProcessCreationApi creation;
    private final AdminUserApi users;
    private final PermissionApi permissions;
    private final CutoverBpmGuard guard;
    private final CutoverRoleTaskAccess roleAccess;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String provision(Long tenant, Long manager) {
        identity(tenant, manager);
        if (!permissions.hasAnyPermissions(manager, "bpm:model:deploy") || !permissions.hasAnyPermissions(manager, "pms:cutover-config:publish")) {
            throw new SecurityException("初始化割接审批须具备流程发布和割接配置发布权限");
        }
        var active = definitions.getActiveProcessDefinition(DEFINITION_KEY);
        if (active != null) {
            if (!tenant.toString().equals(active.getTenantId())) throw new SecurityException("割接审批模型租户不一致");
            String previous = active.getId();
            if (CutoverWorkflowDefinitionPolicy.supportsRoles(repository.getBpmnModel(previous))) return definition(tenant);
            var info = definitions.getProcessDefinitionInfo(previous);
            var existing = models.getModel(info.getModelId());
            if (existing == null || !tenant.toString().equals(existing.getTenantId()))
                throw new IllegalArgumentException("割接审批模型来源不存在");
            var revision = BeanUtils.toBean(BpmModelConvert.INSTANCE.parseMetaInfo(existing), BpmModelSaveReqVO.class);
            revision.setId(existing.getId()); revision.setKey(DEFINITION_KEY);
            revision.setName(existing.getName()); revision.setCategory(existing.getCategory());
            revision.setBpmnXml(templateXml());
            models.updateModel(manager, revision);
            models.deployModel(manager, existing.getId());
            return definition(tenant);
        }
        users.validateUser(manager);
        var model = new BpmModelSaveReqVO();
        model.setKey(DEFINITION_KEY); model.setName("割接分级评审"); model.setCategory("pms");
        model.setType(BpmModelTypeEnum.BPMN.getType()); model.setFormType(BpmModelFormTypeEnum.CUSTOM.getType());
        model.setFormCustomCreatePath("/pms/cutover/workbench"); model.setFormCustomViewPath("/pms/cutover/approval-form/index.vue");
        model.setVisible(false); model.setManagerUserIds(List.of(manager));
        model.setAutoApprovalType(BpmAutoApproveTypeEnum.NONE.getType());
        model.setAllowWithdrawTask(false); model.setAllowCancelRunningProcess(true);
        model.setBpmnXml(templateXml());
        String id = models.createModel(model);
        models.deployModel(manager, id);
        return definition(tenant);
    }
    @Override
    public String definition(Long tenant) {
        requireTenant(tenant);
        var definition = definitions.getActiveProcessDefinition(DEFINITION_KEY);
        if (definition == null || !tenant.toString().equals(definition.getTenantId())) throw new IllegalArgumentException("请先初始化割接分级审批流程");
        var info = definitions.getProcessDefinitionInfo(definition.getId());
        if (info == null || !Objects.equals(info.getAutoApprovalType(), BpmAutoApproveTypeEnum.NONE.getType())) throw new IllegalArgumentException("割接审批不得自动通过");
        var model = repository.getBpmnModel(definition.getId());
        CutoverWorkflowDefinitionPolicy.validate(model);
        return definition.getId();
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String start(Start command) {
        identity(command.tenantId(), command.actorId());
        List<String> expected = switch (command.grade()) {
            case "A" -> List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE", "RND");
            case "B" -> List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE");
            case "C", "D" -> List.of("INITIATOR", "SERVICE_MANAGER");
            default -> throw new IllegalArgumentException("割接等级无效");
        };
        if (!expected.equals(command.nodes().stream().map(Node::code).toList())
                || !Objects.equals(command.nodes().getFirst().userId(), command.actorId())) throw new IllegalArgumentException("割接审批路由未完整解析");
        users.validateUserList(command.nodes().stream().map(Node::userId).filter(Objects::nonNull).distinct().toList());
        command.nodes().stream().map(Node::role).filter(Objects::nonNull)
                .forEach(role -> roleAccess.requireRole(new CutoverRoleTaskAccess.RoleQuery(command.tenantId(), role)));
        String definition = definition(command.tenantId());
        if (command.nodes().stream().anyMatch(node -> node.role() != null)
                && !CutoverWorkflowDefinitionPolicy.supportsRoles(repository.getBpmnModel(definition)))
            throw new IllegalArgumentException("请在割接配置中心更新角色审批流程定义");
        if (history.createHistoricProcessInstanceQuery().processInstanceTenantId(command.tenantId().toString())
                .processDefinitionKey(DEFINITION_KEY).processInstanceBusinessKey(command.approvalId().toString()).count() > 0) throw new IllegalStateException("割接方案版本已发起审批");
        var variables = new HashMap<String, Object>();
        variables.put("cutoverGrade", command.grade());
        List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE", "RND").forEach(code -> variables.put("cutoverUser_" + code, null));
        command.nodes().forEach(node -> {
            if (node.role() == null) variables.put("cutoverUser_" + node.code(), node.userId());
            else {
                variables.put("cutoverRoleId_" + node.code(), node.role().roleId());
                variables.put("cutoverRoleCode_" + node.code(), node.role().roleCode());
                variables.put("cutoverGroup_" + node.code(), node.role().groupKey());
            }
        });
        variables.put("cutoverTaskId", command.taskId()); variables.put("projectId", command.projectId());
        return guard.start(command, () -> creation.create(new ProjectApprovalProcessCreationApi.Command(command.tenantId(), command.actorId(),
                DEFINITION_KEY, definition, command.approvalId().toString(), variables, Map.of())));
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void decide(Decision command) {
        identity(command.tenantId(), command.actorId());
        requireInstance(command.tenantId(), command.processInstanceId());
        var task = tasks.createTaskQuery().processInstanceId(command.processInstanceId()).taskDefinitionKey(command.nodeCode())
                .taskTenantId(command.tenantId().toString()).singleResult();
        if (task == null || task.getAssignee() != null && !Objects.equals(task.getAssignee(), command.actorId().toString())
                || command.feedback() == null || command.feedback().isBlank()) throw new IllegalArgumentException("割接BPM当前待办或意见不匹配");
        guard.mutate(new Operation(command.processInstanceId(), command.nodeCode(), command.actorId(), command.approved() ? "APPROVE" : "REJECT"), () -> {
            if (task.getAssignee() == null) {
                var role = runtime.getIdentityLinksForProcessInstance(command.processInstanceId()).stream()
                        .filter(link -> (PREFIX + "role:" + command.nodeCode()).equals(link.getType())).toList();
                String group = role.size() == 1 ? role.getFirst().getGroupId() : null;
                if (group == null || !roleAccess.lockEligible(new CutoverRoleTaskAccess.Qualification(
                        new cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi.ActorQuery(command.tenantId(), command.actorId()),
                        command.nodeCode(), group))
                        || tasks.createTaskQuery().taskId(task.getId()).taskUnassigned().taskCandidateGroup(group)
                                .taskTenantId(command.tenantId().toString()).count() != 1)
                    throw new IllegalArgumentException("当前用户不具备本角色待办资格");
                tasks.claim(task.getId(), command.actorId().toString());
            }
            if (command.approved()) {
                var request = new BpmTaskApproveReqVO(); request.setId(task.getId()); request.setReason(command.feedback()); request.setVariables(Map.of());
                taskService.approveTask(command.actorId(), request);
            } else {
                var request = new BpmTaskRejectReqVO(); request.setId(task.getId()); request.setReason(command.feedback());
                taskService.rejectTask(command.actorId(), request);
            }
        });
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void reassign(Reassignment command) {
        identity(command.tenantId(), command.actorId()); requireInstance(command.tenantId(), command.processInstanceId()); users.validateUser(command.userId());
        if (command.reason() == null || command.reason().isBlank()) throw new IllegalArgumentException("改派原因不能为空");
        guard.mutate(new Operation(command.processInstanceId(), command.nodeCode(), command.actorId(), "REASSIGN"), () -> {
            var links = runtime.getIdentityLinksForProcessInstance(command.processInstanceId()).stream().filter(link -> (CURRENT + command.nodeCode()).equals(link.getType())).toList();
            if (links.size() > 1) throw new IllegalStateException("割接待办候选身份不唯一");
            if (!links.isEmpty()) runtime.deleteUserIdentityLink(command.processInstanceId(), links.getFirst().getUserId(), CURRENT + command.nodeCode());
            runtime.addUserIdentityLink(command.processInstanceId(), command.userId().toString(), CURRENT + command.nodeCode());
            if (!List.of("INITIATOR", "SERVICE_MANAGER").contains(command.nodeCode()))
                throw new IllegalArgumentException("角色审批节点不允许改派个人");
            runtime.setVariable(command.processInstanceId(), "cutoverUser_" + command.nodeCode(), command.userId());
            var task = tasks.createTaskQuery().processInstanceId(command.processInstanceId()).taskDefinitionKey(command.nodeCode()).singleResult();
            if (task != null) { tasks.addComment(task.getId(), command.processInstanceId(), "CUT_REASSIGN", "操作人 " + command.actorId() + "：" + command.reason()); tasks.setAssignee(task.getId(), command.userId().toString()); }
        });
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void cancel(Cancellation cancellation) {
        Long tenant = cancellation.tenantId(); Long actor = cancellation.actorId();
        String instance = cancellation.processInstanceId(); String reason = cancellation.reason();
        identity(tenant, actor); requireInstance(tenant, instance);
        guard.mutate(new Operation(instance, null, actor, "CANCEL"), () -> {
            var command = new BpmProcessInstanceCancelReqVO(); command.setId(instance); command.setReason(reason);
            instances.cancelProcessInstanceByStartUser(actor, command);
        });
    }
    private void requireInstance(Long tenant, String id) {
        var process = runtime.createProcessInstanceQuery().processInstanceTenantId(tenant.toString()).processInstanceId(id).singleResult();
        if (process == null || !DEFINITION_KEY.equals(process.getProcessDefinitionKey())) throw new IllegalArgumentException("割接BPM实例不存在或已结束");
    }
    private static void identity(Long tenant, Long actor) {
        requireTenant(tenant);
        if (actor == null || !Objects.equals(actor, SecurityFrameworkUtils.getLoginUserId())) throw new SecurityException("割接BPM操作人身份不一致");
    }
    private static String templateXml() {
        try (var input = new ClassPathResource("bpmn/cutover/PMS_CUTOVER_APPROVAL.bpmn").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException error) { throw new IllegalStateException("割接审批模板资源缺失", error); }
    }
}
