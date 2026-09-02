package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

/** 受控权威副本批次写入结果。 */
@Deprecated(since = "2026.09")
public record AuthorityWriteResult(
        String sourceBatchId,
        boolean replayed,
        int contractCount,
        int salesOrderCount,
        int salesOrderLineCount) {
}
