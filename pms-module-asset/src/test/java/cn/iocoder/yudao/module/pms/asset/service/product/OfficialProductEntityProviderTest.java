package cn.iocoder.yudao.module.pms.asset.service.product;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfficialProductEntityProviderTest {
    final AssetProductOfficialMapper products=mock(AssetProductOfficialMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final OfficialProductEntityProvider provider=new OfficialProductEntityProvider(products,permissions);
    final EntityDataRef target=EntityDataRef.current(new EntityRef(1L,"AST","ProductOfficial",10L));
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    AssetProductOfficialDO product() {
        var row=new AssetProductOfficialDO();row.setId(10L);row.setTenantId(1L);row.setProductCode("CODE");row.setVersion(3L);return row;
    }
    @Test void trustedObserverCanReadButCannotWriteOrAccessAnotherTenant() {
        var observer=new EntityActor(1L,0L,EntityActor.SYSTEM_OBSERVER);when(products.selectById(10L)).thenReturn(product());
        assertEquals(3L,provider.concurrencyBasis(target,observer));
        assertThrows(IllegalArgumentException.class,()->provider.lockForWrite(target,observer,3L));
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->provider.requireReadable(target,observer));
        verify(products,never()).selectByProductCodeForUpdate(any());verifyNoInteractions(permissions);
    }
    @Test void ordinaryReadNeedsAnExistingCatalogOrIntegrationPermission() {
        var actor=new EntityActor(1L,7L,"read");assertThrows(IllegalArgumentException.class,()->provider.requireReadable(target,actor));
        verifyNoInteractions(products);
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);when(products.selectById(10L)).thenReturn(product());
        assertDoesNotThrow(()->provider.requireReadable(target,actor));
    }
    @Test void lockingRejectsAConcurrentVersionChangeBeforeThePlatformWritesValues() {
        var actor=new EntityActor(1L,7L,"write");when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        var observed=product();var locked=product();locked.setVersion(4L);
        when(products.selectById(10L)).thenReturn(observed);when(products.selectByProductCodeForUpdate(any())).thenReturn(locked);
        assertThrows(IllegalStateException.class,()->provider.lockForWrite(target,actor,3L));
        verify(products).selectByProductCodeForUpdate(argThat(query->query.tenantId()==1L && "CODE".equals(query.productCode())));
    }
    @Test void productExtensionDoesNotOfferInventedRevisionsOrResurrectDeletedProducts() {
        var observer=new EntityActor(1L,0L,EntityActor.SYSTEM_OBSERVER);
        assertThrows(IllegalArgumentException.class,()->provider.requireReadable(new EntityDataRef(target.entity(),99L),observer));
        var row=product();row.setDeleted(true);when(products.selectById(10L)).thenReturn(row);
        assertThrows(IllegalArgumentException.class,()->provider.requireReadable(target,observer));
    }
}
