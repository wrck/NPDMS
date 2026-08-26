package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FileUploadInitReqVO {

    @NotBlank @Size(max = 32)
    private String modeCode;
    @Positive
    private Long artifactId;
    @PositiveOrZero
    private Integer expectedReferenceVersion;
    @NotBlank @Size(max = 32)
    private String ownerContext;
    @NotBlank @Size(max = 64)
    private String objectType;
    @NotBlank @Size(max = 128)
    private String objectId;
    @NotBlank @Size(max = 64)
    private String purposeCode;
    @NotBlank @Size(max = 128)
    private String referenceKey;
    @NotBlank @Size(max = 256)
    private String fileName;
    @NotBlank @Size(max = 64)
    private String categoryCode;
    @NotNull @Positive
    private Long declaredSizeBytes;
    @NotBlank @Size(max = 128)
    private String declaredMediaType;
    @Size(max = 64)
    private String clientSha256;
}
