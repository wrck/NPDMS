package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PreparationPageReqVO {
    private String cursor;
    @Min(1)
    @Max(100)
    private Integer pageSize;
}
