package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - 现场工勘 Response VO（FR-ENG-001）。
 */
@Schema(description = "管理后台 - 现场工勘 Response VO")
@Data
public class SiteSurveyRespVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "工勘编码", example = "SUR-2026-001")
    private String code;

    @Schema(description = "工勘名称", example = "核心机房工勘")
    private String name;

    @Schema(description = "工勘日期")
    private LocalDate surveyDate;

    @Schema(description = "工勘责任人", example = "1")
    private Long surveyorUserId;

    @Schema(description = "工勘地点")
    private String location;

    private Long addressId;
    private Integer addressVersion;
    private Long siteId;
    private Integer siteVersion;
    private Long siteLocationId;
    private Integer siteLocationVersion;
    private String locationResolutionStatus;
    private String addressSnapshot;
    private String locationSnapshot;

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

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
