package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 配置调试新增/修改 Request VO（FR-ENG-023）。
 */
@Schema(description = "管理后台 - 配置调试新增/修改 Request VO")
@Data
public class ConfigurationSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "配置编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "CFG-2026-001")
    @NotBlank(message = "配置编码不能为空")
    private String code;

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "设备编号不能为空")
    private Long equipmentId;

    @Schema(description = "配置 Log 文件")
    private String configLogUrl;

    @Schema(description = "调试结果")
    private String debugResult;

    @Schema(description = "调试人", example = "1")
    private Long debuggerUserId;

    @Schema(description = "调试时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime debugTime;

    @Schema(description = "配置档案快照")
    private String configSnapshot;

    @Schema(description = "状态：0 待调试 1 进行中 2 已完成 3 异常", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
