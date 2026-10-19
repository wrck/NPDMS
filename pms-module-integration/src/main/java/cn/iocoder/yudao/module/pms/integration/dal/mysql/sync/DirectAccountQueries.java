package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import java.util.Set;

public final class DirectAccountQueries {
    private DirectAccountQueries() {}
    public record UserMigrationTenantQuery(Long tenantId) {}
    public record UserMigrationReferenceQuery(Long tenantId, Set<Long> ids) {}
    public record RoleMigrationTenantQuery(Long tenantId) {}
    public record RoleMigrationAuditActorQuery(Long tenantId, Set<Long> userIds) {}
    public record UserRoleMigrationQuery(Long tenantId, Set<Long> userIds, Set<Long> roleIds) {}
    public record UserScopeMigrationReferenceQuery(Long tenantId, Set<Long> userIds,
                                               Set<Long> companyIds, Set<Long> departmentIds,
                                               Set<String> roleCodes) {}
    public record UserScopeMigrationTenantQuery(Long tenantId) {}
}
