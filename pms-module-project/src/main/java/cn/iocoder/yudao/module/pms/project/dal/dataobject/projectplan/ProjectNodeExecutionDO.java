package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proj_project_node_execution")
public class ProjectNodeExecutionDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long planVersionId;
    private String nodeKey;
    private String nodeKind;
    private Long nodeInstanceId;
    private Long contractId;
    private Integer roundNo;
    private Integer currentMarker;
    private String status;
    private LocalDateTime admittedAt;
    private LocalDateTime startedAt;
    private Long startedPlanVersionId;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String submissionNote;
    private LocalDateTime endedAt;
    private String resultSnapshot;
    private Integer version;
}
