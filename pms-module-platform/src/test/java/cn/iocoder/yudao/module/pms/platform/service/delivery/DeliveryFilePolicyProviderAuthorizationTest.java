package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryFilePolicyProviderAuthorizationTest {
    @Test void existingExtensionCatalogProducesStrictUploadMimeTypes() {
        var catalog=mock(DeliveryCatalogService.class);var access=mock(DeliveryOwnerAccess.class);
        var type=new DeliveryTypeDO();type.setTypeCode("IMPLEMENTATION_PLAN");type.setCategory("DOCUMENT");type.setMaxSizeBytes(1024L);
        when(catalog.requireEnabledType("IMPLEMENTATION_PLAN")).thenReturn(type);
        when(catalog.allowedMedia(type)).thenReturn(List.of("pdf","html","jpeg","png"));
        when(access.require(anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyString(),anyBoolean(),anyBoolean(),nullable(Long.class))).thenReturn(3L);
        var provider=new DeliveryFilePolicyProvider(catalog,List.of());
        org.springframework.test.util.ReflectionTestUtils.setField(provider,"ownerAccess",access);
        var fact=provider.inspect(new FileBusinessObjectPolicyQuery(1L,7L,"PLT","DELIVERY_MATERIAL","SOL:solution:42","IMPLEMENTATION_PLAN","key",FileActionCodes.UPLOAD,null));
        assertEquals(java.util.Set.of("application/pdf","text/html","image/jpeg","image/png"),fact.allowedMediaTypes());
        assertEquals(3L,fact.scopeVersion());
    }

    @Test
    void catalogCodeCollisionCannotBypassOwnerScope() {
        var catalog = mock(DeliveryCatalogService.class);
        var owner = mock(DeliveryMaterialUploadPolicyValidator.class);
        DeliveryTypeDO type = new DeliveryTypeDO();
        type.setTypeCode("RECEIPT"); type.setCategory("DOCUMENT"); type.setVersion(1);
        type.setMaxSizeBytes(1024L);
        when(catalog.findEnabledType("RECEIPT")).thenReturn(type);
        lenient().when(catalog.allowedMedia(type)).thenReturn(List.of("application/pdf"));
        lenient().when(owner.ownerModule()).thenReturn("ACC");
        when(owner.supportsEntityType("project_deliverable")).thenReturn(true);
        lenient().when(owner.validateUpload(eq(1L),eq(7L),eq("project_deliverable"),eq("42"),
                eq("RECEIPT"),eq(FileActionCodes.UPLOAD),eq(false),isNull()))
                .thenThrow(new BusinessContractException("DELIVERY_UPLOAD_DENIED", "project outside scope"));
        var provider = new DeliveryFilePolicyProvider(catalog,List.of(owner));
        assertThrows(BusinessContractException.class, () -> provider.inspect(new FileBusinessObjectPolicyQuery(
                1L,7L,"PLT","DELIVERY_MATERIAL","ACC:project_deliverable:42","RECEIPT","key",
                FileActionCodes.UPLOAD,null)));
        verify(owner).validateUpload(1L,7L,"project_deliverable","42","RECEIPT",FileActionCodes.UPLOAD,false,null);
    }
}
