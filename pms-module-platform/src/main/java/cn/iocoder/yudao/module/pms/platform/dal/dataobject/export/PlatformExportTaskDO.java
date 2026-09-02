package cn.iocoder.yudao.module.pms.platform.dal.dataobject.export;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("plt_export_task")
@Data
public class PlatformExportTaskDO implements Serializable {

    @TableId
    private Long id;
    private Long tenantId;
    private String ownerContext;
    private String exportType;
    private String operationId;
    private String requestDigest;
    private Long actorUserId;
    private String filterSnapshot;
    private String scopeSnapshot;
    private String requestedFieldsSnapshot;
    private Boolean includeFiles;
    private Long scopeVersion;
    private String taskStatus;
    private Long resultCount;
    private Long artifactId;
    private Integer fileVersionNo;
    private String referenceKey;
    private Integer artifactVersion;
    private Integer referenceVersion;
    private Integer availabilityVersion;
    private String fileHash;
    private LocalDateTime expiresAt;
    private String failureCode;
    private Boolean failureRetryable;
    private Integer retryCount;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
