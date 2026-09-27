package cn.iocoder.yudao.module.pms.platform.api.businessmodel.result;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

import java.time.LocalDateTime;

/**
 * 统一业务结果：结果类型、对象与结果身份、形成依据、有效性、可选修订引用。
 * 字段修改时间不能替代结果形成依据；固定结果查不到时不得回退最新。
 * sequence 为形成序号（Owner 登记时分配的全局递增序号），是轮次边界的唯一依据。
 */
public record BusinessResultRecord(
        String resultType,
        EntityRef objectRef,
        String resultId,
        ResultSemantics semantics,
        String formationBasis,
        LocalDateTime formedAt,
        boolean valid,
        Long revisionId,
        long sequence) {
}
