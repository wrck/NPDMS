package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_plan_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverPlanRevisionDO extends BaseBusinessEntity {
    private Long cutoverTaskId;
    private Integer revisionNo;
    private String originCode;
    private String editModeCode;
    private String gradeCode;
    private Long assessmentId;
    private Long assessmentVersion;
    private Long checklistId;
    private Long checklistVersion;
    private Long configurationRevisionId;
    private String configurationCode;
    private Integer configurationRevisionNo;
    private String templateSectionSnapshot;
    private String sourceSnapshot;
    private String contentSnapshot;
    private Long fileArtifactId;
    private Integer fileVersionNo;
    private String fileReferenceKey;
    private String fileFactVersion;
    private Long fileScopeVersion;
    private String fileSha256;
    private Boolean ownershipConfirmed;
    private String statusCode;
    private Integer currentMarker;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long approvalInstanceId;
    private Long approvalVersion;
    private Long sourcePlanRevisionId;
    private String revisionReasonCode;
    private Long invalidatedBy;
    private LocalDateTime invalidatedAt;
    private String invalidationReasonCode;
    private Long legacyPlanId;
    private Integer legacyStatusRaw;
    private Integer legacySourceVersion;
    private String legacyMappingVersion;
}
