package cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 授权管理分页 Request VO（FR-ENG-010）。
 */
@Schema(description = "管理后台 - PMS 授权管理分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorizationPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "授权编号，模糊匹配", example = "AUTH-2026")
    private String code;

    @Schema(description = "授权名称，模糊匹配", example = "临时授权")
    private String name;

    @Schema(description = "授权类型：FORMAL/TEMPORARY/LOAN", example = "TEMPORARY")
    private String authorizationType;

    @Schema(description = "状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止", example = "0")
    private Integer status;

    @Schema(description = "设备ID", example = "1024")
    private Long deviceId;

    @Schema(description = "设备序列号，模糊匹配", example = "SN001")
    private String deviceSerial;

    @Schema(description = "提交人", example = "1024")
    private Long submitUserId;

    @Schema(description = "审批人", example = "1024")
    private Long approverUserId;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
