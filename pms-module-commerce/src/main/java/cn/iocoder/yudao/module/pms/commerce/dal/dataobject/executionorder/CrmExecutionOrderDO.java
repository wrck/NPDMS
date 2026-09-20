package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("com_crm_execution_order")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmExecutionOrderDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String sourceSystem; private String executionNo; private String projectCode; private String projectName;
    private Long primaryProjectId; private String salesRepCode; private String salesRepName; private String salesRepPhone;
    private String marketCode; private String marketName; private String systemSourceKey; private String systemCode;
    private String systemName; private String expendSourceKey; private String expendCode; private String expendName;
    private String industryCode; private String industryName; private Long departmentId; private String departmentCode;
    private String departmentName; private String serviceTypeName; private String channelName; private String engineeringFeeRaw;
    private BigDecimal engineeringFee; private String sourceObjectId; private String applyType; private Long companyId;
    private String companyCode; private String companyName; private String customerProjectName; private String finalCustomerName;
    private String agentName; private String projectManagerCode; private String projectManagerName; private String decisionPath;
    private java.time.LocalDate requiredInDate; private String receiverName; private String receiverContact; private String receiverAddress;
    private String loanReason; private String projectType; private String majorProjectLevel; private BigDecimal projectAmount;
    private BigDecimal afProjectAmount; private LocalDateTime submitTime; private LocalDateTime predictedBidTime;
    private String contactName; private String contactPhone; private String afEvidenceStatus; private LocalDateTime sourceSyncTime;
    private String status; @Version private Integer version;
}
