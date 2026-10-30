package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineFact;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineRef;

import java.util.List;

/**
 * 校验设备清单行属于当前项目：清单行 = 合同对应销售订单行的交付范围分配（范围明细拆分行或未拆分范围行）。
 * 只读事实校验，不锁定交付范围，不校验换货数量上限（数量上限非本项目授权的阈值）。
 */
public interface DeliveryScopeLineFactApi {

    List<DeliveryScopeLineFact> validateSelection(Long projectId, List<DeliveryScopeLineRef> lines);
}
