package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileVersionRespVO {

    private Long id;
    private Integer versionNo;
    private String sha256;
    private Long sizeBytes;
    private String mediaType;
    private String scanStatus;
    private String availabilityStatus;
    private Integer availabilityVersion;
    private String unavailableReasonCode;
    private String versionNote;
    private Long createdBy;
    private LocalDateTime createdAt;
}
