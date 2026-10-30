package cn.iocoder.yudao.module.system.controller.admin.permission.vo.scope;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 只接收组织主键；编码、名称、租户及状态由服务端取得。 */
@Data
public class UserOrganizationScopeSaveReqVO {
    @NotNull @Positive
    private Long userId;
    @NotNull @Positive
    private Long companyId;
    @Positive
    private Long departmentId;
    @NotBlank @Size(max = 32)
    private String scopeRole;
    @NotNull
    private Boolean isPrimary;
    @NotNull
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @PositiveOrZero
    private Integer expectedVersion;
}
