package cn.iocoder.yudao.module.pms.platform.domain.businessview;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewRules.require;

/**
 * PM-03 / F-PLT-003 / SDS08: pure immutable registration revisions and explicit transitions.
 * No repository, event store, execution idempotency or business entity lifecycle is provided.
 * The caller must compare/persist the returned version atomically against current storage;
 * an in-memory expectedVersion check is not a database CAS or PlatformCommandExecutionApi.
 */
public final class BusinessViewRegistration {

    public static final class Revision {
        private final BusinessViewDescriptor descriptor;
        private final Instant publishedAt;
        private final Instant disabledAt;

        private Revision(BusinessViewDescriptor descriptor, Instant publishedAt, Instant disabledAt) {
            this.descriptor = descriptor;
            this.publishedAt = publishedAt;
            this.disabledAt = disabledAt;
        }

        public BusinessViewDescriptor descriptor() { return descriptor; }
        public Instant publishedAt() { return publishedAt; }
        public Instant disabledAt() { return disabledAt; }
        public boolean isDraft() { return publishedAt == null && disabledAt == null; }
        public boolean availableForNewReference() { return publishedAt != null && disabledAt == null; }
    }

    private final long tenantId;
    private final String entityType;
    private final String ownerContext;
    private final String viewKey;
    private final long version;
    private final Map<Long, Revision> revisions;

    private BusinessViewRegistration(BusinessViewDescriptor identity, long version, Map<Long, Revision> revisions) {
        this.tenantId = identity.tenantId();
        this.entityType = identity.entityType();
        this.ownerContext = identity.ownerContext();
        this.viewKey = identity.viewKey();
        this.version = version;
        this.revisions = Map.copyOf(revisions);
    }

    public static BusinessViewRegistration draft(BusinessViewDescriptor descriptor) {
        require(descriptor != null, "descriptor: required");
        return new BusinessViewRegistration(descriptor, 1,
                Map.of(descriptor.revisionNo(), new Revision(descriptor, null, null)));
    }

    public long version() {
        return version;
    }

    /** Exact revision lookup retains disabled historical content without consulting live Providers. */
    public Revision revision(long tenantId, long revisionNo) {
        require(this.tenantId == tenantId, "registration: tenant mismatch");
        Revision revision = revisions.get(revisionNo);
        require(revision != null, "registration: unknown exact revision");
        return revision;
    }

    public Revision forNewReference(long tenantId, long revisionNo) {
        Revision revision = revision(tenantId, revisionNo);
        require(revision.availableForNewReference(), "registration: revision unavailable for new reference");
        return revision;
    }

    public BusinessViewRegistration editDraft(BusinessViewDescriptor replacement, long expectedVersion) {
        expectVersion(expectedVersion);
        require(replacement != null, "replacement: required");
        require(replacement.tenantId() == tenantId && replacement.entityType().equals(entityType)
                        && replacement.ownerContext().equals(ownerContext) && replacement.viewKey().equals(viewKey),
                "registration: identity/Owner mismatch");
        Revision current = revision(tenantId, replacement.revisionNo());
        require(current.isDraft(), "registration: published content is immutable");
        return replacing(new Revision(replacement, null, null));
    }

    /** Copies configuration only; publishing/disabled metadata never propagates into the new draft. */
    public BusinessViewRegistration copyDraft(long tenantId, long sourceRevisionNo,
                                               long newRevisionNo, long expectedVersion) {
        expectVersion(expectedVersion);
        Revision source = revision(tenantId, sourceRevisionNo);
        long latestRevisionNo = revisions.keySet().stream().mapToLong(Long::longValue).max().orElseThrow();
        require(newRevisionNo > latestRevisionNo, "registration: new revision must exceed existing revisions");
        return replacing(new Revision(source.descriptor().withRevision(newRevisionNo), null, null));
    }

    public BusinessViewRegistration publish(long tenantId, long revisionNo, long expectedVersion,
                                             ControlledBusinessViewCatalog catalog, Instant publishedAt) {
        expectVersion(expectedVersion);
        Revision current = revision(tenantId, revisionNo);
        require(current.isDraft(), "registration: only a draft can be published (no republish)");
        require(catalog != null, "controlled catalog: required");
        Objects.requireNonNull(publishedAt, "publishedAt");
        catalog.validate(current.descriptor());
        return replacing(new Revision(current.descriptor(), publishedAt, null));
    }

    public BusinessViewRegistration disable(long tenantId, long revisionNo,
                                             long expectedVersion, Instant disabledAt) {
        expectVersion(expectedVersion);
        Revision current = revision(tenantId, revisionNo);
        require(current.availableForNewReference(), "registration: only an active published revision can be disabled");
        Objects.requireNonNull(disabledAt, "disabledAt");
        require(!disabledAt.isBefore(current.publishedAt()), "disabledAt: precedes publication");
        return replacing(new Revision(current.descriptor(), current.publishedAt(), disabledAt));
    }

    private void expectVersion(long expectedVersion) {
        if (expectedVersion != version) {
            throw new IllegalStateException("registration: expectedVersion conflict");
        }
    }

    private BusinessViewRegistration replacing(Revision replacement) {
        Map<Long, Revision> updated = new HashMap<>(revisions);
        updated.put(replacement.descriptor().revisionNo(), replacement);
        return new BusinessViewRegistration(replacement.descriptor(), Math.incrementExact(version), updated);
    }
}
