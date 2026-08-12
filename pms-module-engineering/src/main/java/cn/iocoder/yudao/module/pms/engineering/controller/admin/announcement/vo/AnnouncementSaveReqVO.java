package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 管理后台 - PMS 技术公告新增/修改 Request VO（FR-ENG-009）。
 */
@Schema(description = "管理后台 - PMS 技术公告新增/修改 Request VO")
@Data
public class AnnouncementSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "公告编号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "TA-2026-001")
    @NotBlank(message = "公告编号不能为空")
    @Size(max = 64, message = "公告编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "公告标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "FW-2000 停产公告")
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题长度不能超过 200 个字符")
    private String title;

    @Schema(description = "公告类型：TECH_NOTICE/EOS/EOM", example = "EOS")
    @Size(max = 32, message = "公告类型长度不能超过 32 个字符")
    private String announcementType;

    @Schema(description = "适用设备型号")
    @Size(max = 128, message = "设备型号长度不能超过 128 个字符")
    private String productModel;

    @Schema(description = "影响版本范围JSON数组")
    private String affectedVersions;

    @Schema(description = "发布日期", example = "2026-01-01")
    private LocalDate publishDate;

    @Schema(description = "生效日期", example = "2026-01-15")
    private LocalDate effectiveDate;

    @Schema(description = "失效日期", example = "2026-12-31")
    private LocalDate expireDate;

    @Schema(description = "严重等级：CRITICAL/HIGH/MEDIUM/LOW", example = "HIGH")
    @Size(max = 16, message = "严重等级长度不能超过 16 个字符")
    private String severity;

    @Schema(description = "公告内容富文本")
    private String content;

    @Schema(description = "处置建议")
    private String handlingSuggestion;

    @Schema(description = "附件URL")
    @Size(max = 512, message = "附件URL长度不能超过 512 个字符")
    private String fileUrl;

    @Schema(description = "附件名")
    @Size(max = 200, message = "附件名长度不能超过 200 个字符")
    private String fileName;

    @Schema(description = "附件大小（字节）", example = "102400")
    private Long fileSize;

    @Schema(description = "附件校验值")
    @Size(max = 64, message = "附件校验值长度不能超过 64 个字符")
    private String fileChecksum;

    @Schema(description = "创建人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
