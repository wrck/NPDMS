package cn.iocoder.yudao.module.system.api.migration;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory references from preceding Owner previews in one account-foundation batch.
 * Negative IDs identify planned objects only. They are never accepted by apply or saved as
 * formal identities or source-binding target IDs; preview evidence may describe planned references.
 * Existing positive IDs always retain database tenant/existence validation.
 */
public record SystemAccountMigrationPreview(Long tenantId, String owner, Set<Long> plannedUserIds,
        Map<Long, String> plannedRoleCodes, Set<Membership> plannedMemberships) {
    public record Membership(Long userId, String roleCode) {}

    public SystemAccountMigrationPreview {
        plannedUserIds = Set.copyOf(plannedUserIds);
        plannedRoleCodes = Map.copyOf(plannedRoleCodes);
        plannedMemberships = Set.copyOf(plannedMemberships);
        Set<Long> users = plannedUserIds;
        if (tenantId == null || owner == null || owner.isBlank()
                || plannedUserIds.stream().anyMatch(id -> id >= 0)
                || plannedRoleCodes.entrySet().stream().anyMatch(e -> e.getKey() >= 0 || e.getValue().isBlank())
                || plannedMemberships.stream().anyMatch(m -> m.userId() == null || m.userId() == 0
                    || m.roleCode() == null || m.roleCode().isBlank()
                    || m.userId() < 0 && !users.contains(m.userId())))
            throw new IllegalArgumentException("本轮账号预览引用无效");
    }

    public void requireOwner(Long tenant, String expectedOwner) {
        if (!Objects.equals(tenantId, tenant) || !Objects.equals(owner, expectedOwner))
            throw new IllegalArgumentException("本轮账号预览不能跨租户或运行所有权复用");
    }

    public static SystemAccountMigrationPreview empty(Long tenant, String owner) {
        return new SystemAccountMigrationPreview(tenant, owner, Set.of(), Map.of(), Set.of());
    }
}
