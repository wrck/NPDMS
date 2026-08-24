package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("proj_project_progress_snapshot")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectProgressSnapshotDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private Long policyRevisionId;
    private Long treeVersion;
    private String sourceWatermark;
    private String snapshotStatus;
    private BigDecimal progress;
    private Integer missingItemCount;
    private LocalDateTime calculatedAt;
    private Integer version;
}
