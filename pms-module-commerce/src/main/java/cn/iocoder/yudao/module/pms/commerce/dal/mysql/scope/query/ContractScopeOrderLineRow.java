package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContractScopeOrderLineRow extends SalesOrderLineDO {
    private Boolean contractAssociated;
    private String orderAuthorityStatus;
    private String orderLifecycleStatus;
    private String orderStatus;
}
