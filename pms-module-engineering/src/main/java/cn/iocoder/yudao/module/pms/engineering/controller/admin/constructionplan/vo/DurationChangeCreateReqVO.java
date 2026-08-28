package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DurationChangeCreateReqVO {
    @NotNull @Min(0) private Integer expectedProjectVersion;
    @NotBlank @Size(max = 32) private String calculationBasis;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationDays;
    @NotBlank @Size(max = 64) private String reasonType;
    @Size(max = 1000) private String reasonDetail;
    @Positive private Long customerEvidenceFileId;
    @Positive private Integer customerEvidenceFileVersion;
    @Size(max = 128) private String customerEvidenceReferenceKey;
}
