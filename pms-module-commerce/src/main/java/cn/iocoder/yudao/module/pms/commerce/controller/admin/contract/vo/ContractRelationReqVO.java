package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContractRelationReqVO(
        @NotNull Long projectId,
        @Size(max = 32) String relationRole,
        @NotBlank @Size(max = 500) String reason) {
}
