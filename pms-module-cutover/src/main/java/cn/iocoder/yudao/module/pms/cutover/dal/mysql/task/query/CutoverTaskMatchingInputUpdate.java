package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchInput;

public record CutoverTaskMatchingInputUpdate(CutoverTaskDO task, CutoverChecklistMatchInput input, Long actorId) { }
