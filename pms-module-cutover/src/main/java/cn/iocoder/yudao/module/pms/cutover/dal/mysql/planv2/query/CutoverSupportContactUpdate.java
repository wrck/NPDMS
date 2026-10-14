package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverPlanSupportContactChangeDO;

public record CutoverSupportContactUpdate(CutoverPlanRevisionDO plan, CutoverSupportArrangementDO baseline,
                                          CutoverPlanSupportContactChangeDO change, Long expectedPlanVersion) {}
