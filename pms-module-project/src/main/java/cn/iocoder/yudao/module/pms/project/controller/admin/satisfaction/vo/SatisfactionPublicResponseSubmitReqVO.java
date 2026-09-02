package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SatisfactionPublicResponseSubmitReqVO {
    @NotBlank private String requestId;
    @NotNull private Long responseId;
    @NotBlank private String customerContactRef;
    @NotBlank private String answerSnapshot;
    @Valid @NotEmpty private List<FileFact> files;

    @Data
    public static class FileFact {
        @NotBlank private String role;
        @NotBlank private String fileSlotKey;
        @NotNull private Integer sequence;
        @NotNull private Long artifactId;
        @NotNull private Integer versionNo;
        @NotBlank private String referenceKey;
        @NotNull private Integer artifactVersion;
        @NotNull private Integer referenceVersion;
        @NotNull private Integer availabilityVersion;
        @NotNull private Long scopeVersion;
        @NotBlank private String sha256;
    }
}
