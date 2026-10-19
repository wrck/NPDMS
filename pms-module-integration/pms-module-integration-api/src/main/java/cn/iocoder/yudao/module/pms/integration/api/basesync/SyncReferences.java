package cn.iocoder.yudao.module.pms.integration.api.basesync;

import java.util.List;

/** Shared reference reads consumed by the base workflow, never business write APIs. */
public interface SyncReferences {
    record AuditUsersQuery(long tenantId, List<String> usernames) {
        public AuditUsersQuery { usernames = List.copyOf(usernames); }
    }
    record AuditUser(long id, String username) {}
    List<AuditUser> findAuditUsers(AuditUsersQuery query);
    record AuditCatalogueQuery(long tenantId) {}
    default List<AuditUser> findAuditUserCatalogue(AuditCatalogueQuery query){throw new UnsupportedOperationException("未提供本租户审计账号目录");}

    /** Opt-in for tasks whose approved mapping resolves historical audit usernames. */
    interface SourceAudit {
        String getSourceCreator();
        String getSourceUpdater();
        SourceAudit setResolvedCreator(String value);
        SourceAudit setResolvedUpdater(String value);
        default boolean requiresAuditReferences() { return true; }
        default boolean requiresResolvedAuditReferences() { return false; }
        default boolean usesAuditCatalogue() { return false; }
    }
}
