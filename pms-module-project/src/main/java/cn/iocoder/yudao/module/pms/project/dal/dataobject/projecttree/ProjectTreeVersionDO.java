package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("proj_project_tree_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTreeVersionDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long rootProjectId;
    private Long treeVersion;
    private String status;
    private String changeBatchId;
    private Integer nodeCount;
    private Integer pathCount;
    private LocalDateTime activatedAt;
    private String failedReason;
    private Integer version;
}
