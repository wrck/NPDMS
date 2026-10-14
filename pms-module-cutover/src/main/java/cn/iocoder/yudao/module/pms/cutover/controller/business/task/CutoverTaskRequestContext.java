package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;

/** HTTP 边界解析已有业务上下文，不接受客户端自报身份或权限。 */
public interface CutoverTaskRequestContext {
    CutoverTaskContext current();
}
