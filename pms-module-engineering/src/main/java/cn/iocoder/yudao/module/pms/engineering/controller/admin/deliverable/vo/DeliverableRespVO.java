package cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 阶段交付件归集 Response VO（FR-ENG-027）。
 */
@Schema(description = "管理后台 - 阶段交付件归集 Response VO")
@Data
public class DeliverableRespVO {

    @Schema(description = "交付件编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "阶段编号", example = "3010")
    private Long phaseId;

    @Schema(description = "交付件编码", example = "D20260101001")
    private String code;

    @Schema(description = "交付件名称", example = "上海某某项目日报-20260101")
    private String name;

    @Schema(description = "类型", example = "DAILY")
    private String deliverableType;

    @Schema(description = "来源业务类型", example = "INSTALLATION")
    private String sourceType;

    @Schema(description = "来源业务编号", example = "1024")
    private Long sourceId;

    @Schema(description = "文件地址", example = "https://pms.example.com/deliverable/2026/01/01.pdf")
    private String fileUrl;

    @Schema(description = "文件大小（字节）", example = "102400")
    private Long fileSize;

    @Schema(description = "文件校验值", example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    private String fileChecksum;

    @Schema(description = "状态：0待归集 1已归集 2已作废", example = "1")
    private Integer status;

    @Schema(description = "归集时间")
    private LocalDateTime archivedTime;

    @Schema(description = "归集人编号", example = "1024")
    private Long archivedBy;

    @Schema(description = "备注", example = "实施日报")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
