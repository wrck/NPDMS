package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** PM-03 / SDS09 explicit edge; generated default_marker remains database-owned. */
@Data @EqualsAndHashCode(callSuper = true)
@TableName("proj_stage_transition_definition")
public class ProjectTemplateTransitionDefinitionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long templateRevisionId;
    private String transitionCode;
    private String fromStageCode;
    private String toStageCode;
    private Long conditionRuleRevisionId;
    private Integer priority;
    @TableField("is_default") private Boolean defaultBranch;
    private Long revisionNo;
}
