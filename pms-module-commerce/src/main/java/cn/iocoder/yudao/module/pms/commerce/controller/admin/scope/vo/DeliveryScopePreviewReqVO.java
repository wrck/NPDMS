package cn.iocoder.yudao.module.pms.commerce.controller.admin.scope.vo;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record DeliveryScopePreviewReqVO(
        @NotNull @Positive Long projectId,
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        @NotNull @PositiveOrZero Long expectedProjectScopeVersion,
        @NotNull @PositiveOrZero Long expectedDeliveryScopeVersion,
        @NotNull @Positive Long orderLineId,
        @NotBlank @Size(max = 128) String expectedOrderLineSourceVersion,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal proposedQuantity,
        List<@NotBlank @Size(max = 128) String> serialNumbers) {
}
