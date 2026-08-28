package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("sol_preparation_source_reference")
@Data
public class PreparationSourceReferenceDO implements Serializable {
    @TableId private Long id;
    private Long preparationId;
    private Long itemId;
    private String sourceTypeCode;
    private String sourceObjectType;
    private String sourceObjectId;
    private String sourceReferenceKey;
    private String requiredResultPolicySnapshot;
    private String normalizedResultCode;
    private String sourceFactVersion;
    private String sourceWatermark;
    private String syncStatusCode;
    private String lastSuccessResultCode;
    private String lastSuccessFactVersion;
    private String lastSuccessWatermark;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastSyncedAt;
    private String lastSyncErrorCode;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Long tenantId;
}
