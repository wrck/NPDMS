package cn.iocoder.yudao.module.pms.project.api.workbinding.result;

import java.time.LocalDateTime;
import java.util.Objects;

/** Internal Owner fact boundary; configuration metadata neither executes commands nor grants user access. */
public interface BusinessResultSource {
    Descriptor descriptor();
    Observation inspect(Query query);

    record Type(String ownerContext, String entityType, String resultType) {
        public Type { text(ownerContext); text(entityType); text(resultType); }
    }

    /** A retained exact result is distinct from the ability to prove a new-result formation boundary. */
    record Descriptor(Type type, boolean currentLookup, boolean exactLookup, boolean historicalLookup) {
        public Descriptor {
            Objects.requireNonNull(type, "result type");
            if (!currentLookup && !exactLookup || historicalLookup && !exactLookup)
                throw new IllegalArgumentException("RESULT_CAPABILITIES_INVALID");
        }
    }

    /** Null resultId requests the current result of one explicit business object, never an arbitrary first match. */
    record Query(Long tenantId, Long projectId, Type type, String objectId, String resultId) {
        public Query {
            if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0)
                throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
            Objects.requireNonNull(type, "result type");
            if (objectId == null && resultId == null) throw new IllegalArgumentException("RESULT_IDENTITY_REQUIRED");
            if (objectId != null) text(objectId);
            if (resultId != null) text(resultId);
        }
    }

    enum Validity { CURRENT, NOT_CURRENT, REVOKED }
    enum Status { AVAILABLE, NOT_FOUND, NOT_FORMED, UNAVAILABLE }

    /** Native IDs identify the fact; observationVersion only describes the current concurrency observation. */
    record Result(Long tenantId, Long projectId, Type type, String objectId, String resultId,
                  String businessRevision, String observationVersion, Validity validity, LocalDateTime formedAt) {
        public Result {
            new Query(tenantId, projectId, type, objectId, resultId);
            text(objectId); text(resultId);
            if (businessRevision != null) text(businessRevision);
            if (observationVersion != null) text(observationVersion);
            Objects.requireNonNull(validity, "result validity");
        }
    }

    record Observation(Status status, Result result, String reason) {
        public Observation {
            Objects.requireNonNull(status, "result status");
            if ((status == Status.AVAILABLE) != (result != null))
                throw new IllegalArgumentException("RESULT_OBSERVATION_INVALID");
            if (status == Status.AVAILABLE ? reason != null : reason == null || reason.isBlank())
                throw new IllegalArgumentException("RESULT_REASON_INVALID");
        }
        public static Observation available(Result result) { return new Observation(Status.AVAILABLE, result, null); }
        public static Observation absent(Status status, String reason) { return new Observation(status, null, reason); }
    }

    /** Only adapters for native numeric IDs use this; the shared contract keeps arbitrary string IDs lossless. */
    static Long nativeId(String value) {
        if (value == null) return null;
        try {
            long id = Long.parseLong(value);
            if (id <= 0 || !Long.toString(id).equals(value)) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("RESULT_NATIVE_ID_INVALID"); }
    }

    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 128)
            throw new IllegalArgumentException("RESULT_IDENTITY_INVALID");
    }
}
