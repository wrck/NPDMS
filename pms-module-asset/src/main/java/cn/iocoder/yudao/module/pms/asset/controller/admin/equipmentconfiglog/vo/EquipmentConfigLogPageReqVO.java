package cn.iocoder.yudao.module.pms.asset.controller.admin.equipmentconfiglog.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 设备配置日志分页 Request VO（FR-RES-003）。
 */
@Schema(description = "管理后台 - 设备配置日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentConfigLogPageReqVO extends PageParam {

    @Schema(description = "设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "配置类型", example = "RUNNING_CONFIG")
    private String configType;

    @Schema(description = "来源系统，模糊匹配", example = "NMS")
    private String sourceSystem;

    @Schema(description = "配置文件哈希", example = "a1b2c3d4")
    private String fileHash;

    @Schema(description = "采集时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] collectedAt;
}
