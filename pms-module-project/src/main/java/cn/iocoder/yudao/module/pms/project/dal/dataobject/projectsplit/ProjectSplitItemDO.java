package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("proj_project_split_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectSplitItemDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long splitRequestId;
    private String clientItemKey;
    private String projectName;
    private String businessLevelCode;
    private Integer treeSort;
    private String officeDepartmentCode;
    private String itemStatus;
    private String validationResult;
    private Long createdProjectId;
    private Integer version;
}
