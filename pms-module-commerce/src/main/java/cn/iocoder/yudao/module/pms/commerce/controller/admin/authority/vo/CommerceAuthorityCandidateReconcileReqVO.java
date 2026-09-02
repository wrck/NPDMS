package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommerceAuthorityCandidateReconcileReqVO(
        Long ownerId,
        @NotBlank @Size(max = 16) String decision,
        @NotBlank @Size(max = 512) String decisionReason) {
}
