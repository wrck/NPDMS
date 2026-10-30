package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverPlanSupportContactChangeDO;

public record CutoverSupportContactUpdate(CutoverPlanRevisionDO plan, CutoverSupportArrangementDO baseline,
                                          CutoverPlanSupportContactChangeDO change, Long expectedPlanVersion) {}
