package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("com_delivery_scope_detail")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryScopeDetailDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long deliveryScopeId;
    private Integer detailSequence;
    // Historical callers retain this value; the current table stores office facts on the scope root.
    @TableField(exist = false)
    private String officeDepartmentCode;
    private String serialNo;
    private String productCode;
    private String productName;
    private String deviceTypeCode;
    private String deviceTypeName;
    private String deliveryBatchNo;
    private String sourceRecordKey;
    private BigDecimal allocatedQty;
    private String detailStatus;
    private String sourceSnapshot;
    private String remark;
    @Version
    private Integer version;
}
