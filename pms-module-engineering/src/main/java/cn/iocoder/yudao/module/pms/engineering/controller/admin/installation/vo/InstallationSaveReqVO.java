package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 硬件安装新增/修改 Request VO（FR-ENG-022）。
 */
@Schema(description = "管理后台 - 硬件安装新增/修改 Request VO")
@Data
public class InstallationSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "安装编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "INS-2026-001")
    @NotBlank(message = "安装编码不能为空")
    private String code;

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "设备编号不能为空")
    private Long equipmentId;

    @Schema(description = "安装位置", example = "机房A-机柜01")
    private String installLocation;

    @Schema(description = "安装时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime installTime;

    @Schema(description = "安装人", example = "1")
    private Long installerUserId;

    @Schema(description = "环境检查")
    private String environmentCheck;

    @Schema(description = "安装规范检查")
    private String specCheck;

    @Schema(description = "安装照片")
    private String photoUrl;

    @Schema(description = "安装结果")
    private String result;

    @Schema(description = "状态：0 待安装 1 进行中 2 已完成 3 异常", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
