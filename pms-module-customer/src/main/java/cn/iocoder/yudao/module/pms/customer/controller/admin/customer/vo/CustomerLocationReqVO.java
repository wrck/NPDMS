package cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerLocationReqVO {

    @NotBlank
    private String locationType;

    @NotNull
    private Long locationId;

    @NotNull
    @Min(0)
    private Integer sourceVersion;
}
