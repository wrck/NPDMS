package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class DurationChangeSubmitReqVO {

    @NotNull
    @Positive
    private Long changeId;

    @NotNull
    @PositiveOrZero
    private Integer expectedProjectVersion;

}
