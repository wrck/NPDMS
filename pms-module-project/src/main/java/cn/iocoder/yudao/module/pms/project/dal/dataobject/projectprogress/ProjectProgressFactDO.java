package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("proj_project_progress_fact")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectProgressFactDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private String factSourceType;
    private String factSourceId;
    private Long factVersion;
    private BigDecimal progress;
    private String sourceWatermark;
    private LocalDateTime occurredAt;
    private Integer version;
}
