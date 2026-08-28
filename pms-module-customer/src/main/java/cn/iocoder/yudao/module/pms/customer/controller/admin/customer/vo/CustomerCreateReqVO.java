package cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerCreateReqVO {

    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String shortName;
    private String remark;
    @NotNull
    private CustomerSourceType sourceType;
    private String sourceKey;
    private String sourceVersion;
    private String temporaryReason;
    private boolean reconciliationPending;
    @NotBlank
    private String departmentCode;
    @NotBlank
    private String marketCode;
    @NotBlank
    private String systemCode;
    @NotBlank
    private String expendCode;
    @NotBlank
    private String industryCode;
}
