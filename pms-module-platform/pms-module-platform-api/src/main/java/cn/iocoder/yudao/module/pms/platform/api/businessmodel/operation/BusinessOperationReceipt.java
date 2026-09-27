package cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

import java.util.List;

/**
 * 操作回执：命令结果、真实实体引用、文件/审批/专业结果引用、恢复状态。
 * 相同幂等键重放返回原回执；网络未知结果通过查询回执恢复，不能直接重做领域命令。
 */
public record BusinessOperationReceipt(
        ReceiptOutcome outcome,
        EntityRef entityRef,
        Long newConcurrencyBasis,
        List<ResultReference> references,
        String recoveryState,
        String failureReason) {
}
