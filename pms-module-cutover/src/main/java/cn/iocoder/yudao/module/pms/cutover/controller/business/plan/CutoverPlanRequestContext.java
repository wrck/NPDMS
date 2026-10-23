package cn.iocoder.yudao.module.pms.cutover.controller.business.plan;

import cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext;

/** HTTP 边界解析已有业务上下文，不接受客户端自报身份或权限。 */
public interface CutoverPlanRequestContext {
    CutoverPlanContext current();
}
