package cn.iocoder.yudao.module.pms.platform.service.delivery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DeliveryCatalogFileAuthorizationTest {
    DeliveryCatalogService catalog=mock(DeliveryCatalogService.class);
    DeliveryOwnerAccess owner=mock(DeliveryOwnerAccess.class);
    DeliveryFilePolicyProvider provider=new DeliveryFilePolicyProvider(catalog,List.of());
    DeliveryCatalogFileAuthorizationTest() {ReflectionTestUtils.setField(provider,"ownerAccess",owner);}
    FileBusinessObjectPolicyQuery query(String action) {
        return new FileBusinessObjectPolicyQuery(1L,7L,"PLT","DELIVERY_MATERIAL","PRJ:project:42","RECEIPT","key",action,null);
    }
    DeliveryTypeDO type() {
        var row=new DeliveryTypeDO();row.setTypeCode("RECEIPT");row.setEnabled(false);row.setCategory("DOCUMENT");row.setMaxSizeBytes(1024L);row.setVersion(1);
        when(catalog.allowedMedia(row)).thenReturn(List.of("application/pdf"));return row;
    }
    @Test void disabledTypeKeepsAuthorizedExistingFileReadable() {
        when(owner.require(1L,7L,"PRJ","project",42L,"RECEIPT",false,false,null)).thenReturn(5L);
        var row=type();when(catalog.requireType("RECEIPT")).thenReturn(row);
        assertTrue(provider.inspect(query(FileActionCodes.READ)).allowed());
        verify(catalog,never()).requireEnabledType(anyString());
    }
    @Test void enabledCatalogCannotGrantUploadToForeignOwner() {
        when(owner.require(1L,7L,"PRJ","project",42L,"RECEIPT",true,false,null)).thenThrow(new BusinessContractException("DELIVERY_ACCESS_DENIED","scope"));
        assertThrows(BusinessContractException.class,()->provider.inspect(query(FileActionCodes.UPLOAD)));
        verifyNoInteractions(catalog);
    }
    @Test void uploadCompletionRechecksOwnerWithExpectedScopeUnderLock() {
        when(owner.require(1L,7L,"PRJ","project",42L,"RECEIPT",true,true,5L)).thenThrow(new BusinessContractException("DELIVERY_ACCESS_DENIED","changed scope"));
        var complete=new FileBusinessObjectPolicyRevalidationQuery(1L,7L,"PLT","DELIVERY_MATERIAL","PRJ:project:42","RECEIPT","key",FileActionCodes.UPLOAD,5L,null);
        assertThrows(BusinessContractException.class,()->provider.lockAndRevalidate(complete));
        verify(owner).require(1L,7L,"PRJ","project",42L,"RECEIPT",true,true,5L);
    }
    @Test void completionTakesCurrentCatalogRowLockAndAppliesChangedConstraints() {
        when(owner.require(1L,7L,"PRJ","project",42L,"RECEIPT",true,true,5L)).thenReturn(5L);
        var row=type();row.setEnabled(true);row.setVersion(2);row.setMaxSizeBytes(50L);
        when(catalog.lockEnabledType("RECEIPT")).thenReturn(row);
        var complete=new FileBusinessObjectPolicyRevalidationQuery(1L,7L,"PLT","DELIVERY_MATERIAL","PRJ:project:42","RECEIPT","key",FileActionCodes.UPLOAD,5L,null);
        assertEquals(50L,provider.lockAndRevalidate(complete).maxSizeBytes());
        verify(catalog).lockEnabledType("RECEIPT");verify(catalog,never()).requireEnabledType(anyString());
    }
    @Test void completionRejectsTypeDisabledSinceInitialization() {
        when(owner.require(1L,7L,"PRJ","project",42L,"RECEIPT",true,true,5L)).thenReturn(5L);
        when(catalog.lockEnabledType("RECEIPT")).thenThrow(new BusinessContractException("DELIVERY_TYPE_DISABLED","disabled"));
        var complete=new FileBusinessObjectPolicyRevalidationQuery(1L,7L,"PLT","DELIVERY_MATERIAL","PRJ:project:42","RECEIPT","key",FileActionCodes.UPLOAD,5L,null);
        assertThrows(BusinessContractException.class,()->provider.lockAndRevalidate(complete));
    }

}
