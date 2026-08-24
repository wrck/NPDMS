package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@TableName("proj_project_progress_policy_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectProgressPolicyItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long policyRevisionId;
    private Long childProjectId;
    private BigDecimal weight;
    private String includeStatusSnapshot;
    private Integer version;
}
