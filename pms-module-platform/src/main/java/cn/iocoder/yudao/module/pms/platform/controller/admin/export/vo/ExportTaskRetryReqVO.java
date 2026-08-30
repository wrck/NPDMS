package cn.iocoder.yudao.module.pms.platform.controller.admin.export.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExportTaskRetryReqVO(@NotNull @Min(0) Integer expectedVersion) {
}
