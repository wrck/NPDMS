package cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** PM-03 immutable stage execution contract. Legacy revision ids are optional source evidence. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proj_project_stage_execution_contract")
public class ProjectStageExecutionContractDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long stageId;
    /** Stable V2 node identity from TemplateExecutionSnapshot. */
    private String sourceNodeKey;
    private Long graphVersion;
    private Long definitionRevisionId;
    private Long workBindingRevisionId;
    private Integer bindingVersion;
    private String bindingType;
    private String bindingSnapshot;
    private String permissionSnapshot;
    private Long permissionPolicyRevisionId;
    private String completionRuleSnapshot;
    private Long completionRuleRevisionId;
    /** Legacy publication closure only; V2 runtime does not resolve it. */
    private String definitionSnapshot;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer version;
}
