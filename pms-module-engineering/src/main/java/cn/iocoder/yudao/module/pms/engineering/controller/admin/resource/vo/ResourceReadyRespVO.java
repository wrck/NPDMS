package cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 资源与备件就绪 Response VO（FR-ENG-018）。
 */
@Schema(description = "管理后台 - 资源与备件就绪 Response VO")
@Data
public class ResourceReadyRespVO {

    @Schema(description = "资源编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "资源编码", example = "R20260101001")
    private String code;

    @Schema(description = "资源名称", example = "核心交换机备件")
    private String name;

    @Schema(description = "资源类型", example = "SPARE")
    private String resourceType;

    @Schema(description = "关联设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "数量", example = "10")
    private Integer quantity;

    @Schema(description = "就绪状态：0未就绪 1已就绪 2异常", example = "0")
    private Integer readyStatus;

    @Schema(description = "就绪时间")
    private LocalDateTime readyTime;

    @Schema(description = "就绪确认人编号", example = "1024")
    private Long readyUserId;

    @Schema(description = "备注", example = "需提前一周到货")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
