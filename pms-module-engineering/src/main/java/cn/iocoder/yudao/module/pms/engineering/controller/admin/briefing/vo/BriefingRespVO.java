package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 工程交底书 Response VO（FR-ENG-006）。
 */
@Schema(description = "管理后台 - PMS 工程交底书 Response VO")
@Data
public class BriefingRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "交底书编号", example = "BR-2026-001")
    private String code;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "交底书名称", example = "XX网络安全项目工程交底书")
    private String name;

    @Schema(description = "交底类型", example = "STANDARD")
    private String briefingType;

    @Schema(description = "关联交底书模板ID", example = "1024")
    private Long templateId;

    @Schema(description = "模板快照JSON")
    private String templateSnapshot;

    @Schema(description = "前序基线数据快照JSON")
    private String sourceSnapshot;

    @Schema(description = "交底内容富文本")
    private String content;

    @Schema(description = "生成的文件URL")
    private String fileUrl;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）", example = "102400")
    private Long fileSize;

    @Schema(description = "文件校验值")
    private String fileChecksum;

    @Schema(description = "状态：0 草稿 1 已生成 2 已审核 3 已发布 4 已作废", example = "0")
    private Integer status;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "生成时间")
    private LocalDateTime generateTime;

    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "审核人", example = "1024")
    private Long approverUserId;

    @Schema(description = "审核意见")
    private String approveOpinion;

    @Schema(description = "审核时间")
    private LocalDateTime approveTime;

    @Schema(description = "编制人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
