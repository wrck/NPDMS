package cn.iocoder.yudao.module.system.controller.admin.permission.vo.scope;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserOrganizationScopePageReqVO extends PageParam {
    @NotNull @Positive
    private Long userId;
    @Min(0) @Max(1)
    private Integer status;
}
