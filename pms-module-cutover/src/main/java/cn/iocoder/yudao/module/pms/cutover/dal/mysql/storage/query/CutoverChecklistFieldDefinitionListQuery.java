package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取统一结构化字段定义；空集合返回空结果。 */
@Builder
public record CutoverChecklistFieldDefinitionListQuery(Long tenantId, Set<Long> itemDefinitionRevisionIds, Set<Long> customChecklistItemIds) {
}
