package cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** PM-03 immutable project transition, copied only at project creation. */
@Data @EqualsAndHashCode(callSuper = true)
@TableName("proj_project_stage_transition")
public class ProjectStageTransitionDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long templateRevisionId;
    private Long sourceTransitionId;
    private String transitionCode;
    private Long transitionRevision;
    private Long fromStageId;
    private Long toStageId;
    private Integer priority;
    private Boolean isDefault;
    private Long conditionRuleRevisionId;
    private String conditionSnapshot;
    private Long graphVersion;
}
