package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 公告预检查 Response VO（FR-ENG-009）。
 */
@Schema(description = "管理后台 - PMS 公告预检查 Response VO")
@Data
public class AnnouncementCheckRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "检查编号", example = "PCH-2026-001")
    private String code;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "关联技术公告ID", example = "1024")
    private Long announcementId;

    @Schema(description = "关联设备ID", example = "1024")
    private Long deviceId;

    @Schema(description = "设备序列号")
    private String deviceSerial;

    @Schema(description = "设备型号")
    private String deviceModel;

    @Schema(description = "设备版本")
    private String deviceVersion;

    @Schema(description = "匹配结果", example = "HIT")
    private String matchResult;

    @Schema(description = "EOS/EOM状态", example = "EOS")
    private String eomStatus;

    @Schema(description = "处置建议")
    private String handlingSuggestion;

    @Schema(description = "状态：0 待检查 1 已检查 2 已处置 3 已忽略", example = "1")
    private Integer status;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "检查人", example = "1024")
    private Long checkerUserId;

    @Schema(description = "检查时间")
    private LocalDateTime checkTime;

    @Schema(description = "处理意见")
    private String handleOpinion;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Schema(description = "创建人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
