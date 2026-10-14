package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;

public record CutoverAssessmentDraftUpdate(CutoverAssessmentDO assessment, Long expectedVersion) { }
