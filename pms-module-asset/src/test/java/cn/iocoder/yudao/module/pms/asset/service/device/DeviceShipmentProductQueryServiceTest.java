package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceShipmentProductQueryServiceTest {
    private final AssetProductOfficialMapper products = mock(AssetProductOfficialMapper.class);
    private final DeviceShipmentProductQueryService service = new DeviceShipmentProductQueryService(products);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    private DeviceShipmentDO shipment() {
        var row = new DeviceShipmentDO();row.setId(3L);row.setTenantId(1L);
        row.setDeviceSn("SN");row.setProductCode("PRIMARY");row.setSecondaryItem("SECONDARY");
        row.setInternalSerialNo("COMBINED");return row;
    }
    private AssetProductOfficialDO product(String code, Long tenant) {
        var row = new AssetProductOfficialDO();row.setProductCode(code);row.setTenantId(tenant);
        row.setId("PRIMARY".equals(code)?10L:11L);
        row.setProductName(code+" name");row.setProductModel(code+" model");return row;
    }
    @Test void formalNamesAndModelsComeFromTenantProductCodesAndShipmentFactsStayIntact() {
        when(products.selectShipmentProducts(any())).thenReturn(List.of(product("PRIMARY",1L),product("SECONDARY",1L)));
        var page = service.withDirectoryLabels(1L,new PageResult<>(List.of(shipment()),21L));
        var row = page.getList().getFirst();assertEquals(21L,page.getTotal());
        assertEquals("PRIMARY name",row.getProductName());assertEquals("PRIMARY model",row.getProductModel());
        assertEquals("SECONDARY name",row.getSecondaryProductName());assertEquals("SECONDARY model",row.getSecondaryProductModel());
        assertEquals("PRIMARY",row.getProductCode());assertEquals("SECONDARY",row.getSecondaryItem());
        assertEquals("COMBINED",row.getInternalSerialNo());assertEquals("SN",row.getDeviceSn());
        verify(products).selectShipmentProducts(argThat(query -> query.tenantId()==1L
                && query.productCodes().equals(java.util.Set.of("PRIMARY","SECONDARY"))));
    }
    @Test void emptyPageOrMissingCodesDoesNotReadTheWholeProductCatalogue() {
        assertTrue(service.withDirectoryLabels(1L,new PageResult<DeviceShipmentDO>(List.of(),0L)).getList().isEmpty());
        var row=shipment();row.setProductCode(null);row.setSecondaryItem(null);
        assertNull(service.withDirectoryLabels(1L,new PageResult<>(List.of(row),1L)).getList().getFirst().getProductName());
        verifyNoInteractions(products);
    }
    @Test void approvedTrimOnlyAppliesToTheCatalogJoinAndKeepsOriginalShipmentCode() {
        var row=shipment();row.setProductCode(" PRIMARY ");row.setSecondaryItem(null);
        when(products.selectShipmentProducts(any())).thenReturn(List.of(product("PRIMARY",1L)));
        var response=service.withDirectoryLabels(1L,new PageResult<>(List.of(row),1L)).getList().getFirst();
        assertEquals(" PRIMARY ",response.getProductCode());assertEquals("PRIMARY name",response.getProductName());
        verify(products).selectShipmentProducts(argThat(query -> query.productCodes().equals(java.util.Set.of("PRIMARY"))));
    }
    @Test void wrongTenantStopsBeforeDirectoryLookup() {
        assertThrows(IllegalArgumentException.class,()->service.withDirectoryLabels(2L,new PageResult<>(List.of(shipment()),1L)));
        var row=shipment();row.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.withDirectoryLabels(1L,new PageResult<>(List.of(row),1L)));
        verifyNoInteractions(products);
    }
    @Test void duplicateOrCrossTenantProductIsNeverSelectedArbitrarily() {
        when(products.selectShipmentProducts(any())).thenReturn(List.of(product("PRIMARY",1L),product("PRIMARY",1L)));
        assertThrows(IllegalStateException.class,()->service.withDirectoryLabels(1L,new PageResult<>(List.of(shipment()),1L)));
        when(products.selectShipmentProducts(any())).thenReturn(List.of(product("PRIMARY",2L)));
        assertThrows(IllegalStateException.class,()->service.withDirectoryLabels(1L,new PageResult<>(List.of(shipment()),1L)));
    }
}
