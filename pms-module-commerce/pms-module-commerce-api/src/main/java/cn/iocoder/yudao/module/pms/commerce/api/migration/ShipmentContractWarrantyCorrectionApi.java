package cn.iocoder.yudao.module.pms.commerce.api.migration;

import java.util.List;

/** COM owns the adjudicated empty year correction; integration owns source evidence and receipts. */
public interface ShipmentContractWarrantyCorrectionApi {
    record Entry(Long targetId, String sourceKey, String expectedFingerprint, String normalizedFingerprint) {}
    record Request(Long tenantId, String owner, List<Entry> entries) {}
    record Result(Long targetId, String sourceKey, String beforeValue, String action, String fingerprint, String normalizedFingerprint) {}
    List<Result> preview(Request request);
    List<Result> apply(Request request);
}
