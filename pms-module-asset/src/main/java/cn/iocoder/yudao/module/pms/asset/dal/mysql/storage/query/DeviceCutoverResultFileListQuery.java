package cn.iocoder.yudao.module.pms.asset.dal.mysql.storage.query;

import java.util.Set;
import lombok.Builder;

/** 按精确父对象集合批量读取AST设备割接文件事实；空集合返回空结果。 */
@Builder
public record DeviceCutoverResultFileListQuery(Long tenantId, Set<Long> deviceCutoverResultIds) {
}
