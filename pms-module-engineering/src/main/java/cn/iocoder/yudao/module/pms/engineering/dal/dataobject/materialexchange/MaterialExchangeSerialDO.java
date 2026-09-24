package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 换货申请设备行明细快照：设备清单行 = 合同对应销售订单行的交付范围分配（范围明细拆分行或未拆分范围行）。
 * scopeDetailId/scopeId 为清单行稳定引用；equipmentId/sn/productModel/contractNo 仅兼容旧序列号快照，新行不再写入。
 * 不修改设备清单、设备档案或历史申请。
 */
@TableName("imp_eng_material_exchange_serial")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialExchangeSerialDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long exchangeId;
    /** 清单行引用：交付范围明细拆分行 */
    private Long scopeDetailId;
    /** 清单行引用：无明细拆分的交付范围行 */
    private Long scopeId;
    private String orderNo;
    private String lineNo;
    private String itemCode;
    /** 换货数量，按清单行填写 */
    private BigDecimal quantity;
    private String name;
    private String productCode;
    private String deviceTypeCode;
    private String deviceTypeName;
    /** 兼容旧序列号快照：原设备ID */
    private Long equipmentId;
    /** 兼容旧序列号快照：原设备序列号 */
    private String sn;
    /** 兼容旧序列号快照 */
    private String productModel;
    /** 兼容旧序列号快照 */
    private String contractNo;
}
