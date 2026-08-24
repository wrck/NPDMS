package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("proj_project_tree_path")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTreePathDO extends TenantBaseDO {
    @TableId private Long id;
    private Long treeVersion;
    private Long rootProjectId;
    private Long ancestorProjectId;
    private Long descendantProjectId;
    private Integer distance;
    private Integer version;
}
