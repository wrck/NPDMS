package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("proj_project_tree_change")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTreeChangeDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String changeBatchId;
    private String operationType;
    private Long projectId;
    private Long parentIdBefore;
    private Long parentIdAfter;
    private Long baseTreeVersion;
    private Long newTreeVersion;
    private Long actorId;
    private String reason;
    private LocalDateTime occurredAt;
    private Integer version;
}
