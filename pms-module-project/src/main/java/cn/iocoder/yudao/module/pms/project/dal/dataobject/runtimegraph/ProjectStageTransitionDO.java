package cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** PM-03 immutable project transition copied from TemplateExecutionSnapshot at project creation. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proj_project_stage_transition")
public class ProjectStageTransitionDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long templateRevisionId;
    /** Stable V2 compiled edge identity. */
    private String sourceTransitionKey;
    /** Legacy transition row id; optional in V2. */
    private Long sourceTransitionId;
    private String transitionCode;
    /** Legacy source revision; optional in V2. */
    private Long transitionRevision;
    private Long fromStageId;
    private Long toStageId;
    private Integer priority;
    private Boolean isDefault;
    /** Legacy rule revision evidence only. */
    private Long conditionRuleRevisionId;
    /** Canonical frozen rule AST used by V2 runtime. */
    private String conditionSnapshot;
    private Long graphVersion;
}
