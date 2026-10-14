package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;

public record CutoverPlanDraftUpdate(CutoverPlanRevisionDO plan, Long expectedVersion, Long newVersion) {}
