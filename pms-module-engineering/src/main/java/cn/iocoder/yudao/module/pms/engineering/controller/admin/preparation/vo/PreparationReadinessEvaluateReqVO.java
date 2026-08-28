package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreparationReadinessEvaluateReqVO {
    @NotNull @Min(0) private Integer expectedProjectVersion;
}
