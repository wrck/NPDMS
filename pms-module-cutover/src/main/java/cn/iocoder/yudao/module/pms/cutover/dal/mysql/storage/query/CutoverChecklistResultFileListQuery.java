package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取统一结果文件引用；空集合返回空结果。 */
@Builder
public record CutoverChecklistResultFileListQuery(Long tenantId, Set<Long> checklistResultIds) {
}
