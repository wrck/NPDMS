package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 公告预检查新增/修改 Request VO（FR-ENG-009）。
 */
@Schema(description = "管理后台 - PMS 公告预检查新增/修改 Request VO")
@Data
public class AnnouncementCheckSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "检查编号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "PCH-2026-001")
    @NotBlank(message = "检查编号不能为空")
    @Size(max = 64, message = "检查编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "关联技术公告ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "关联技术公告不能为空")
    private Long announcementId;

    @Schema(description = "关联设备ID", example = "1024")
    private Long deviceId;

    @Schema(description = "设备序列号")
    @Size(max = 64, message = "设备序列号长度不能超过 64 个字符")
    private String deviceSerial;

    @Schema(description = "设备型号")
    @Size(max = 128, message = "设备型号长度不能超过 128 个字符")
    private String deviceModel;

    @Schema(description = "设备版本")
    @Size(max = 64, message = "设备版本长度不能超过 64 个字符")
    private String deviceVersion;

    @Schema(description = "匹配结果：HIT/MISS/UNKNOWN", example = "HIT")
    @Size(max = 16, message = "匹配结果长度不能超过 16 个字符")
    private String matchResult;

    @Schema(description = "EOS/EOM状态：EOS/EOM/NONE", example = "EOS")
    @Size(max = 16, message = "EOS/EOM状态长度不能超过 16 个字符")
    private String eomStatus;

    @Schema(description = "处置建议")
    private String handlingSuggestion;

    @Schema(description = "检查人", example = "1024")
    private Long checkerUserId;

    @Schema(description = "创建人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
