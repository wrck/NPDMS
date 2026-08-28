package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理后台 - 设备归属 Response VO")
public record DeviceAssignmentRespVO(
        @Schema(description = "更新后的归属版本") Long assignmentVersion,
        @Schema(description = "祖先投影操作ID") String operationId) {
}
