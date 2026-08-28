package cn.iocoder.yudao.module.pms.customer.service.location;

import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.location.CustomerLocationReferenceDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.location.CustomerLocationReferenceMapper;
import cn.iocoder.yudao.module.pms.customer.service.location.command.CustomerLocationCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerLocationReferenceServiceTest {

    @Mock AssetLocationApi assetLocationApi;
    @Mock CustomerMasterMapper customerMasterMapper;
    @Mock CustomerLocationReferenceMapper locationReferenceMapper;
    @InjectMocks CustomerLocationReferenceService service;

    @Test
    void replacesCurrentAddressReferenceAfterAstValidation() {
        when(customerMasterMapper.selectById(100L)).thenReturn(customer());
        when(assetLocationApi.getAddress(200L, 3)).thenReturn(new AddressRespDTO(
                200L, null, null, null, null, null, null, null, null, null, null,
                null, null, 0, 3));
        CustomerLocationReferenceDO current = reference("ADDRESS", 199L, 2);
        when(locationReferenceMapper.selectCurrent(any())).thenReturn(current);

        CustomerLocationReferenceDO result = service.maintain(
                new CustomerLocationCommand(1L, 100L, "ADDRESS", 200L, 3, "location-key"));

        verify(locationReferenceMapper).updateById(current);
        ArgumentCaptor<CustomerLocationReferenceDO> inserted = ArgumentCaptor.forClass(CustomerLocationReferenceDO.class);
        verify(locationReferenceMapper).insert(inserted.capture());
        assertEquals(200L, result.getLocationId());
        assertEquals(3, result.getSourceVersion());
    }

    @Test
    void acceptsSiteReference() {
        when(customerMasterMapper.selectById(100L)).thenReturn(customer());
        when(assetLocationApi.getSite(300L, 4)).thenReturn(new SiteRespDTO(
                300L, "SITE-1", "现场", 100L, 200L, "DELIVERY", 0, 4));

        CustomerLocationReferenceDO result = service.maintain(
                new CustomerLocationCommand(1L, 100L, "SITE", 300L, 4, "location-key"));

        assertEquals("SITE", result.getLocationType());
        verify(locationReferenceMapper).insert(any(CustomerLocationReferenceDO.class));
    }

    @Test
    void rejectsSiteLocationTypeWithoutCallingAst() {
        assertThrows(IllegalArgumentException.class, () -> service.maintain(
                new CustomerLocationCommand(1L, 100L, "SITE_LOCATION", 400L, 1, "location-key")));

        verify(assetLocationApi, never()).getSiteLocation(any(), any());
    }

    @Test
    void rejectsCrossTenantCustomer() {
        CustomerMasterDO customer = customer();
        customer.setTenantId(2L);
        when(customerMasterMapper.selectById(100L)).thenReturn(customer);

        assertThrows(IllegalArgumentException.class, () -> service.maintain(
                new CustomerLocationCommand(1L, 100L, "ADDRESS", 200L, 3, "location-key")));

        verify(assetLocationApi, never()).getAddress(any(), any());
    }

    private CustomerMasterDO customer() {
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setId(100L);
        customer.setTenantId(1L);
        return customer;
    }

    private CustomerLocationReferenceDO reference(String type, Long id, int version) {
        CustomerLocationReferenceDO reference = new CustomerLocationReferenceDO();
        reference.setId(10L);
        reference.setTenantId(1L);
        reference.setCustomerId(100L);
        reference.setLocationType(type);
        reference.setLocationId(id);
        reference.setSourceVersion(version);
        reference.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        return reference;
    }
}
