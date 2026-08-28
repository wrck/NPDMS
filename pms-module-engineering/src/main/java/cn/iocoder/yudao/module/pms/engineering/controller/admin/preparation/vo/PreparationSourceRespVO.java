package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PreparationSourceRespVO {
    private Long sourceReferenceId;
    private String sourceTypeCode;
    private String sourceObjectType;
    private String sourceObjectId;
    private String sourceReferenceKey;
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
    private Integer sourceVersion;
}
