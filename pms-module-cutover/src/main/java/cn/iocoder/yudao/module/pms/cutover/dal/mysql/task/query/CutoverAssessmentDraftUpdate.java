package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;

public record CutoverAssessmentDraftUpdate(CutoverAssessmentDO assessment, Long expectedVersion) { }
