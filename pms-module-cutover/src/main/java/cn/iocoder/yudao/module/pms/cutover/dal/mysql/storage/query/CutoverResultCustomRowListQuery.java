package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取真正自定义的表格结果行；空集合返回空结果。 */
@Builder
public record CutoverResultCustomRowListQuery(Long tenantId, Set<Long> checklistResultIds) {
}
