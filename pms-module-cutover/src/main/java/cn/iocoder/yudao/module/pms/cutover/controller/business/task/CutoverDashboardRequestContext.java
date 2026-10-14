package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardContext;

/** HTTP边界组合各阶段的可信身份和权限，服务层不依赖Controller。 */
public interface CutoverDashboardRequestContext {

    CutoverDashboardContext current();
}
