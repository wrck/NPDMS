package cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SiteLocationDisableReqVO {

    @NotNull
    private Long id;
    @NotNull
    private Integer version;

}
