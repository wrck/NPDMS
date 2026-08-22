package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApiImpl;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.AddressDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.LocationSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.AddressMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.LocationSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetLocationApiImplTest {

    @Mock private AddressMapper addressMapper;
    @Mock private SiteMapper siteMapper;
    @Mock private LocationSourceMappingMapper sourceMappingMapper;
    @Mock private SiteLocationTreeService treeService;
    @Mock private AreaDepartmentMappingService mappingService;

    private AssetLocationApiImpl api;

    @BeforeEach
    void setUp() {
        api = new AssetLocationApiImpl(addressMapper, siteMapper, sourceMappingMapper, treeService, mappingService);
    }

    @Test
    void shouldAllowMultipleSitesToReuseOneAddress() {
        AddressDO storedAddress = address(11L, 0);
        doAnswer(invocation -> {
            AddressDO value = invocation.getArgument(0);
            value.setId(11L);
            return 1;
        }).when(addressMapper).insert(any(AddressDO.class));
        when(addressMapper.selectById(11L)).thenReturn(storedAddress);
        AtomicLong siteId = new AtomicLong(20L);
        doAnswer(invocation -> {
            SiteDO value = invocation.getArgument(0);
            value.setId(siteId.incrementAndGet());
            return 1;
        }).when(siteMapper).insert(any(SiteDO.class));

        LocationReferenceDTO first = api.maintain(new LocationMaintenanceCommand(1L,
                new AddressInput(null, null, "CN", "中国", "330000", "浙江省", "330100", "杭州市",
                        "330106", "西湖区", "文三路", "浙江省杭州市西湖区文三路", null, null, null, null),
                new SiteInput(null, null, "SITE-A", "站点A", null, "CUSTOMER_SITE"),
                null, null, null, null, null));
        LocationReferenceDTO second = api.maintain(new LocationMaintenanceCommand(1L,
                new AddressInput(11L, 0, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                new SiteInput(null, null, "SITE-B", "站点B", null, "CUSTOMER_SITE"),
                null, null, null, null, null));

        assertEquals(11L, first.addressId());
        assertEquals(11L, second.addressId());
        assertNotEquals(first.siteId(), second.siteId());
        verify(addressMapper, times(1)).insert(any(AddressDO.class));
    }

    @Test
    void shouldRejectStaleAddressVersion() {
        when(addressMapper.selectById(11L)).thenReturn(address(11L, 2));
        assertThrows(ServiceException.class, () -> api.getAddress(11L, 1));
    }

    @Test
    void shouldReplaySameSourceVersionAndRejectDifferentReferences() {
        LocationSourceMappingDO mapping = new LocationSourceMappingDO();
        mapping.setId(31L);
        mapping.setSourceVersion("v3");
        mapping.setAddressId(11L);
        mapping.setSiteId(21L);
        mapping.setLocationResolutionStatus("RESOLVED");
        mapping.setVersion(0);
        when(sourceMappingMapper.selectBySourceKey("PMS", "SURVEY", "S-1")).thenReturn(mapping);
        when(addressMapper.selectById(11L)).thenReturn(address(11L, 0));
        SiteDO site = new SiteDO();
        site.setId(21L);
        site.setAddressId(11L);
        site.setStatus(0);
        site.setVersion(0);
        when(siteMapper.selectById(21L)).thenReturn(site);

        LocationReferenceDTO replay = api.maintain(new LocationMaintenanceCommand(1L,
                new AddressInput(11L, 0, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                new SiteInput(21L, 0, null, null, null, null), null, null, "SURVEY", "S-1", "v3"));
        assertEquals(21L, replay.siteId());

        assertThrows(ServiceException.class, () -> api.maintain(new LocationMaintenanceCommand(1L,
                new AddressInput(12L, 0, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                new SiteInput(21L, 0, null, null, null, null), null, null, "SURVEY", "S-1", "v3")));
    }

    private AddressDO address(Long id, int version) {
        AddressDO address = new AddressDO();
        address.setId(id);
        address.setStatus(0);
        address.setVersion(version);
        return address;
    }

}
