package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SatisfactionGrantFileUploadCompleteReqVO {
    @NotBlank private String requestId;
    @NotNull private Long responseId;
    @NotBlank private String policyKey;
    @NotBlank private String operationId;
    @NotBlank private String fileSlotKey;
    @NotNull private Integer fileSequence;
    @NotNull private Long artifactId;
    private String clientSha256;
}
