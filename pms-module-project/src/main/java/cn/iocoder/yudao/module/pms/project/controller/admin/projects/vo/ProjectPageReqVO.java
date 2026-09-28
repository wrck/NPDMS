package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 项目分页 Request VO（名称/编码/状态/三维/责任成员/归属/时间/设备条件过滤）
 *
 * 时间范围语义统一左闭右开：调用方传 [start, endExclusive)。
 */
@Schema(description = "管理后台 - 项目分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPageReqVO extends PageParam {

    @Schema(description = "项目名称（模糊）", example = "网络优化")
    private String projectName;

    @Schema(description = "项目编码前缀", example = "PJT2026")
    private String projectCode;

    @Schema(description = "项目状态", example = "S0")
    private String status;

    @Schema(description = "签约方式", example = "DIRECT")
    private String signingMethod;

    @Schema(description = "项目类别", example = "ENGINEERING")
    private String projectCategory;

    @Schema(description = "实施方式", example = "FACTORY_SERVICE")
    private String implementationMode;

    @Schema(description = "CRM重大项目级别", example = "OFFICE_MAJOR")
    private String majorProjectLevel;

    @Schema(description = "合同号（模糊）", example = "HT2026")
    private String contractNo;

    @Schema(description = "办事处部门编号（匹配下单办事处）", example = "1024")
    private Long departmentId;

    @Schema(description = "所属公司编号（匹配下单公司）", example = "1024")
    private Long companyId;

    @Schema(description = "项目经理/负责人用户编号（匹配项目有效经理成员）", example = "1024")
    private Long managerId;

    @Schema(description = "服务经理用户编号（匹配项目有效服务经理成员）", example = "1024")
    private Long serviceManagerId;

    @Schema(description = "销售人员用户编号（匹配项目有效销售代表成员）", example = "1024")
    private Long salesId;

    @Schema(description = "代理商/服务商名称或编码（模糊，匹配有效当事方）", example = "某某科技")
    private String agentServiceProviderKeyword;

    @Schema(description = "设备序列号（模糊，项目当前归属设备）")
    private String deviceSn;

    @Schema(description = "设备产品型号（模糊，项目当前归属设备）")
    private String deviceProductModel;

    @Schema(description = "设备维保状态（等值，CRM同步原文）")
    private String deviceWarrantyStatus;

    @Schema(description = "创建时间范围开始（>=）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime createTimeStart;

    @Schema(description = "创建时间范围结束（<）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime createTimeEnd;

    @Schema(description = "闭环时间范围开始（>=）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime closeTimeStart;

    @Schema(description = "闭环时间范围结束（<）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime closeTimeEnd;

    @Schema(description = "刷新时间范围开始（>=）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime refreshTimeStart;

    @Schema(description = "刷新时间范围结束（<）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime refreshTimeEnd;
}
