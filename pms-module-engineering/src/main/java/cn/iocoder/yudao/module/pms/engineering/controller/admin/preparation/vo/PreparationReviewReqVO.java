package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PreparationReviewReqVO {
    private Integer expectedPreparationVersion;
    @NotNull private Integer expectedProjectVersion;
    @Size(max = 2000) private String reason;
}
