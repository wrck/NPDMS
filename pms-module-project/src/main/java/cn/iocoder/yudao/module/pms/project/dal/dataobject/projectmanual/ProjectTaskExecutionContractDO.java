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

/**
 * ProjectTask执行契约；每个任务至多一个effectiveTo为空的当前版本。
 */
@TableName("proj_project_task_execution_contract")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskExecutionContractDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectTaskId;
    private Long templateTaskDefinitionId;
    private String workBindingTypeCode;
    private String targetContextCode;
    private String targetObjectType;
    private String targetObjectKey;
    private String componentKey;
    private Long dynamicFormRevisionId;
    private Long approvalInstanceId;
    private String bindingParameterSnapshot;
    private String permissionPolicyRef;
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
