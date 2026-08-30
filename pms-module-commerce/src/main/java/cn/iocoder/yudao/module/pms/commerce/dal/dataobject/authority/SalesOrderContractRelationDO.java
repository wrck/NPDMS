package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("com_sales_order_contract_relation")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderContractRelationDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long salesOrderId;
    private Long contractId;
    private String relationStatus;
    private String sourceSystem;
    private String salesOrderSourceKey;
    private String contractSourceKey;
    private String sourceVersion;
    private String sourceEvidence;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
