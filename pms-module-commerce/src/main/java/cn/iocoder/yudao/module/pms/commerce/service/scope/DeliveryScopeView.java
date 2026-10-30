package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopePageRow;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderFieldsRow;

import java.util.List;

public record DeliveryScopeView(DeliveryScopePageRow scope, List<DeliveryScopeDetailDO> details, DeliveryScopeOrderFieldsRow orderFields) {
    public DeliveryScopeView(DeliveryScopePageRow scope, List<DeliveryScopeDetailDO> details) { this(scope,details,null); }
    public String contractNo() { return orderFields == null ? null : orderFields.contractNo(); }
}
