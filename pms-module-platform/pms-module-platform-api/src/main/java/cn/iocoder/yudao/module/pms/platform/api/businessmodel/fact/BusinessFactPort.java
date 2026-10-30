package cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

import java.util.List;

/**
 * 公共事实读取：实体字段、关联集合、交付件和审批事实；普通条件不再增加布尔完成 Provider。
 * 专业结果经 {@code BusinessResultPort} 以统一结构返回。
 * 能力未启用时观察结果为 UNAVAILABLE，不虚构满足。
 */
public interface BusinessFactPort {

    FactObservation observeField(EntityDataRef ref, String fieldCode, String sceneCode);

    /**
     * 关系成员观察：按筛选条件返回匹配成员数与完整性；成员不可读或集合不完整时
     * 不得泄露数据，也不得把未知或不完整当作通过。值：匹配成员数（Long）；
     * 去重单位为成员对象身份；漏页或成员不可读一律 PARTIAL/UNKNOWN，不得声称全部满足。
     */
    FactObservation observeMembers(String ownerModule, String entityType, Long entityId,
                                   String relationCode, List<BusinessFieldFilter> scopeFilters, String sceneCode);

    /** 交付件事实：登记材料数、满足要求数与缺口；能力未启用返回 UNAVAILABLE。值：Map 计数结构。 */
    FactObservation observeDelivery(EntityRef entity, String sceneCode);

    /** 审批事实：在途尝试数、已批准数、已驳回数；能力未启用返回 UNAVAILABLE。值：Map 计数结构。 */
    FactObservation observeApproval(EntityRef entity, String sceneCode);
}
