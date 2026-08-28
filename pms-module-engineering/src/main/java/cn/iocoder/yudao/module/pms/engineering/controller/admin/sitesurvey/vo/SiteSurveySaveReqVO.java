package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 现场工勘新增/修改 Request VO（FR-ENG-001）。
 */
@Schema(description = "管理后台 - 现场工勘新增/修改 Request VO")
@Data
public class SiteSurveySaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "工勘编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SUR-2026-001")
    @NotBlank(message = "工勘编码不能为空")
    private String code;

    @Schema(description = "工勘名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心机房工勘")
    @NotBlank(message = "工勘名称不能为空")
    private String name;

    @Schema(description = "工勘日期", example = "2026-07-01")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDate surveyDate;

    @Schema(description = "工勘责任人", example = "1")
    private Long surveyorUserId;

    @Schema(description = "工勘地点", example = "北京核心机房")
    private String location;

    @Schema(description = "地点维护命令；提供时创建或修订结构化地址、站点和站点位置")
    private LocationMaintenanceCommand locationMaintenance;

    @Schema(description = "供电条件")
    private String powerSupply;

    @Schema(description = "机柜条件")
    private String cabinet;

    @Schema(description = "网口条件")
    private String networkPort;

    @Schema(description = "光纤条件")
    private String fiber;

    @Schema(description = "模块条件")
    private String module;

    @Schema(description = "线缆条件")
    private String cable;

    @Schema(description = "接地条件")
    private String ground;

    @Schema(description = "施工资源条件")
    private String constructionResource;

    @Schema(description = "工勘结论")
    private String conclusion;

    @Schema(description = "状态：0 草稿 1 已确认 2 已驳回 3 已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
