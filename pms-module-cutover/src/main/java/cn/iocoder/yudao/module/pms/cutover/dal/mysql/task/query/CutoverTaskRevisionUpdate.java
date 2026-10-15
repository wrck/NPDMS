package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;

public record CutoverTaskRevisionUpdate(CutoverTaskDO source, CutoverTaskRules.RevisionTarget target,
                                       Long assessmentId, Long actorId, CutoverConfigurationRevisionDO configuration) { }
