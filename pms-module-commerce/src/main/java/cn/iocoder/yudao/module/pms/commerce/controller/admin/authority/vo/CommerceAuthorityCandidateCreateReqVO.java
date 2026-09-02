package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record CommerceAuthorityCandidateCreateReqVO(
        @NotBlank @Size(max = 32) String objectType,
        @NotBlank @Size(max = 128) String sourceKey,
        @NotBlank @Size(max = 64) String candidateVersion,
        @NotNull JsonNode candidatePayload,
        @NotNull JsonNode evidenceReference) {
}
