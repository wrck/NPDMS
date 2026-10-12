package cn.iocoder.yudao.module.system.api.migration;

import java.util.List;

/** Additive carry into the existing user-role relationship, never replacing current permissions. */
public interface SystemUserRoleMigrationApi {
    record Relation(Long id, Long userId, Long roleId) {}
    record Entry(String object, String sourceKey, Long userId, Long roleId, String expectedRoleCode, Long targetId) {}
    record Request(Long tenantId, String owner, List<Entry> entries) {}
    record Result(String object, String sourceKey, Relation before, Relation after, String action) {}
    List<Result> preview(Request request);
    List<Result> apply(Request request);
    void refreshCaches();
}
