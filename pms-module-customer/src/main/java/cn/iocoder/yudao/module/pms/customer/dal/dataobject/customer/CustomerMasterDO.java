package cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cus_customer_master")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerMasterDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String name;
    private String shortName;
    private String lifecycleStatus;
    private String sourceType;
    private String sourceKey;
    private String sourceVersion;
    private String syncStatus;
    private LocalDateTime dataAsOf;
    private Boolean reconciliationPending;
    private String temporaryReason;
    private String contactPhone;
    private String contactEmail;
    private String departmentCode;
    private String departmentName;
    private String marketCode;
    private String marketName;
    private String systemCode;
    private String systemName;
    private String expendCode;
    private String expendName;
    private String industryCode;
    private String industryName;
    private String legacyAddressSnapshot;
    private String remark;
    @Version
    private Integer version;
}
