package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ConstructionPlanCreateReqVO {

    @NotNull
    @Positive
    private Long projectId;
    @NotBlank
    private String calculationBasis;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationDays;
    @NotNull
    @Min(0)
    private Integer expectedProjectVersion;
}
