package cn.iocoder.yudao.module.system.controller.admin.permission.vo.scope;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserOrganizationScopeDisableReqVO {
    @NotNull @Positive
    private Long userId;
    @NotNull @PositiveOrZero
    private Integer expectedVersion;
}
