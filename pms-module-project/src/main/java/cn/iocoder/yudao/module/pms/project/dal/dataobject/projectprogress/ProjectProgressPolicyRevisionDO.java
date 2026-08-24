package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("proj_project_progress_policy_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectProgressPolicyRevisionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long parentProjectId;
    private Integer revisionNo;
    private String status;
    private String policyType;
    private String processDefinitionKey;
    private String processInstanceId;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long supersedesRevisionId;
    private Integer version;
}
