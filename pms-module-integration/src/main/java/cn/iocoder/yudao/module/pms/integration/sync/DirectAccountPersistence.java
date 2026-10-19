package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** Private direct writers satisfy the existing source interpreters' DTO contracts, not Owner beans. */
@Component
@RequiredArgsConstructor
final class DirectAccountPersistence {
    private final DirectAccountUserMapper users;
    private final DirectAccountRoleMapper roles;
    private final DirectAccountMembershipMapper memberships;
    private final DirectAccountScopeMapper scopes;
    private final DirectAccountAuxiliaryMapper auxiliary;
    private final PasswordEncoder passwordEncoder;
    private final TenantProperties tenantProperties;
    private final OAuth2TokenCommonApi tokens;
    private final DataSource dataSource;
    private final CacheManager caches;

    SystemUserMigrationApi users() { return new DirectAccountUsers(users,auxiliary,passwordEncoder,tenantProperties,tokens,dataSource); }
    SystemRoleMigrationApi roles() { return new DirectAccountRoles(roles,caches,dataSource); }
    SystemUserRoleMigrationApi memberships() { return new DirectAccountMemberships(memberships,dataSource,caches); }
    SystemUserScopeMigrationApi scopes() { return new DirectAccountScopes(scopes,dataSource); }
}
