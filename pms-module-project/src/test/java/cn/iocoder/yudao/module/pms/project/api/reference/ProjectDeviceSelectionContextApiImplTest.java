package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeviceSelectionContextApiImplTest {
    private final ProjectMasterMapper mapper = mock(ProjectMasterMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final ProjectContractQueryApi contracts = mock(ProjectContractQueryApi.class);
    private final ProjectDeviceSelectionContextApiImpl api = new ProjectDeviceSelectionContextApiImpl(mapper, scope, contracts);
    private MockedStatic<SecurityFrameworkUtils> security;
    private ProjectMasterDO project;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(99L);
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 1L, Set.of(10L), Set.of()));
        project = new ProjectMasterDO(); project.setId(10L); project.setTenantId(1L); project.setContractNo(" MANUAL ");
        when(mapper.selectById(10L)).thenReturn(project);
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @Test void formalContractsTakePrecedenceOverManualCompatibilityField() {
        when(contracts.getCurrentContractNumbers(10L)).thenReturn(Set.of("FORMAL-A", "FORMAL-B"));
        assertEquals(Set.of("FORMAL-A", "FORMAL-B"), api.getContractNumbers(10L));
    }
    @Test void manualProjectUsesItsRegisteredContractWhenNoFormalRelationExists() {
        when(contracts.getCurrentContractNumbers(10L)).thenReturn(Set.of());
        assertEquals(Set.of("MANUAL"), api.getContractNumbers(10L));
    }
    @Test void projectWithoutContractDoesNotMatchBlankContracts() {
        project.setContractNo(" ");
        when(contracts.getCurrentContractNumbers(10L)).thenReturn(Set.of());
        assertTrue(api.getContractNumbers(10L).isEmpty());
    }
    @Test void unauthorizedProjectDoesNotExposeContractNumbers() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 1L, Set.of(), Set.of(10L)));
        assertThrows(ServiceException.class, () -> api.getContractNumbers(10L));
        verifyNoInteractions(mapper, contracts);
    }
    @Test void crossTenantProjectIsRejectedEvenIfProviderScopeWasIncorrect() {
        project.setTenantId(2L);
        assertThrows(ServiceException.class, () -> api.getContractNumbers(10L));
        verifyNoInteractions(contracts);
    }
    @Test void explicitTaskActorWorksWithoutBrowserLoginAndIsUsedForScope() {
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
        assertEquals(Set.of("MANUAL"),api.getContractNumbers(new ProjectDeviceSelectionContextApi.Authorization(1L,42L,10L)));
        verify(scope).resolveCurrent(argThat(query -> query.subjectUserId().equals(42L) && query.tenantId().equals(1L)));
        security.verify(SecurityFrameworkUtils::getLoginUserId,never());
    }
    @Test void explicitTaskContextRejectsAnotherTenantAndMissingActor() {
        assertThrows(ServiceException.class,()->api.getContractNumbers(new ProjectDeviceSelectionContextApi.Authorization(2L,42L,10L)));
        assertThrows(ServiceException.class,()->api.getContractNumbers(new ProjectDeviceSelectionContextApi.Authorization(1L,null,10L)));
        verifyNoInteractions(scope,mapper,contracts);
    }
    @Test void explicitActorCannotReadAProjectOutsideItsCurrentScope() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of()));
        assertThrows(ServiceException.class,()->api.getContractNumbers(new ProjectDeviceSelectionContextApi.Authorization(1L,42L,10L)));
        verifyNoInteractions(mapper,contracts);
    }
}
