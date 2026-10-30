package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 交付范围当前分页投影；主档展示字段不参与范围写入或历史快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryScopePageRow extends DeliveryScopeDO {
    private String productModel;
    private String productName;
}
