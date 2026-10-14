package cn.iocoder.yudao.module.pms.cutover.controller.business.closure.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CutoverCollectionExecutionLinkReqVO(@NotNull @Positive Long executionId, @NotBlank String entry) { }
