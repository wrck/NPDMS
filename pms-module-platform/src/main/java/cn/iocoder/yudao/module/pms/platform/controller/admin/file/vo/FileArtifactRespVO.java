package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FileArtifactRespVO {

    private Long artifactId;
    private String name;
    private String categoryCode;
    private String ownerContext;
    private String lifecycleStatus;
    private Integer artifactVersion;
    private FileReferenceRespVO reference;
    private List<String> allowedActions;
    private LocalDateTime createdAt;
}
