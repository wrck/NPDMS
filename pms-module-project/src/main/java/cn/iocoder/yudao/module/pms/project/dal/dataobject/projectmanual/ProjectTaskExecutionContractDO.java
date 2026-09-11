package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** ProjectTask immutable execution contract. Legacy definition ids are optional source evidence in V2. */
@TableName("proj_project_task_execution_contract")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskExecutionContractDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectTaskId;
    /** Stable V2 node identity from TemplateExecutionSnapshot. */
    private String sourceNodeKey;
    private Long templateTaskDefinitionId;
    private Long definitionRevisionId;
    private Long workBindingRevisionId;
    private Long permissionPolicyRevisionId;
    private Long completionRuleRevisionId;
    /** Legacy closure only; V2 runtime does not resolve it. */
    private String definitionSnapshot;
    private String workBindingTypeCode;
    private String targetContextCode;
    private String targetObjectType;
    private String targetObjectKey;
    private String componentKey;
    private Long dynamicFormRevisionId;
    private Long approvalInstanceId;
    private String bindingParameterSnapshot;
    /** Frozen BusinessView registration when the binding uses one. */
    private String bindingViewSnapshot;
    private String permissionPolicyRef;
    /** Frozen permission requirement, not a grant. */
    private String permissionSnapshot;
    private String completionRuleTypeCode;
    private String completionRuleSnapshot;
    private String gateRef;
    private Integer sourceDefinitionVersion;
    private Integer contractVersion;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer currentMarker;
    private Integer version;
}
