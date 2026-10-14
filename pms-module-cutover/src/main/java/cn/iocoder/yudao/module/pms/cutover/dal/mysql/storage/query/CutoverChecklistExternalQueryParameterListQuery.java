package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取外部查询实际参数；空集合返回空结果。 */
@Builder
public record CutoverChecklistExternalQueryParameterListQuery(Long tenantId, Set<Long> externalQueryIds) {
}
