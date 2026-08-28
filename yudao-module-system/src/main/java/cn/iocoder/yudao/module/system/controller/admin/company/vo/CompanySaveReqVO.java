package cn.iocoder.yudao.module.system.controller.admin.company.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompanySaveReqVO {

    private Long id;
    private Integer expectedVersion;
    @NotBlank private String code;
    @NotBlank private String name;
    @NotNull private Integer status;

}
