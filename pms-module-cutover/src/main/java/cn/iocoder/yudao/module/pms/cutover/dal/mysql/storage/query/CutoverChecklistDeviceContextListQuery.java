package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取P3设备及软件版本上下文；空集合返回空结果。 */
@Builder
public record CutoverChecklistDeviceContextListQuery(Long tenantId, Set<Long> checklistIds) {
}
