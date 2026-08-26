package cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cus_customer_field_history")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerFieldHistoryDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long customerId;
    private String fieldName;
    private String fieldOwner;
    private String beforeValueDigest;
    private String afterValueDigest;
    private String sourceType;
    private String operationId;
    private Long operatorId;
    private LocalDateTime occurredAt;
}
