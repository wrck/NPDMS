package cn.iocoder.yudao.module.pms.project.service.projectselection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceShipmentApi;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactApi;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSelectionQueryServiceTest {
    private final ProjectBusinessContextApi projects = mock(ProjectBusinessContextApi.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectDeviceShipmentApi shipments = mock(ProjectDeviceShipmentApi.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<CustomerServiceLevelFactApi> provider = mock(ObjectProvider.class);
    private final CustomerServiceLevelFactApi customer = mock(CustomerServiceLevelFactApi.class);
    private final ProjectSelectionQueryService service = new ProjectSelectionQueryService(projects, scopes, shipments, provider);
    private final ProjectSelectionQueryService.Actor actor = new ProjectSelectionQueryService.Actor(7L, 13L);
    private final ProjectSelectionQueryService.ProjectQuery query = new ProjectSelectionQueryService.ProjectQuery(actor, 29L);
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(13L);
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @Test void optionsUseCurrentActorViewContractAndReturnEmptyWithoutFallback() {
        when(projects.searchOptions(any())).thenReturn(List.of());
        assertTrue(service.options(new ProjectSelectionQueryService.OptionsQuery(actor, "项目")).isEmpty());
        verify(projects).searchOptions(new ProjectBusinessContextApi.OptionsQuery(7L, 13L, "项目"));
        verifyNoInteractions(scopes, shipments, provider);
    }

    @Test void rejectsSpoofedTenantAndActorBeforeOwnerCalls() {
        assertThrows(ServiceException.class, () -> service.options(new ProjectSelectionQueryService.OptionsQuery(
                new ProjectSelectionQueryService.Actor(8L, 13L), "")));
        assertThrows(ServiceException.class, () -> service.options(new ProjectSelectionQueryService.OptionsQuery(
                new ProjectSelectionQueryService.Actor(7L, 14L), "")));
        verifyNoInteractions(projects, scopes, shipments, provider);
    }

    @Test void rejectsForbiddenProjectBeforeBackgroundOrDevicesAreRead() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(), Set.of()));
        assertThrows(ServiceException.class, () -> service.context(query));
        assertThrows(ServiceException.class, () -> service.devices(
                new ProjectSelectionQueryService.DevicePageQuery(actor, 29L, "SN", 1, 20)));
        verifyNoInteractions(projects, shipments, provider);
    }

    @Test void devicesReuseAssetQueryAfterViewAuthorizationWithoutEditOrConsumerPermissions() {
        scope();
        var page = new ProjectDeviceShipmentApi.DevicePage(List.of(new ProjectDeviceShipmentApi.Device(101L, "SN-101", "P", "M")), 1);
        when(shipments.pageDevices(any())).thenReturn(page);
        assertSame(page, service.devices(new ProjectSelectionQueryService.DevicePageQuery(actor, 29L, "SN", 2, 20)));
        var order = inOrder(scopes, shipments);
        order.verify(scopes).resolveCurrent(argThat(q -> ProjectScopeApi.ACTION_VIEW.equals(q.actionCode())));
        order.verify(shipments).pageDevices(new ProjectDeviceShipmentApi.DeviceQuery(7L, 13L, 29L, "SN", 2, 20));
        verifyNoInteractions(projects, provider);
    }

    @Test void backgroundAndCustomerLevelComeFromTheirOwnersAtAuthorizedScopeVersion() {
        background();
        when(provider.getIfAvailable()).thenReturn(customer);
        var fact = new CustomerServiceLevelFact(CustomerServiceLevelFact.Status.AVAILABLE, 7L, 67L, 71L,
                "LEVEL_1", 1L, LocalDateTime.of(2026, 10, 1, 0, 0), null);
        when(customer.inspectCurrent(any())).thenReturn(new CustomerServiceLevelFactResult(CustomerServiceLevelFactResult.Decision.AVAILABLE, fact));
        var result = service.context(query);
        assertEquals("LEVEL_1", result.customerServiceLevel());
        assertEquals("P-29", result.project().projectCode());
        verify(projects).presentation(new ProjectBusinessContextApi.Query(7L, 13L, 29L, 41L));
        verify(customer).inspectCurrent(new CustomerServiceLevelFactQuery(7L, 67L));
    }

    @Test void unavailableCustomerProviderNeverFabricatesConfiguredLevel() {
        background();
        assertNull(service.context(query).customerServiceLevel());
        when(provider.getIfAvailable()).thenReturn(customer);
        when(customer.inspectCurrent(any())).thenThrow(new CustomerServiceLevelFactException(
                CustomerServiceLevelFactException.Code.PROVIDER_UNAVAILABLE, "unavailable"));
        assertNull(service.context(query).customerServiceLevel());
    }

    @Test void customerIntegrityFailuresAreNotHiddenAsDefaultLevel() {
        background(); when(provider.getIfAvailable()).thenReturn(customer);
        var failure = new CustomerServiceLevelFactException(CustomerServiceLevelFactException.Code.OWNER_DATA_CORRUPTED, "corrupt");
        when(customer.inspectCurrent(any())).thenThrow(failure);
        assertSame(failure, assertThrows(CustomerServiceLevelFactException.class, () -> service.context(query)));
    }

    private void scope() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(29L), Set.of()));
    }
    private void background() {
        scope();
        when(projects.inspect(any())).thenReturn(ProjectBusinessContextApi.Context.builder().tenantId(7L)
                .projectId(29L).customerId(67L).build());
        when(projects.presentation(any())).thenReturn(ProjectBusinessContextApi.Presentation.builder()
                .projectId(29L).projectCode("P-29").projectName("项目29").build());
    }
}
