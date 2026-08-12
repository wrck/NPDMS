package cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 准备数据表单实例 Response VO（FR-ENG-007）。
 */
@Schema(description = "管理后台 - PMS 准备数据表单实例 Response VO")
@Data
public class FormInstanceRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "实例编号", example = "FI-2026-001")
    private String code;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "关联模板ID", example = "1024")
    private Long templateId;

    @Schema(description = "模板快照JSON")
    private String templateSnapshot;

    @Schema(description = "填报数据JSON")
    private String formData;

    @Schema(description = "实例名称", example = "XX项目防火墙采集表")
    private String name;

    @Schema(description = "状态：0 待填 1 已填 2 已提交 3 已审核 4 已驳回", example = "0")
    private Integer status;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "审核人", example = "1024")
    private Long approverUserId;

    @Schema(description = "审核意见")
    private String approveOpinion;

    @Schema(description = "审核时间")
    private LocalDateTime approveTime;

    @Schema(description = "填报人", example = "1024")
    private Long fillerUserId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
