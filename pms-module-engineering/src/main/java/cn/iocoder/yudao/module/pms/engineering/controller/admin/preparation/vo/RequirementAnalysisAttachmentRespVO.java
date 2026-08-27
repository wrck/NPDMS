package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import lombok.Data;

@Data
public class RequirementAnalysisAttachmentRespVO {
    private Long artifactId;
    private Integer versionNo;
    private String referenceKey;
    private String name;
    private Long sizeBytes;
    private String mediaType;
    private String availabilityStatus;
    private String referenceStatus;
    private FileFactVersion fileFactVersion;
    private Long scopeVersion;
}
