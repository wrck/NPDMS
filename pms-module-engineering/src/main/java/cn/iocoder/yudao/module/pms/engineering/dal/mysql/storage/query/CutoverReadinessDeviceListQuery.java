package cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取IMP就绪冻结设备；空集合返回空结果。 */
@Builder
public record CutoverReadinessDeviceListQuery(Long tenantId, Set<Long> snapshotIds) {
}
