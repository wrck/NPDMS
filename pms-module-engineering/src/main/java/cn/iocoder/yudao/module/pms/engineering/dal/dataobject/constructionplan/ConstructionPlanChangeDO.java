package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** SOL项目工期变更与审批关联。 */
@TableName("sol_construction_plan_change")
@Data
public class ConstructionPlanChangeDO implements Serializable {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @TableId
    private Long id;
    private Long planId;
    private Long baseRevisionId;
    private Long candidateRevisionId;
    private String statusCode;
    private String reasonTypeCode;
    private String reasonDetail;
    private Boolean customerEvidenceRequired;
    private Long customerEvidenceFileId;
    private Integer customerEvidenceFileVersion;
    private String customerEvidenceReferenceKey;
    private Integer customerEvidenceArtifactVersion;
    private Integer customerEvidenceReferenceVersion;
    private Integer customerEvidenceAvailabilityVersion;
    private Long customerEvidenceScopeVersion;
    private String processDefinitionKey;
    private String processInstanceId;
    private LocalDateTime submittedAt;
    private Long applicantUserId;
    private Long approverUserId;
    private LocalDateTime approvedAt;
    private String approvalOpinion;
    private LocalDateTime createdAt;
    private Integer version;
    private Long tenantId;

}
