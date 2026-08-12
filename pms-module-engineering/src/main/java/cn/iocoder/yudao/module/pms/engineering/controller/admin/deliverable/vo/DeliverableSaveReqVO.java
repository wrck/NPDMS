package cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 阶段交付件归集新增/修改 Request VO（FR-ENG-027）。
 * <p>
 * 已归集（status=1）的交付件不可修改，仅可作废。
 */
@Schema(description = "管理后台 - 阶段交付件归集新增/修改 Request VO")
@Data
public class DeliverableSaveReqVO {

    @Schema(description = "交付件编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "阶段编号", example = "3010")
    private Long phaseId;

    @Schema(description = "交付件编码，项目内唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "D20260101001")
    @NotBlank(message = "交付件编码不能为空")
    @Size(max = 64, message = "交付件编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "交付件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "上海某某项目日报-20260101")
    @NotBlank(message = "交付件名称不能为空")
    @Size(max = 255, message = "交付件名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "类型 DAILY 日报 / RECEIPT 签收单 / SERVICE 服务单 / COMPLETION 完工证明 / TEST 测试记录 / CONFIG 配置档案",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "DAILY")
    @NotBlank(message = "类型不能为空")
    @Size(max = 32, message = "类型长度不能超过 32 个字符")
    private String deliverableType;

    @Schema(description = "来源业务类型", example = "INSTALLATION")
    @Size(max = 32, message = "来源业务类型长度不能超过 32 个字符")
    private String sourceType;

    @Schema(description = "来源业务编号", example = "1024")
    private Long sourceId;

    @Schema(description = "文件地址", example = "https://pms.example.com/deliverable/2026/01/01.pdf")
    @Size(max = 500, message = "文件地址长度不能超过 500 个字符")
    private String fileUrl;

    @Schema(description = "文件大小（字节）", example = "102400")
    private Long fileSize;

    @Schema(description = "文件校验值（SHA-256）", example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    @Size(max = 128, message = "文件校验值长度不能超过 128 个字符")
    private String fileChecksum;

    @Schema(description = "备注", example = "实施日报")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
