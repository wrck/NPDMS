package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;

import java.util.List;

public record DeliveryScopeView(DeliveryScopeDO scope, List<DeliveryScopeDetailDO> details) {
}
