package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultBusinessDeliveryFilePolicyTest {
    final DeliveryTypeMapper types=mock(DeliveryTypeMapper.class);
    final DeliveryCatalogService catalog=new DeliveryCatalogService(types,mock(DeliveryCapabilityConfigMapper.class));
    final DeclaredBusinessDeliveryBridge owners=mock(DeclaredBusinessDeliveryBridge.class);
    final BusinessCallerContext callers=mock(BusinessCallerContext.class);
    final DefaultBusinessDeliveryFilePolicy policy=new DefaultBusinessDeliveryFilePolicy(owners,callers,catalog);
    final DeliveryTypeDO plan=new DeliveryTypeDO();

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(7L);
        when(callers.require()).thenReturn(new AbstractBusinessApplicationService.ResolvedCaller(7L,9L,null));
        when(owners.requireDefault(eq(7L),eq(9L),eq("SOL"),eq("solution"),eq(42L),anyBoolean(),anyBoolean(),nullable(Long.class))).thenReturn(4L);
        when(owners.projectId(7L,"SOL","solution",42L)).thenReturn(99L);
        plan.setTypeCode("IMPLEMENTATION_PLAN");plan.setCategory("工程交付");plan.setEnabled(true);
        plan.setAllowedMediaJson("[\"pdf\",\"html\"]");plan.setMaxSizeBytes(1024L);
        when(types.selectByCode("IMPLEMENTATION_PLAN")).thenReturn(Optional.of(plan));
        when(types.selectCodeForUpdate(any())).thenReturn(plan);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    FileBusinessObjectPolicyQuery query(String purpose,String action) {
        return new FileBusinessObjectPolicyQuery(7L,9L,"PLT","DEFAULT_BUSINESS_DELIVERY","99:SOL:solution:42",purpose,"export",action,null);
    }

    @Test void registeredTypeUsesItsOwnMimeAndSizeLimitsForRealContentValidation() {
        var fact=policy.inspect(query("IMPLEMENTATION_PLAN",FileActionCodes.UPLOAD));
        assertEquals(Set.of("text/html","application/pdf"),fact.allowedMediaTypes());
        assertEquals(Set.of("工程交付"),fact.allowedCategoryCodes());assertEquals(1024L,fact.maxSizeBytes());
        assertEquals(4L,fact.scopeVersion());
        var content=new FileContentPolicyService(new BoundedMultipartReader(),List.of(),false);
        byte[] html="<!doctype html><html><head><title>Plan</title></head><body>Approved scope</body></html>".getBytes(StandardCharsets.UTF_8);
        assertEquals("text/html",content.validateBounded(new BoundedFileContentValidationCommand(html,"plan.html",html.length,"text/html",null,fact)).mediaType());
        assertThrows(RuntimeException.class,()->content.validateBounded(new BoundedFileContentValidationCommand(html,"plan.txt",html.length,"text/plain",null,fact)));
        byte[] tooLarge=new byte[1025];
        assertThrows(RuntimeException.class,()->content.validateBounded(new BoundedFileContentValidationCommand(tooLarge,"plan.html",tooLarge.length,"text/html",null,fact)));
    }

    @Test void disabledTypesRejectNewUploadsAndReferencesButKeepHistoryReadable() {
        plan.setEnabled(false);
        for(String action:List.of(FileActionCodes.UPLOAD,FileActionCodes.REFERENCE)) {
            assertEquals("DELIVERY_TYPE_DISABLED",assertThrows(BusinessContractException.class,()->policy.inspect(query("IMPLEMENTATION_PLAN",action))).getErrorCode());
        }
        assertTrue(policy.inspect(query("IMPLEMENTATION_PLAN",FileActionCodes.DOWNLOAD)).allowed());
        var read=policy.inspect(query("ATTACHMENT",FileActionCodes.UPLOAD));
        assertEquals(Set.of("DOCUMENT"),read.allowedCategoryCodes());assertFalse(read.allowedMediaTypes().contains("text/html"));
    }

    @Test void directOwnerPolicyLocksCatalogAndCannotFallbackAfterTypeIsDisabled() {
        var direct=mock(DirectBusinessOwners.class);
        doReturn(Optional.of(mock(ProjectBusinessService.class))).when(direct).byIdentity("SOL","solution");
        when(direct.require(7L,9L,"SOL","solution",42L,true,true,4L)).thenReturn(new DirectBusinessOwners.Access(99L,4L));
        @SuppressWarnings("unchecked") ObjectProvider<DirectBusinessOwners> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(direct);ReflectionTestUtils.setField(policy,"directOwners",provider);
        var request=new FileBusinessObjectPolicyRevalidationQuery(7L,9L,"PLT","DEFAULT_BUSINESS_DELIVERY","99:SOL:solution:42","IMPLEMENTATION_PLAN","export",FileActionCodes.UPLOAD,4L,null);
        assertTrue(policy.lockAndRevalidate(request).allowed());
        verify(types).selectCodeForUpdate(argThat(value->value.tenantId().equals(7L)&&value.typeCode().equals("IMPLEMENTATION_PLAN")));
        plan.setEnabled(false);
        assertEquals("DELIVERY_TYPE_DISABLED",assertThrows(BusinessContractException.class,()->policy.lockAndRevalidate(request)).getErrorCode());
        verifyNoInteractions(owners);
    }

    @Test void catalogPermissionNeverReplacesEntityScopeAndUploadUsesCatalogCategory() {
        when(owners.projectId(7L,"SOL","solution",42L)).thenReturn(100L);
        assertThrows(BusinessContractException.class,()->policy.inspect(query("IMPLEMENTATION_PLAN",FileActionCodes.UPLOAD)));
        verifyNoInteractions(types);
        when(owners.projectId(7L,"SOL","solution",42L)).thenReturn(99L);
        var models=mock(BusinessModelCatalog.class);var model=mock(BusinessModelDescriptor.class);
        when(model.ownerModule()).thenReturn("SOL");when(model.entityType()).thenReturn("solution");
        when(models.findByStableCode("SOL_SOLUTION")).thenReturn(Optional.of(model));
        var uploads=mock(FileUploadApplicationService.class);var stop=new IllegalStateException("stop before storage");
        doThrow(stop).when(uploads).initialize(any());
        var service=new DefaultBusinessDeliveryService(models,mock(BusinessAccessGuard.class),callers,owners,mock(ProjectScopeApi.class),uploads,mock(FileEvidenceApi.class),mock(DeliveryMaterialMapper.class),catalog);
        assertSame(stop,assertThrows(IllegalStateException.class,()->service.upload(new Scope(99L,"SOL_SOLUTION","42","IMPLEMENTATION_PLAN"),new MockMultipartFile("file","plan.html","text/html","<html></html>".getBytes(StandardCharsets.UTF_8)),"export")));
        verify(uploads).initialize(argThat(value->"工程交付".equals(value.categoryCode())));
    }
}
