package cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 巡检规则分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvRulePageReqVO extends PageParam {

    @Schema(description = "规则编码，模糊匹配", example = "R-001")
    private String code;

    @Schema(description = "规则名称，模糊匹配", example = "标准")
    private String name;

    @Schema(description = "规则类型 ONLINE 在线 / OFFLINE 离线", example = "ONLINE")
    private String ruleType;

    @Schema(description = "状态 0草稿 1已发布 2已停用", example = "0")
    private Integer status;

    @Schema(description = "生效时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] effectiveTime;

}
