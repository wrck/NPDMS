package cn.iocoder.yudao.module.pms.commerce.controller.admin.scope.vo;

import jakarta.validation.constraints.*;

public record DeliveryScopeReleaseReqVO(
        @NotNull @Positive Long projectId,
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        @NotNull @PositiveOrZero Long expectedProjectScopeVersion,
        @NotNull @PositiveOrZero Long expectedDeliveryScopeVersion,
        @NotBlank @Size(max = 128) String expectedOrderLineSourceVersion,
        @NotBlank @Size(max = 500) String reason) {
}
