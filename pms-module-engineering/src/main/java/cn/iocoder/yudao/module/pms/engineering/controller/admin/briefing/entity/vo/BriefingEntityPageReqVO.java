package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/** HTTP 分页边界；由 Service 转换为 DAL Query，Mapper 不依赖本类。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BriefingEntityPageReqVO extends PageParam {
    private Long projectId;
    private String code;
    private String name;
    private String briefingType;
    private Integer status;
    private Long creatorUserId;
    private Long approverUserId;
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
