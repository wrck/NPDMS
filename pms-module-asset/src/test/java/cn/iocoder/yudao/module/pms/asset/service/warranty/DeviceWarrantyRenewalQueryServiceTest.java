package cn.iocoder.yudao.module.pms.asset.service.warranty;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceWarrantyRenewalQueryServiceTest {
    DeviceWarrantyRenewalRecordMapper renewals = mock(DeviceWarrantyRenewalRecordMapper.class);
    DeviceMapper devices = mock(DeviceMapper.class);
    DeviceAccessScopeService deviceScope = mock(DeviceAccessScopeService.class);
    ProjectScopeApi projectScope = mock(ProjectScopeApi.class);
    ProjectContractQueryApi contracts = mock(ProjectContractQueryApi.class);
    DeviceWarrantyRenewalQueryService service = new DeviceWarrantyRenewalQueryService(renewals, devices, deviceScope, projectScope, contracts);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void close() { TenantContextHolder.clear(); }
    DeviceWarrantyRenewalQueryService.Query query() { return new DeviceWarrantyRenewalQueryService.Query(1L, 7L, 55L, null, "SN", 2, 20); }
    @Test void projectPageUsesContractShipmentScopeWithoutResolvingDeviceMaster() {
        when(projectScope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(55L, 1L, Set.of(55L), Set.of()));
        when(contracts.getWarrantyShipmentContractNumbers(55L)).thenReturn(Set.of("C-1"));
        service.page(query());
        verify(renewals).count(new DeviceWarrantyRenewalRecordMapper.PageQuery(1L, null, Set.of("C-1"), "SN", 20, 20));
        verifyNoInteractions(devices, deviceScope);
    }
    @Test void unauthorizedOrPlaceholderProjectCannotReadContractsOrRenewals() {
        when(projectScope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(55L, 1L, Set.of(), Set.of(55L)));
        assertThrows(AccessDeniedException.class, () -> service.page(query()));
        verifyNoInteractions(contracts, renewals);
    }
    @Test void emptyContractsCannotBroadenQuery() {
        when(projectScope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(55L, 1L, Set.of(55L), Set.of()));
        when(contracts.getWarrantyShipmentContractNumbers(55L)).thenReturn(Set.of());
        assertEquals(0, service.page(query()).getTotal());verifyNoInteractions(renewals);
    }
    @Test void crossTenantOrAmbiguousQueryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.page(new DeviceWarrantyRenewalQueryService.Query(2L, 7L, 55L, null, null, 1, 20)));
        assertThrows(IllegalArgumentException.class, () -> service.page(new DeviceWarrantyRenewalQueryService.Query(1L, 7L, 55L, 1L, null, 1, 20)));
        verifyNoInteractions(renewals, contracts, devices);
    }
}
