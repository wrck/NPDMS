package cn.iocoder.yudao.module.pms.cutover.service.approval.command;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;

/** 只由已锁定任务及方案的修订事务调用。 */
public record WithdrawCutoverApprovalCommand(CutoverTaskDO task, CutoverPlanRevisionDO plan, Long actorId, String reason) { }
