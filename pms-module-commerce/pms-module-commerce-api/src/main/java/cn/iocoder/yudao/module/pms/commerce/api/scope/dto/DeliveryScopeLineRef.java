package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

/**
 * 设备清单行引用：清单行 = 合同对应销售订单行在项目内的交付范围分配。
 * scopeDetailId 为交付范围明细拆分行；scopeId 为无明细拆分的交付范围行；二者必有其一。
 */
public record DeliveryScopeLineRef(Long scopeDetailId, Long scopeId) {

    public static DeliveryScopeLineRef ofDetail(Long scopeDetailId) {
        return new DeliveryScopeLineRef(scopeDetailId, null);
    }

    public static DeliveryScopeLineRef ofScope(Long scopeId) {
        return new DeliveryScopeLineRef(null, scopeId);
    }
}
