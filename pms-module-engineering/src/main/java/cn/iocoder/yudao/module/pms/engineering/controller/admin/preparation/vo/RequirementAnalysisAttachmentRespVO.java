package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import lombok.Data;

@Data
@Deprecated // 固定章节附件快照已由PLT动态表单文件引用替代。
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
