package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 设备配置日志分页 Request VO（自 /pms/equipment/config-log 旧链承接）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceConfigurationLogPageReqVO extends PageParam {

    private Long deviceId;

    private String configType;

    private String sourceSystem;

    private String fileHash;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] collectedAt;
}
