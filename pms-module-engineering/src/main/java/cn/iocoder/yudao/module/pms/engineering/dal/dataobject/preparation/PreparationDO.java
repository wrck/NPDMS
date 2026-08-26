package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("sol_preparation")
@Data
public class PreparationDO implements Serializable {
    @TableId private Long id;
    private Long projectId;
    private String preparationTypeCode;
    private Integer businessVersion;
    private Integer currentMarker;
    private Long templateId;
    private Long templateRevisionId;
    private String templateSnapshot;
    private Integer fixedFormCatalogVersion;
    private String statusCode;
    private String readinessStatusCode;
    private Long latestReadinessSnapshotId;
    private Integer inputVersion;
    private Integer readinessVersion;
    private Boolean snapshotCurrent;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime returnedAt;
    private String returnReason;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Long tenantId;
}
