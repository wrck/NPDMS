package cn.iocoder.yudao.module.pms.cutover.api.task;

import cn.iocoder.yudao.module.pms.cutover.api.task.dto.CutoverTaskIntakeCommand;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.CutoverTaskIntakeResult;

/** ITR与项目事件接入CUT任务的稳定公共边界。 */
public interface CutoverTaskIntakeApi {

    CutoverTaskIntakeResult create(CutoverTaskIntakeCommand command);
}
