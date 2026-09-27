package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

/**
 * 审批发起：主体、用途、提交标识、提交依据与中性流程引用。
 * 过程由引擎执行；不可手工伪造批准，原生实例标识只出现在关联记录。
 */
public record ApprovalSubmissionRequest(
        EntityRef subject,
        String purpose,
        String attemptId,
        String submissionBasis,
        String neutralProcessRef) {
}
