package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 申请设备序列号快照；不修改设备档案或历史申请。 */
@TableName("imp_eng_material_exchange_serial")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialExchangeSerialDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long exchangeId;
    private Long equipmentId;
    private String sn;
    private String name;
    private String productCode;
    private String productModel;
    private String contractNo;
}
