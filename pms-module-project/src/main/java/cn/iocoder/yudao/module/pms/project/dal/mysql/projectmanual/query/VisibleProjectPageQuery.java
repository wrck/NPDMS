package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 服务端已解析项目范围后的项目分页查询。
 *
 * 时间范围统一左闭右开 [startInclusive, endExclusive)；成员类筛选（经理/服务经理/销售）
 * 与设备条件由 Service 先解析为项目 ID 集合并取交集，不进入本查询。
 */
@Data
@Builder
public class VisibleProjectPageQuery {
    private Long tenantId;
    private Set<Long> visibleProjectIds;
    private PageParam pageParam;
    private String projectNameKeyword;
    private String projectCodePrefix;
    private String status;
    private String signingMethod;
    private String projectCategory;
    private String implementationMode;
    private String majorProjectLevel;
    private String contractNoKeyword;
    private Long departmentId;
    private Long companyId;
    private String agentServiceProviderKeyword;
    private LocalDateTime effectiveAt;
    private LocalDateTime createTimeStart;
    private LocalDateTime createTimeEnd;
    private LocalDateTime closeTimeStart;
    private LocalDateTime closeTimeEnd;
    private LocalDateTime refreshTimeStart;
    private LocalDateTime refreshTimeEnd;
}
