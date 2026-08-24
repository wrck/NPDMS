package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@TableName("proj_project_progress_snapshot_detail")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectProgressSnapshotDetailDO extends TenantBaseDO {
    @TableId private Long id;
    private Long snapshotId;
    private Long childProjectId;
    private Long factVersion;
    private BigDecimal childProgress;
    private BigDecimal normalizedWeight;
    private BigDecimal contribution;
    private String missingReason;
    private Integer version;
}
