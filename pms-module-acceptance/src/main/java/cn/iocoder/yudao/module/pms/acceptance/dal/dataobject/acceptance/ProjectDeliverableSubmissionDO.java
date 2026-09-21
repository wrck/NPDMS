package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("acc_project_deliverable_submission")
public class ProjectDeliverableSubmissionDO {
    @TableId private Long id;
    private Long tenantId;
    private Long projectId;
    private Long deliverableId;
    private Long planVersionId;
    private Long sourceVersionId;
    private String requestKey;
    private String requestPayload;
    private String configurationSnapshot;
    private String sourceType;
    private String sourceEvidence;
    private String decisionEvidence;
    private String creator;
    private LocalDateTime createTime;
}
