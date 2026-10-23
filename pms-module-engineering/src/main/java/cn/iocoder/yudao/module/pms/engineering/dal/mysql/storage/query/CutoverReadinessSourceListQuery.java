package cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取IMP实施完成来源；空集合返回空结果。 */
@Builder
public record CutoverReadinessSourceListQuery(Long tenantId, Set<Long> snapshotIds) {
}
