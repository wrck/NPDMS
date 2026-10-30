package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

import java.time.LocalDateTime;

@TableName("imp_arrival_acceptance")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArrivalAcceptanceDO extends BaseBusinessEntity {

    private Long projectId;
    private String batchCode;
    private Integer batchRootMarker;
    private String logisticsNo;
    private LocalDateTime arrivedAt;
    private String signerSnapshot;
    private String status;
    private Long projectVersion;
    private Long projectParticipantFactVersion;
    private Long projectScopeVersion;
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
    private String successorReason;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
}
