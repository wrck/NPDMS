package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@TableName("proj_project_split_scope")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectSplitScopeDO extends TenantBaseDO {
    @TableId private Long id;
    private Long splitItemId;
    private Long orderLineId;
    private BigDecimal allocatedQty;
    private String officeDepartmentCode;
    private String serialNo;
    private Long sourceScopeVersion;
    private String sourceSnapshot;
    private Integer version;
}
