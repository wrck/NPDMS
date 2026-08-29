package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("com_delivery_scope")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryScopeDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private String projectCompanyCode;
    private String projectCompanyName;
    private String projectDepartmentCode;
    private String projectDepartmentName;
    private String projectCustomerCode;
    private String projectCustomerName;
    private String projectManagerEmployeeNo;
    private String projectManagerName;
    private Long orderLineId;
    private String orderSourceSystem;
    private String orderCompanyCode;
    private String orderCompanyName;
    private String orderType;
    private String orderNo;
    private String lineNo;
    private String itemCode;
    private String itemDesc;
    private BigDecimal allocatedQty;
    private String scopeStatus;
    private Long allocationVersion;
    private String allocationSource;
    private String changeReason;
    private Long officeDepartmentId;
    private String officeDepartmentCode;
    private String officeDepartmentName;
    private Integer officeDepartmentVersion;
    private String sourceEvidence;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
    @Version
    private Integer version;
}
