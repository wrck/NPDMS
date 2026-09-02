package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

/** 受控权威副本批次写入结果。 */
public record AuthorityWriteResult(
        String sourceBatchId,
        boolean replayed,
        int contractCount,
        int salesOrderCount,
        int salesOrderLineCount) {
}
