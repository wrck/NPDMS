package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 技术公告 Response VO（FR-ENG-009）。
 */
@Schema(description = "管理后台 - PMS 技术公告 Response VO")
@Data
public class AnnouncementRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "公告编号", example = "TA-2026-001")
    private String code;

    @Schema(description = "公告标题", example = "FW-2000 停产公告")
    private String title;

    @Schema(description = "公告类型", example = "EOS")
    private String announcementType;

    @Schema(description = "适用设备型号")
    private String productModel;

    @Schema(description = "影响版本范围JSON数组")
    private String affectedVersions;

    @Schema(description = "发布日期")
    private LocalDate publishDate;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "失效日期")
    private LocalDate expireDate;

    @Schema(description = "严重等级", example = "HIGH")
    private String severity;

    @Schema(description = "公告内容富文本")
    private String content;

    @Schema(description = "处置建议")
    private String handlingSuggestion;

    @Schema(description = "附件URL")
    private String fileUrl;

    @Schema(description = "附件名")
    private String fileName;

    @Schema(description = "附件大小（字节）", example = "102400")
    private Long fileSize;

    @Schema(description = "附件校验值")
    private String fileChecksum;

    @Schema(description = "状态：0 草稿 1 已发布 2 已停用", example = "1")
    private Integer status;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
