package cn.iocoder.yudao.module.system.api.migration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** SYSTEM owns role writes. Source bindings, preview approval and retry remain in integration. */
public interface SystemRoleMigrationApi {
    record Definition(String code, String name, Integer sort, String remark,
                      String creator, LocalDateTime createTime, String updater, LocalDateTime updateTime) {}
    record Role(Long id, Definition definition, Integer status, Integer type,
                Integer dataScope, Set<Long> dataScopeDeptIds) {}
    /** Reference entries preserve current definitions and privileges; only primary entries own metadata. */
    record Entry(String object, String sourceKey, Definition definition, Long targetId,
                 Definition lastApplied, boolean referenceOnly) {}
    record Request(Long tenantId, String owner, List<Entry> entries, boolean adoptExisting) {}
    record Result(String object, String sourceKey, Role before, Role after, String action, String message) {}
    List<Result> preview(Request request);
    /** Must participate in the transaction that also commits source bindings and migration evidence. */
    List<Result> apply(Request request);
    record AuditCorrection(Role expectedCurrent, Role restored) {}
    record AuditCorrectionRequest(Long tenantId, String owner, String generatedActor, List<AuditCorrection> entries) {}
    record AuditCorrectionResult(Role before, Role after, String action) {}
    List<AuditCorrectionResult> previewAuditCorrection(AuditCorrectionRequest request);
    List<AuditCorrectionResult> applyAuditCorrection(AuditCorrectionRequest request);
    void refreshCaches();
}
