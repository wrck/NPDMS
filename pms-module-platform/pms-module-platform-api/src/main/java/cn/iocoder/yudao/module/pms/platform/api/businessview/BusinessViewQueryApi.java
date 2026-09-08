package cn.iocoder.yudao.module.pms.platform.api.businessview;

/**
 * PM-03 / PM-11: narrow exact published-registration query. Tenant and actor come from trusted
 * server context. Callers must first authorize their template or runtime Owner object; this
 * interface neither requires global registration-management permissions nor grants object
 * permissions. No entity values, creation, Provider invocation or mutable latest lookup.
 */
public interface BusinessViewQueryApi {
    BusinessViewRevision getRevision(Query query);

    /**
     * PM-03: authenticated template Owner calls after its own authorization. Must join an existing
     * transaction; locks registration identity/revision, then validates deployed dependencies in
     * Owner lock order. NEW_REFERENCE and expectedVersion are mandatory. No object authorization
     * is granted and no global registration-management permission is required.
     */
    BusinessViewRevision lockAndRevalidate(Query query);

    /** Locks all registration identities before any dependency; output follows input order. */
    java.util.List<BusinessViewRevision> lockAndRevalidateAll(java.util.List<Query> queries);

    enum Purpose { NEW_REFERENCE, HISTORICAL_REFERENCE }
    record Query(Long revisionId, Purpose purpose, Integer expectedVersion) {
        public Query(Long revisionId, Purpose purpose) { this(revisionId, purpose, null); }
    }
}
