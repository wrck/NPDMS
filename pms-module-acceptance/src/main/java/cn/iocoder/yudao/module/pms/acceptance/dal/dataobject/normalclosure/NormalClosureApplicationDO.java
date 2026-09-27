package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("acc_project_closure")
@Data @EqualsAndHashCode(callSuper = true)
public class NormalClosureApplicationDO extends BaseBusinessEntity {
    private Long projectId;
    private Long snapshotId;
    private String closureType;
    private Integer ruleRevision;
    private String fromStage;
    private Long projectVersion;
    private Long treeVersion;
    private String status;
    private Long applicantUserId;
    private Long serviceManagerUserId;
    private Long reviewerUserId;
    private String processDefinitionKey;
    private String processDefinitionId;
    private String processInstanceId;
    private String businessKey;
    /** Immutable evidence returned by the BPM Owner, not an editable workflow definition. */
    private String processEvidence;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
}
