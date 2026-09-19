package cn.iocoder.yudao.module.pms.project.api.workbinding.result;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Bounded native inventory. Its cursor is an enumeration position, never a commit or formation boundary. */
public interface BusinessResultInventorySource extends BusinessResultSource {
    InventoryPage inventory(InventoryQuery query);

    record InventoryQuery(Long tenantId, Long projectId, Type type, boolean historical,
                          List<String> objectIds, String after, int limit) {
        public InventoryQuery {
            if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0 || limit < 1 || limit > 100)
                throw new IllegalArgumentException("RESULT_INVENTORY_SCOPE_INVALID");
            Objects.requireNonNull(type, "result type");
            if (objectIds != null) {
                objectIds = List.copyOf(objectIds);
                if (objectIds.stream().anyMatch(id -> id.isBlank() || id.length() > 128)
                        || new HashSet<>(objectIds).size() != objectIds.size())
                    throw new IllegalArgumentException("RESULT_INVENTORY_OBJECTS_INVALID");
            }
            if (after != null && (after.isBlank() || after.length() > 128))
                throw new IllegalArgumentException("RESULT_INVENTORY_CURSOR_INVALID");
        }
    }

    record InventoryPage(String nextCursor, boolean complete, List<Observation> observations) {
        public InventoryPage { observations = List.copyOf(observations); }
    }

    static List<Long> nativeObjects(InventoryQuery query) {
        return query.objectIds() == null ? null : query.objectIds().stream().map(BusinessResultSource::nativeId).toList();
    }

    /** Numeric-ID Owners opt into this helper; other native ID types implement their own bounded cursor. */
    static InventoryPage nativePage(InventoryQuery query, List<String> ids, Function<String, Observation> read) {
        if (ids == null || ids.size() > query.limit() + 1) throw new IllegalStateException("RESULT_INVENTORY_PAGE_INVALID");
        Long previous = BusinessResultSource.nativeId(query.after());
        for (String id : ids) {
            Long current = BusinessResultSource.nativeId(id);
            if (current == null || previous != null && current <= previous)
                throw new IllegalStateException("RESULT_INVENTORY_ORDER_INVALID");
            previous = current;
        }
        int count = Math.min(ids.size(), query.limit());
        List<Observation> results = new ArrayList<>();
        for (int i = 0; i < count; i++) results.add(Objects.requireNonNull(read.apply(ids.get(i)), "Owner inventory observation"));
        return new InventoryPage(count == 0 ? query.after() : ids.get(count - 1), ids.size() <= query.limit(), results);
    }
}
