package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderFieldsRow;

import java.util.List;

public record DeliveryScopeView(DeliveryScopeDO scope, List<DeliveryScopeDetailDO> details, DeliveryScopeOrderFieldsRow orderFields) {
    public DeliveryScopeView(DeliveryScopeDO scope, List<DeliveryScopeDetailDO> details) { this(scope,details,null); }
    public String contractNo() { return orderFields == null ? null : orderFields.contractNo(); }
}
