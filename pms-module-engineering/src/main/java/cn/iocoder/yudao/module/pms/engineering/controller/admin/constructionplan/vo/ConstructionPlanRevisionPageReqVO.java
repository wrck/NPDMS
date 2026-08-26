package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConstructionPlanRevisionPageReqVO {
    @Size(max = 128)
    private String cursor;
    @Min(1)
    @Max(100)
    private Integer pageSize = 20;
}
