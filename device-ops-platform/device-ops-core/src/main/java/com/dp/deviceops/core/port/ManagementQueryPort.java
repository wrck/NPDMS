package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.CollectionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only, explicitly scoped projections. Implementations must enforce scope in the database. */
public interface ManagementQueryPort {
    Page<Summary> collections(Scope scope, Filter filter, int page, int size);
    Overview overview(Scope scope, Filter filter);
    Page<ScriptVersion> scripts(Scope scope, Filter filter, int page, int size);
    Optional<ScriptContent> content(Scope scope, String namespace, String collectionId);

    record Scope(List<String> namespaces, List<String> projects, boolean allNamespaces) {
        public Scope {
            namespaces = List.copyOf(namespaces);
            projects = List.copyOf(projects);
        }

        public Scope(List<String> namespaces, List<String> projects) {
            this(namespaces, projects, false);
        }
    }
    record Filter(String namespace, String project, String device, CollectionStatus status, Instant from, Instant to) {
        public Filter {
            if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("from must not exceed to");
        }
        public static Filter empty() { return new Filter(null, null, null, null, null, null); }
    }
    record Page<T>(List<T> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Summary(String collectionId, String namespace, String projectKey, String externalRequestId,
                   String activityType, CollectionStatus status, Instant createdAt, long targetCount,
                   String scriptSource, String scriptKey, String scriptVersion, String scriptSha256) { }
    record Overview(long total, Map<String, Long> byStatus) {
        public Overview { byStatus = Map.copyOf(byStatus); }
    }
    record ScriptVersion(String namespace, String scriptKey, String version, String source, String sha256,
                         String parserType, String collectionId, boolean contentReadable) { }
    record ScriptContent(String namespace, String scriptKey, String version, String source, String sha256, String content) { }
}
