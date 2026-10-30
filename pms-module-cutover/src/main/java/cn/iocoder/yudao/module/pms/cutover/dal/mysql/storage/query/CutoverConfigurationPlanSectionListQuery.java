package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取配置方案章节；空集合返回空结果。 */
@Builder
public record CutoverConfigurationPlanSectionListQuery(Long tenantId, Set<Long> configurationRevisionIds) {
}
