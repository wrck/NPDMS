package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import cn.iocoder.yudao.module.pms.asset.service.version.SoftwareVersionValues;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record SoftwareVersionRegistrationReqVO(
        @NotNull @Positive Long projectId,
        @NotNull @Min(0) Integer expectedVersion,
        @NotBlank @Size(max=180) String requestKey,
        @NotNull @Valid SoftwareVersionValues values) {}
