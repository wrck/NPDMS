package cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("acc_project_closure")
@Data @EqualsAndHashCode(callSuper = true)
public class NormalClosureApplicationDO extends TenantBaseDO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long projectId;
    private Long snapshotId;
    private String closureType;
    private Integer ruleRevision;
    private String fromStage;
    private Integer projectVersion;
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
    private Integer version;
}
