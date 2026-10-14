package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取批准后保障联系信息变更；空集合返回空结果。 */
@Builder
public record CutoverPlanSupportContactChangeListQuery(Long tenantId, Set<Long> supportArrangementIds) {
}
