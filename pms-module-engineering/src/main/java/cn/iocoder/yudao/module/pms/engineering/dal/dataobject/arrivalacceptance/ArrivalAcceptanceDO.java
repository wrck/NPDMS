package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("imp_arrival_acceptance")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArrivalAcceptanceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private String batchCode;
    private String logisticsNo;
    private LocalDateTime arrivedAt;
    private String signerSnapshot;
    private String status;
    private Long deliveryScopeVersion;
    private String expectedScopeSnapshot;
    private String scopeWatermark;
    private String migrationResolutionStatus;
    private String migrationReasonCode;
    private Long legacySourceId;
    private Long projectFactVersion;
    private Long evidenceId;
    private Integer evidenceRevision;
    private Long predecessorAcceptanceId;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    @Version
    private Integer version;
}
