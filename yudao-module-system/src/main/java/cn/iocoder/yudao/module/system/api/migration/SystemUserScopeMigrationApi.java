package cn.iocoder.yudao.module.system.api.migration;

import java.time.LocalDateTime;
import java.util.List;

/** Historical scope qualification in the existing formal table; grants no project membership. */
public interface SystemUserScopeMigrationApi {
    record Scope(Long userId, Long companyId, String companyCode, String companyName,
                 Long departmentId, String departmentCode, String departmentName, String scopeRole,
                 Boolean primary, LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Integer status) {}
    record Audit(String creator, LocalDateTime createTime, String updater, LocalDateTime updateTime) {}
    record Relation(Long id, Scope scope, Audit audit, Integer version) {}
    record Entry(String object, String sourceKey, Scope scope, Audit audit, Long targetId, Relation lastApplied) {}
    record Request(Long tenantId, String owner, List<Entry> entries) {}
    record Result(String object, String sourceKey, Relation before, Relation after, String action) {}
    List<Result> preview(Request request);
    record FoundationPreview(Request request, SystemAccountMigrationPreview preceding) {}
    List<Result> previewAfterFoundation(FoundationPreview request);
    /** Must share the transaction that persists the integration source bindings and run evidence. */
    List<Result> apply(Request request);
}
