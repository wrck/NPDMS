package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopePageRow;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;

import java.util.List;

public record DeliveryScopeView(DeliveryScopePageRow scope, List<DeliveryScopeDetailDO> details, String contractNo) {
}
