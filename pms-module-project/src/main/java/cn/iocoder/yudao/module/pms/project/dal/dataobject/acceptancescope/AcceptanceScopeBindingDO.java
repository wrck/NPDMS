package cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancescope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** ACC拥有的项目验收阶段范围锁定事实，独立于初验/终验报告。 */
@TableName("acc_acceptance_scope_binding")
@Data
@EqualsAndHashCode(callSuper = true)
public class AcceptanceScopeBindingDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long projectStageSnapshotId;
    private Long deliveryScopeId;
    private Long scopeAllocationVersion;
    private String bindingTrigger;
    private String bindingStatus;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer acceptanceFactVersion;
    @Version
    private Integer version;
}
