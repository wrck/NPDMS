package cn.iocoder.yudao.module.pms.project.model;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.PermissionBusinessAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTreeCatalogPermissionTest {

    @Mock private PermissionCommonApi permissions;
    // Catalog declarations do not query their Mapper dependencies.
    @InjectMocks private ProjectBusinessModelContributor contributor;
    private MockedStatic<SecurityFrameworkUtils> security;
    private PermissionBusinessAccessGuard guard;
    private List<BusinessModelDescriptor> treeModels;
    private final EntityActor actor = new EntityActor(1L, 9L, null);

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser();
        user.setId(9L);
        user.setTenantId(1L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(user);
        TenantContextHolder.setTenantId(1L);
        guard = new PermissionBusinessAccessGuard(permissions);
        treeModels = contributor.declarations().stream()
                .map(BusinessModelDeclaration::descriptor)
                .filter(model -> Set.of("treeVersion", "treeChange").contains(model.entityType()))
                .toList();
        assertEquals(2, treeModels.size());
    }

    @AfterEach
    void tearDown() {
        security.close();
        TenantContextHolder.clear();
    }

    @Test
    void currentProjectQueryPermissionAllowsBothTreeCatalogEntries() {
        when(permissions.hasAnyPermissions(9L, "pms:project:query")).thenReturn(true);
        treeModels.forEach(model -> assertDoesNotThrow(
                () -> guard.requireReadable(model, actor, "catalog")));
    }

    @Test
    void missingProjectQueryPermissionRejectsBothTreeDetails() {
        treeModels.forEach(model -> assertThrows(BusinessContractException.class,
                () -> guard.requireReadable(model, actor, "detail")));
    }

    @Test
    void anotherTenantCannotUseTheAuthenticatedIdentity() {
        EntityActor anotherTenant = new EntityActor(2L, 9L, null);
        treeModels.forEach(model -> assertThrows(BusinessContractException.class,
                () -> guard.requireReadable(model, anotherTenant, "detail")));
    }
}
