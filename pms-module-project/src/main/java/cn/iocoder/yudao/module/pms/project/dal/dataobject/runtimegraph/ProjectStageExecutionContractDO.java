package cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** PM-03: exact stage execution contract and immutable definition closure. */
@Data @EqualsAndHashCode(callSuper = true)
@TableName("proj_project_stage_execution_contract")
public class ProjectStageExecutionContractDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long stageId;
    private Long graphVersion;
    private Long definitionRevisionId;
    private Long workBindingRevisionId;
    private Integer bindingVersion;
    private String bindingType;
    private String bindingSnapshot;
    private Long permissionPolicyRevisionId;
    private Long completionRuleRevisionId;
    private String definitionSnapshot;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer version;
}
