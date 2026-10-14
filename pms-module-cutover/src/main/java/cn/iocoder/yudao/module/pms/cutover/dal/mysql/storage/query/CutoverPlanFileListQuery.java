package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取方案固定槽位文件；空集合返回空结果。 */
@Builder
public record CutoverPlanFileListQuery(Long tenantId, Set<Long> planRevisionIds) {
}
