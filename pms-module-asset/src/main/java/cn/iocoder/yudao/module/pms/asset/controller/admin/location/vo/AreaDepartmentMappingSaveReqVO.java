package cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AreaDepartmentMappingSaveReqVO {

    private Long id;
    private Integer expectedVersion;
    @NotBlank private String areaCode;
    @NotBlank private String areaLevel;
    @NotBlank private String departmentCode;
    @NotNull private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @NotNull private Integer status;

}
