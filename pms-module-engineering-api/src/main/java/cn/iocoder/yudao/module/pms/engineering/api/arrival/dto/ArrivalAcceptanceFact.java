package cn.iocoder.yudao.module.pms.engineering.api.arrival.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** EXE-01到货签收项目里程碑事实。 */
public record ArrivalAcceptanceFact(
        Long tenantId,
        Long projectId,
        List<Long> sourceAcceptanceIds,
        String decision,
        Long factVersion,
        ArrivalScopeWatermark scopeWatermark,
        boolean reopened,
        Set<Long> acceptedDeviceIds,
        Set<Long> exemptedDeviceIds,
        Set<Long> unmetDeviceIds,
        List<ArrivalQuantityScopeFact> acceptedQuantityScopes,
        List<ArrivalQuantityScopeFact> exemptedQuantityScopes,
        List<ArrivalQuantityScopeFact> unmetQuantityScopes) {

    public static final String DECISION_ACCEPTED = "ACCEPTED";
    public static final String DECISION_NOT_ACCEPTED = "NOT_ACCEPTED";
    public static final String DECISION_STALE = "STALE";
    private static final Set<String> DECISIONS = Set.of(
            DECISION_ACCEPTED, DECISION_NOT_ACCEPTED, DECISION_STALE);

    public ArrivalAcceptanceFact {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || sourceAcceptanceIds == null || !DECISIONS.contains(decision)
                || factVersion == null || factVersion < 0 || scopeWatermark == null) {
            throw new IllegalArgumentException("invalid arrival acceptance fact");
        }
        sourceAcceptanceIds = normalizeIds(sourceAcceptanceIds, "source acceptance ids");
        acceptedDeviceIds = normalizeDeviceIds(acceptedDeviceIds);
        exemptedDeviceIds = normalizeDeviceIds(exemptedDeviceIds);
        unmetDeviceIds = normalizeDeviceIds(unmetDeviceIds);
        requireDisjoint(acceptedDeviceIds, exemptedDeviceIds, unmetDeviceIds);

        acceptedQuantityScopes = ArrivalQuantityScopeFact.normalize(acceptedQuantityScopes);
        exemptedQuantityScopes = ArrivalQuantityScopeFact.normalize(exemptedQuantityScopes);
        unmetQuantityScopes = ArrivalQuantityScopeFact.normalize(unmetQuantityScopes);
        requireDisjoint(acceptedQuantityScopes, exemptedQuantityScopes, unmetQuantityScopes);
    }

    private static List<Long> normalizeIds(List<Long> ids, String fieldName) {
        TreeSet<Long> ordered = new TreeSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0 || !ordered.add(id)) {
                throw new IllegalArgumentException(fieldName + " contain invalid or duplicate item");
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(ordered));
    }

    private static Set<Long> normalizeDeviceIds(Set<Long> ids) {
        if (ids == null) {
            throw new IllegalArgumentException("arrival device result is required");
        }
        TreeSet<Long> ordered = new TreeSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("arrival device result contains invalid item");
            }
            ordered.add(id);
        }
        return Collections.unmodifiableSet(ordered);
    }

    @SafeVarargs
    private static <T> void requireDisjoint(java.util.Collection<T>... collections) {
        Set<T> combined = new HashSet<>();
        for (java.util.Collection<T> collection : collections) {
            for (T item : collection) {
                if (!combined.add(item)) {
                    throw new IllegalArgumentException("arrival fact result scopes overlap");
                }
            }
        }
    }
}
