package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 公告预检查分页 Request VO（FR-ENG-009）。
 */
@Schema(description = "管理后台 - PMS 公告预检查分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementCheckPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "关联技术公告ID", example = "1024")
    private Long announcementId;

    @Schema(description = "检查编号，模糊匹配", example = "PCH-2026")
    private String code;

    @Schema(description = "设备序列号，模糊匹配", example = "SN001")
    private String deviceSerial;

    @Schema(description = "设备型号，模糊匹配", example = "FW-2000")
    private String deviceModel;

    @Schema(description = "匹配结果：HIT/MISS/UNKNOWN", example = "HIT")
    private String matchResult;

    @Schema(description = "EOS/EOM状态：EOS/EOM/NONE", example = "EOS")
    private String eomStatus;

    @Schema(description = "状态：0 待检查 1 已检查 2 已处置 3 已忽略", example = "1")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
