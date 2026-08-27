package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class RequirementAnalysisAttachmentReqVO {
    @NotNull @Positive private Long artifactId;
    @NotNull @Positive private Integer versionNo;
    @NotBlank private String referenceKey;
    @NotNull @Valid private FileFactVersion fileFactVersion;
    @NotNull @PositiveOrZero private Long scopeVersion;
}
