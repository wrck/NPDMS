package cn.iocoder.yudao.module.pms.commerce.controller.admin.scope.vo;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record DeliveryScopeAssignReqVO(
        @NotNull @Positive Long projectId,
        @NotNull @PositiveOrZero Long expectedProjectScopeVersion,
        @NotNull @PositiveOrZero Long expectedDeliveryScopeVersion,
        @NotNull @Positive Long orderLineId,
        @NotBlank @Size(max = 128) String expectedOrderLineSourceVersion,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal allocatedQuantity,
        List<@NotBlank @Size(max = 128) String> serialNumbers,
        @NotBlank @Size(max = 500) String reason) {
}
