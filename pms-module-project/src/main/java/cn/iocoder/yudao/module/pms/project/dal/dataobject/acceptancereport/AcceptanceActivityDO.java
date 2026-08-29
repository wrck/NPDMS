package cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("acc_acceptance")
@Data
@EqualsAndHashCode(callSuper = true)
public class AcceptanceActivityDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private Long projectTaskId;
    private Long executionContractId;
    private String acceptanceType;
    private String activityStatus;
    private Long currentReportVersionId;
    private Integer version;
}
