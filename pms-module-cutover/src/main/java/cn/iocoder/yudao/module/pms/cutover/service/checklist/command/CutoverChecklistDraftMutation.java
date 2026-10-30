package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import cn.iocoder.yudao.module.pms.cutover.service.task.command.CutoverTaskMutation;

/** P3 草稿命令已有的锁定条件；不复制命令载荷或新增一套参数对象。 */
public interface CutoverChecklistDraftMutation extends CutoverTaskMutation {
    Long checklistId();
    Long expectedChecklistVersion();
    Long expectedProjectScopeVersion();
}
