package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

import java.util.Map;

/**
 * 标准读取结果：身份、字段值、并发依据和可用性。原生标识及历史引用不得丢失，
 * revisionId 非空表示读取的是指定修订而非当前对象。
 */
public record BusinessEntityData(
        EntityRef ref,
        Long revisionId,
        Map<String, Object> fieldValues,
        Long concurrencyBasis,
        boolean available,
        String unavailableReason) {
}
