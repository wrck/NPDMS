package cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerLifecycleReqVO {

    @NotBlank
    private String reason;
}
