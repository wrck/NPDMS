package cn.iocoder.yudao.module.pms.project.controller.admin.projectstage.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProjectAcceptanceStageEntryReqVO(@NotNull @Positive Long expectedTreeVersion) {
}
