package cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("acc_project_deliverable_source_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDeliverableSourceVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long deliverableId;
    private String sourceRequirementId;
    private String sourceObjectType;
    private Long sourceObjectId;
    private Integer sourceVersion;
    private String relationStatus;
    private String archiveStatus;
    private String archiveFailureCode;
    private Integer archiveRetryCount;
    private LocalDateTime archiveTime;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer currentMarker;
}
