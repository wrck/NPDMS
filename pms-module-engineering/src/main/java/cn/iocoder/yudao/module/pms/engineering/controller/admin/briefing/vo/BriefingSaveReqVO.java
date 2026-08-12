package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 工程交底书新增/修改 Request VO（FR-ENG-006）。
 */
@Schema(description = "管理后台 - PMS 工程交底书新增/修改 Request VO")
@Data
public class BriefingSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "交底书编号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "BR-2026-001")
    @NotBlank(message = "交底书编号不能为空")
    @Size(max = 64, message = "交底书编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "交底书名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX网络安全项目工程交底书")
    @NotBlank(message = "交底书名称不能为空")
    @Size(max = 200, message = "交底书名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "交底类型：STANDARD 标准 / EMERGENCY 紧急 / CUSTOM 自定义", example = "STANDARD")
    @Size(max = 32, message = "交底类型长度不能超过 32 个字符")
    private String briefingType;

    @Schema(description = "关联交底书模板ID", example = "1024")
    private Long templateId;

    @Schema(description = "模板快照JSON（模板版本固定到实例）")
    private String templateSnapshot;

    @Schema(description = "前序基线数据快照JSON（需求/方案/工勘聚合）")
    private String sourceSnapshot;

    @Schema(description = "交底内容富文本")
    private String content;

    @Schema(description = "生成的文件URL")
    @Size(max = 512, message = "文件URL长度不能超过 512 个字符")
    private String fileUrl;

    @Schema(description = "文件名")
    @Size(max = 200, message = "文件名长度不能超过 200 个字符")
    private String fileName;

    @Schema(description = "文件大小（字节）", example = "102400")
    private Long fileSize;

    @Schema(description = "文件校验值")
    @Size(max = 64, message = "文件校验值长度不能超过 64 个字符")
    private String fileChecksum;

    @Schema(description = "编制人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注", example = "需要补充现场照片")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
