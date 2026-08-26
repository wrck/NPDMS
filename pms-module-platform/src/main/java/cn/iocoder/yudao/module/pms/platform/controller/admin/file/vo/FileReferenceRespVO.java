package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileReferenceRespVO {

    private Long referenceId;
    private String ownerContext;
    private String objectType;
    private String objectId;
    private String purposeCode;
    private String referenceKey;
    private Long artifactId;
    private Integer versionNo;
    private String sensitivityCode;
    private String status;
    private Long scopeVersion;
    private Integer referenceVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
