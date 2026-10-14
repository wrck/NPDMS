package cn.iocoder.yudao.module.pms.cutover.service.dashboard.model;

import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext;
import cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext;
import cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext;

/** 任务统计与列表共用的服务端身份、权限上下文。 */
public record CutoverDashboardContext(CutoverTaskContext task, CutoverPlanContext plan,
                                      CutoverApprovalContext approval, CutoverClosureContext closure) {
    public CutoverDashboardContext {
        if (task == null || plan == null || approval == null || closure == null
                || task.tenantId() == null || task.actorId() == null || task.tenantId() <= 0 || task.actorId() <= 0
                || !task.tenantId().equals(plan.tenantId()) || !task.actorId().equals(plan.actorId())
                || !task.tenantId().equals(approval.tenantId()) || !task.actorId().equals(approval.actorId())
                || !task.tenantId().equals(closure.tenantId()) || !task.actorId().equals(closure.actorId())) {
            throw new IllegalArgumentException("割接查询上下文身份不一致");
        }
    }
}
