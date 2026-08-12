package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 硬件安装分页 Request VO（FR-ENG-022）。
 */
@Schema(description = "管理后台 - 硬件安装分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class InstallationPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "安装编码", example = "INS-2026-001")
    private String code;

    @Schema(description = "状态：0 待安装 1 进行中 2 已完成 3 异常", example = "0")
    private Integer status;

    @Schema(description = "设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "安装人", example = "1")
    private Long installerUserId;
}
