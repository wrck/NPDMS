package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext;

/** HTTP 边界解析已有业务上下文，不接受客户端自报身份或权限。 */
public interface CutoverApprovalRequestContext {
    CutoverApprovalContext current();
}
