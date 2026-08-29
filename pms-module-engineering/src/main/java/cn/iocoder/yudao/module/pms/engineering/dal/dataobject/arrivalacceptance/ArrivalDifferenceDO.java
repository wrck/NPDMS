package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("imp_arrival_difference")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArrivalDifferenceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long arrivalAcceptanceId;
    private Long arrivalLineId;
    private Integer differenceNo;
    private Integer revisionNo;
    private String differenceType;
    private String resolutionStatus;
    private String reason;
    private String riskDescription;
    private String scopeSnapshot;
    private Long projectFactVersion;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime exemptionExpiresAt;
    private Long evidenceId;
    private Integer evidenceRevision;
    private Integer currentMarker;
    @Version
    private Integer version;
}
