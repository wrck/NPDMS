package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.CutoverApprovalNodesQuery;
import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeRoleApprovalSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 与CUT命令同一事务衔接BPM；任何引擎失败均保留原业务阶段。 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class CutoverWorkflowCoordinator {
    private final CutoverWorkflowApi workflow;
    private final CutoverApprovalWorkflowMapper links;
    private final CutoverRuntimeApprovalNodeMapper nodes;

    public void start(CutoverApprovalInstanceDO instance, Long actor) {
        if (links.selectById(instance.getId()) != null) throw new IllegalStateException("割接审批已关联BPM");
        var route = nodes.all(new CutoverApprovalNodesQuery(instance.getTenantId(), instance.getId())).stream()
                .map(node -> {
                    var role = CutoverRuntimeRoleApprovalSnapshot.from(node);
                    return role != null && role.byRole()
                            ? new CutoverWorkflowApi.Node(node.getNodeCode(), null,
                                    new CutoverWorkflowApi.RoleReference(role.roleGroupId(), role.roleGroupCode()))
                            : new CutoverWorkflowApi.Node(node.getNodeCode(), node.getCurrentApproverUserId());
                }).toList();
        String process = workflow.start(new CutoverWorkflowApi.Start(instance.getTenantId(), actor, instance.getProjectId(),
                instance.getTaskId(), instance.getId(), instance.getGradeCode(), route));
        var link = new CutoverApprovalWorkflowDO(); link.setId(instance.getId()); link.setTenantId(instance.getTenantId());
        link.setTaskId(instance.getTaskId()); link.setProcessInstanceId(process); link.setVersion(0L);
        link.setCreator(actor.toString()); link.setUpdater(actor.toString());
        if (links.insert(link) != 1) throw new IllegalStateException("割接BPM运行身份保存失败");
    }
    public void decide(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node,
            CutoverRuntimeApprovalApplicationService.DecisionBusinessInput command, Long actor) {
        workflow.decide(new CutoverWorkflowApi.Decision(instance.getTenantId(), actor, link(instance).getProcessInstanceId(),
                node.getNodeCode(), "APPROVE".equals(command.action()), command.feedback()));
    }
    public void reassign(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node,
            cn.iocoder.yudao.module.pms.cutover.service.approval.command.ReassignCutoverApprovalCommand command, Long actor) {
        workflow.reassign(new CutoverWorkflowApi.Reassignment(instance.getTenantId(), actor, link(instance).getProcessInstanceId(),
                node.getNodeCode(), command.newApproverUserId(), command.reason()));
    }
    public void cancel(CutoverApprovalInstanceDO instance, Long actor) {
        workflow.cancel(new CutoverWorkflowApi.Cancellation(instance.getTenantId(), actor,
                link(instance).getProcessInstanceId(), "割接方案来源已失效，重新编制后审批"));
    }
    private CutoverApprovalWorkflowDO link(CutoverApprovalInstanceDO instance) {
        var link = links.selectById(instance.getId());
        if (link == null || !Objects.equals(link.getTenantId(), instance.getTenantId()) || !Objects.equals(link.getTaskId(), instance.getTaskId())) throw new IllegalStateException("割接审批缺少BPM运行身份");
        return link;
    }
}
