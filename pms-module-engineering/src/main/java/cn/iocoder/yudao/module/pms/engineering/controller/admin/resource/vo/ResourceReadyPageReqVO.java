package cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 资源与备件就绪分页 Request VO（FR-ENG-018）。
 */
@Schema(description = "管理后台 - 资源与备件就绪分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceReadyPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "资源编码，模糊匹配", example = "R2026")
    private String code;

    @Schema(description = "资源名称，模糊匹配", example = "交换机")
    private String name;

    @Schema(description = "资源类型", example = "SPARE")
    private String resourceType;

    @Schema(description = "关联设备编号", example = "2048")
    private Long equipmentId;

    @Schema(description = "就绪状态：0未就绪 1已就绪 2异常", example = "0")
    private Integer readyStatus;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
