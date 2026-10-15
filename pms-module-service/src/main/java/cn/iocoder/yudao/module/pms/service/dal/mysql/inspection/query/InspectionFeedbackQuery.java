package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionFeedbackQuery extends PageParam {
    private Long tenantId;
    private Long actorId;
    private Set<Long> visibleProjectIds;
    private boolean administrator;
    private boolean details;
    private Long ruleRevisionId;
    private String detectionId;
    private String categoryCode;
    private String severityCode;
    private String productModel;
    private LocalDateTime fromAt;
    private LocalDateTime toAt;
    private LocalDateTime dataAsOf;
    private Boolean currentOnly;
    private String confirmation;
    public int getOffset() { return Math.multiplyExact(getPageNo() - 1, getPageSize()); }
}
