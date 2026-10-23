package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.location.CustomerLocationReferenceDO;
import cn.iocoder.yudao.module.pms.customer.service.location.CustomerLocationReferenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectCustomerLocationServiceTest {
    private final ProjectCustomerOverviewService overview = mock(ProjectCustomerOverviewService.class);
    private final CustomerLocationReferenceService references = mock(CustomerLocationReferenceService.class);
    private final AssetLocationApi assets = mock(AssetLocationApi.class);
    private final ProjectCustomerLocationService service = new ProjectCustomerLocationService(overview, references, assets);
    private final ProjectCustomerOverviewService.Query project = new ProjectCustomerOverviewService.Query(1L, 31L, 100L);
    private final CustomerSiteQuery sites = new CustomerSiteQuery(1L, 200L);

    private void customer() {
        var customer = new CustomerRespVO(); customer.setId(200L); customer.setName("项目客户");
        when(overview.get(project)).thenReturn(customer);
    }

    private SiteRespDTO site(Long customerId) {
        return new SiteRespDTO(21L, "S-21", "站点", customerId, 11L, "CUSTOMER_SITE", 0, 2L);
    }

    private AddressRespDTO address() {
        return new AddressRespDTO(11L, "CN", "中国", null, null, null, null, null, null,
                "现场地址", "中国现场地址", null, null, 0, 3L);
    }

    @Test
    void readsOnlyAssociatedCustomerSitesAndDeduplicatesItsAddressReferences() {
        customer(); when(assets.getCustomerSites(sites)).thenReturn(List.of(site(200L)));
        when(assets.getAddress(11L, null)).thenReturn(address());
        var ref = new CustomerLocationReferenceDO(); ref.setTenantId(1L); ref.setCustomerId(200L);
        ref.setLocationType("ADDRESS"); ref.setLocationId(11L);
        when(references.listCurrent(1L, 200L)).thenReturn(List.of(ref));
        var result = service.get(new ProjectCustomerLocationService.Query(1L, 31L, 100L, null));
        assertEquals(200L, result.customerId()); assertEquals(List.of(site(200L)), result.sites());
        assertEquals(List.of(address()), result.addresses()); assertEquals(List.of(address()), result.customerAddresses());
        verify(assets, times(1)).getAddress(11L, null); verify(assets, never()).getLocationTree(any());
    }

    @Test
    void deniedProjectCannotReachAnyLocationProvider() {
        when(overview.get(project)).thenThrow(new SecurityException("project scope denied"));
        assertThrows(SecurityException.class, () -> service.get(new ProjectCustomerLocationService.Query(1L,31L,100L,null)));
        verifyNoInteractions(assets, references);
    }

    @Test
    void unassociatedProjectReturnsNoGlobalChoices() {
        var result = service.get(new ProjectCustomerLocationService.Query(1L,31L,100L,null));
        assertNull(result.customerId()); assertTrue(result.sites().isEmpty()); assertTrue(result.addresses().isEmpty());
        assertThrows(ServiceException.class, () -> service.get(new ProjectCustomerLocationService.Query(1L,31L,100L,21L)));
        verifyNoInteractions(assets, references);
    }

    @Test
    void foreignSiteIsRejectedBeforeAddressOrTreeReads() {
        customer(); when(assets.getCustomerSites(sites)).thenReturn(List.of(site(200L)));
        assertThrows(ServiceException.class, () -> service.get(new ProjectCustomerLocationService.Query(1L,31L,100L,99L)));
        verify(assets, never()).getAddress(any(), any()); verify(assets, never()).getLocationTree(any());
        verifyNoInteractions(references);
    }

    @Test
    void customerMismatchFromProviderFailsClosed() {
        customer(); when(assets.getCustomerSites(sites)).thenReturn(List.of(site(201L)));
        assertThrows(ServiceException.class, () -> service.get(new ProjectCustomerLocationService.Query(1L,31L,100L,null)));
        verify(assets, never()).getAddress(any(), any()); verifyNoInteractions(references);
    }

    @Test
    void crossTenantCustomerReferenceFailsClosed() {
        customer(); var ref = new CustomerLocationReferenceDO(); ref.setTenantId(2L); ref.setCustomerId(200L);
        ref.setLocationType("ADDRESS"); ref.setLocationId(11L);
        when(references.listCurrent(1L,200L)).thenReturn(List.of(ref));
        assertThrows(ServiceException.class, () -> service.get(new ProjectCustomerLocationService.Query(1L,31L,100L,null)));
        verify(assets, never()).getAddress(any(), any());
    }

    @Test
    void returnsTreeOnlyForTheAuthorizedCustomerSite() {
        customer(); when(assets.getCustomerSites(sites)).thenReturn(List.of(site(200L)));
        when(assets.getAddress(11L,null)).thenReturn(address());
        var node = new SiteLocationRespDTO(31L,21L,null,"L-31","机房","ROOM","/31",1,0,0,1L);
        when(assets.getLocationTree(21L)).thenReturn(List.of(node));
        assertEquals(List.of(node), service.get(new ProjectCustomerLocationService.Query(1L,31L,100L,21L)).siteLocations());
    }

    @Test
    void mismatchedTreeSiteFailsClosed() {
        customer(); when(assets.getCustomerSites(sites)).thenReturn(List.of(site(200L)));
        when(assets.getAddress(11L,null)).thenReturn(address());
        when(assets.getLocationTree(21L)).thenReturn(List.of(new SiteLocationRespDTO(31L,99L,null,"L","他客机房","ROOM","/31",1,0,0,1L)));
        assertThrows(ServiceException.class, () -> service.get(new ProjectCustomerLocationService.Query(1L,31L,100L,21L)));
    }
}
