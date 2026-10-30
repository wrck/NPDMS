package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;

public record CutoverPlanDraftUpdate(CutoverPlanRevisionDO plan, Long expectedVersion, Long newVersion) {}
