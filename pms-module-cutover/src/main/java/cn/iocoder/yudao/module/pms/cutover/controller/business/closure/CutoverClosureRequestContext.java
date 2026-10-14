package cn.iocoder.yudao.module.pms.cutover.controller.business.closure;

import cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext;

/** HTTP 边界解析已有业务上下文，不接受客户端自报身份或权限。 */
public interface CutoverClosureRequestContext {
    CutoverClosureContext current();
}
