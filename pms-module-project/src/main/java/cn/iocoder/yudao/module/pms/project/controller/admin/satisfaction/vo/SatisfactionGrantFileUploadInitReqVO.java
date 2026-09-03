package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SatisfactionGrantFileUploadInitReqVO {
    @NotBlank private String requestId;
    @NotBlank private String policyKey;
    @NotBlank private String operationId;
    @NotBlank private String fileName;
    @NotBlank private String categoryCode;
    @NotNull private Long declaredSizeBytes;
    @NotBlank private String declaredMediaType;
    private String clientSha256;
}
