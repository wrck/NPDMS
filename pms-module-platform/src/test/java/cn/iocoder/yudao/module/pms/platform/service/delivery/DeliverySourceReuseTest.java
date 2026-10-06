package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeliverySourceReuseTest {
    DeliveryMaterialMapper materials;
    DeliveryFulfillmentMapper links;
    FileEvidenceApi files;
    DeliveryBusinessObjectEvidenceProvider provider;
    DeliveryMaterialService service;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        materials=mock(DeliveryMaterialMapper.class);links=mock(DeliveryFulfillmentMapper.class);
        files=mock(FileEvidenceApi.class);provider=mock(DeliveryBusinessObjectEvidenceProvider.class);
        service=new DeliveryMaterialService(materials,mock(DeliveryCatalogService.class),files,mock(DeliveryEventPublisher.class),List.of(provider));
        ReflectionTestUtils.setField(service,"fulfillmentService",new DeliveryFulfillmentService(links));
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    DeliveryRequirementDO requirement(){
        var r=new DeliveryRequirementDO();r.setId(902L);r.setTenantId(7L);r.setProjectId(99L);
        r.setOwnerModule("ACC");r.setEntityType("project_deliverable");r.setEntityId(99L);r.setTypeCode("REQ-B");return r;
    }
    DeliveryMaterialDO material(){
        var m=new DeliveryMaterialDO();m.setId(710L);m.setTenantId(7L);m.setProjectId(99L);
        m.setRequirementId(901L);m.setTypeCode("REQ-A");m.setStatus("ACTIVE");return m;
    }
    private void canonicalResultStore() {
        var store=new java.util.HashMap<String,DeliveryMaterialDO>();
        when(provider.supports(anyString())).thenReturn(true);
        when(provider.identity(eq(7L),eq(99L),anyString(),any())).thenAnswer(call -> new DeliveryBusinessObjectEvidenceProvider.Identity(
                "SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",call.getArgument(3)));
        when(materials.selectSourceIdentityForUpdate(any())).thenAnswer(call -> store.get(((DeliverySourceIdentityQuery)call.getArgument(0)).sourceIdentityKey()));
        when(materials.insert(any(DeliveryMaterialDO.class))).thenAnswer(call -> {
            DeliveryMaterialDO row=call.getArgument(0);row.setId(800L+store.size());store.put(row.getSourceIdentityKey(),row);return 1;
        });
    }
    @Test void nativeThenWrappedResultSharesMaterialAndPreservesOriginalRoot() {
        canonicalResultStore();
        var nativeRow=service.registerBusinessResult("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",9L,"approved",99L);
        var wrapped=service.registerTemplateFrozenBusinessResult(requirement(),"project_business_result","type|42|42|formed",9L,"approved");
        assertSame(nativeRow,wrapped);assertNull(wrapped.getRequirementId());assertEquals("SOL",wrapped.getOwnerModule());
        assertEquals("solution",wrapped.getSourceEntityType());assertEquals(42L,wrapped.getSourceEntityId());
        verify(materials,times(1)).insert(any(DeliveryMaterialDO.class));verify(links).insert(any(DeliveryFulfillmentDO.class));
    }
    @Test void wrappedThenNativeResultSharesMaterialAndPreservesTemplateHistory() {
        canonicalResultStore();
        var wrapped=service.registerTemplateFrozenBusinessResult(requirement(),"project_business_result","type|42|42|formed",9L,"approved");
        var nativeRow=service.registerBusinessResult("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",9L,"approved",99L);
        assertSame(wrapped,nativeRow);assertEquals(902L,nativeRow.getRequirementId());assertEquals("ACC",nativeRow.getOwnerModule());
        assertEquals("project_business_result",nativeRow.getBusinessObjectType());assertEquals("SOL",nativeRow.getSourceOwnerModule());
        assertEquals("IMPLEMENTATION_PLAN",nativeRow.getBusinessTypeCode());verify(materials,times(1)).insert(any(DeliveryMaterialDO.class));
    }
    @Test void nativeRegistrationReusesLegacyWrapperWithoutOverwritingItsStoredIdentity() {
        var original=material();original.setMaterialKind("BUSINESS_RESULT");original.setBusinessObjectType("project_business_result");
        original.setBusinessObjectId("type|42|42|formed");original.setBusinessRevisionNo(9L);original.setSourceIdentityKey("historical-wrapper-key");
        when(provider.supports("solution")).thenReturn(true);
        when(provider.identity(7L,99L,"42",9L)).thenReturn(new DeliveryBusinessObjectEvidenceProvider.Identity("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",9L));
        when(provider.aliases(7L,99L,"42",9L)).thenReturn(List.of(new DeliveryBusinessObjectEvidenceProvider.Alias("project_business_result","type|42|42|formed",9L)));
        when(materials.selectSourceBusiness(new DeliverySourceBusinessQuery(7L,99L,"project_business_result","type|42|42|formed",9L))).thenReturn(original);
        var row=service.registerBusinessResult("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",9L,"approved",99L);
        assertSame(original,row);assertEquals("historical-wrapper-key",row.getSourceIdentityKey());
        assertEquals("project_business_result",row.getBusinessObjectType());assertEquals("SOL",row.getSourceOwnerModule());
        verify(materials,never()).assignSourceIdentityIfMissing(any());verify(materials,never()).insert(any(DeliveryMaterialDO.class));
    }

    @Test void differentApprovedRevisionStillCreatesDistinctMaterial() {
        canonicalResultStore();
        var first=service.registerBusinessResult("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",9L,"approved",99L);
        var second=service.registerTemplateFrozenBusinessResult(requirement(),"project_business_result","type|42|42|later",10L,"approved");
        assertNotEquals(first.getId(),second.getId());assertNotEquals(first.getSourceIdentityKey(),second.getSourceIdentityKey());
        verify(materials,times(2)).insert(any(DeliveryMaterialDO.class));
    }

    @Test void fileVersionUsesOneMaterialAcrossDifferentRequirementCodesAndReferences(){
        var original=material();original.setMaterialKind("FILE");
        when(files.inspectDocument(7L,81L)).thenReturn(new FileEvidenceApi.Document(81L,"IMP","ARRIVAL_EVIDENCE","4","SIGNED","original-slot",70L,2,"sha","signed.pdf",true));
        when(materials.selectSourceFile(new DeliverySourceFileQuery(7L,99L,70L,2))).thenReturn(original);
        assertSame(original,service.registerTemplateFrozenDocument(requirement(),"arrival_acceptance",81L,"signed"));
        verify(materials,never()).insert(any(DeliveryMaterialDO.class));
        var captured=ArgumentCaptor.forClass(DeliveryFulfillmentDO.class);verify(links).insert(captured.capture());
        assertEquals(902L,captured.getValue().getRequirementId());assertEquals(710L,captured.getValue().getMaterialId());
        assertEquals(901L,original.getRequirementId());assertEquals("REQ-A",original.getTypeCode());
    }
    @Test void businessRevisionUsesOneMaterialAndStillRevalidatesOwnerEvidence(){
        var original=material();original.setMaterialKind("BUSINESS_RESULT");
        when(provider.supports("solution")).thenReturn(true);
        when(materials.selectSourceBusiness(new DeliverySourceBusinessQuery(7L,99L,"solution","42",9L))).thenReturn(original);
        assertSame(original,service.registerTemplateFrozenBusinessResult(requirement(),"solution","42",9L,"plan"));
        verify(provider).validateCurrent(7L,99L,"42",9L);
        verify(materials,never()).insert(any(DeliveryMaterialDO.class));
        verify(links).insert(any(DeliveryFulfillmentDO.class));
    }
    @Test void withdrawnSourceCannotBeReactivatedByRegisteringAnotherRequirement(){
        var original=material();original.setStatus("WITHDRAWN");
        when(files.inspectDocument(7L,81L)).thenReturn(new FileEvidenceApi.Document(81L,"IMP","ARRIVAL_EVIDENCE","4","SIGNED","slot",70L,2,"sha","signed.pdf",true));
        when(materials.selectSourceFile(new DeliverySourceFileQuery(7L,99L,70L,2))).thenReturn(original);
        assertThrows(BusinessContractException.class,()->service.registerTemplateFrozenDocument(requirement(),"arrival_acceptance",81L,"signed"));
        verify(links,never()).insert(any(DeliveryFulfillmentDO.class));
        assertEquals("WITHDRAWN",original.getStatus());
    }
    @Test void relationRejectsAnotherProjectsMaterialBeforeAnyWrite(){
        var original=material();original.setProjectId(100L);
        assertThrows(BusinessContractException.class,()->new DeliveryFulfillmentService(links).associate(requirement(),original));
        verifyNoInteractions(links);
    }
    private cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact projectionFact() {
        return new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact(
                70L,2,"original-slot","EVIDENCE","signed.pdf",3L,"application/pdf","sha",
                "AVAILABLE","ACTIVE",null,8L);
    }
    private void projectionDocument() {
        when(files.inspectDocumentByArtifact(7L,70L,2)).thenReturn(new FileEvidenceApi.Document(
                81L,"ACC","ACCEPTANCE_REPORT_VERSION","4","ATTACHMENT","original-slot",70L,2,"sha","signed.pdf",true));
    }
    @Test void documentFirstProjectionPromotesArchiveObligationWithoutOverwritingIdentity() {
        projectionDocument();var original=material();original.setArchiveStatus("NOT_REQUIRED");
        original.setFileReferenceId(81L);
        when(materials.selectSourceFile(new DeliverySourceFileQuery(7L,99L,70L,2))).thenReturn(original);
        when(materials.requireArchiveIfNotRequired(new DeliveryMaterialArchiveObligationQuery(7L,710L))).thenReturn(1);
        assertSame(original,service.registerProjectionFile(requirement(),projectionFact(),"report","PENDING_COMPENSATION"));
        assertEquals("PENDING_COMPENSATION",original.getArchiveStatus());
        assertEquals(81L,original.getFileReferenceId());assertEquals(901L,original.getRequirementId());
        verify(materials,never()).updateById(any(DeliveryMaterialDO.class));
    }
    @Test void projectionFirstDocumentReuseKeepsArchiveObligation() {
        var original=material();original.setArchiveStatus("PENDING_COMPENSATION");
        when(files.inspectDocument(7L,81L)).thenReturn(new FileEvidenceApi.Document(81L,"ACC","ACCEPTANCE_REPORT_VERSION","4","ATTACHMENT","slot",70L,2,"sha","signed.pdf",true));
        when(materials.selectSourceFile(new DeliverySourceFileQuery(7L,99L,70L,2))).thenReturn(original);
        service.registerTemplateFrozenDocument(requirement(),"ACC.REPORT",81L,"report");
        assertEquals("PENDING_COMPENSATION",original.getArchiveStatus());
        verify(materials,never()).requireArchiveIfNotRequired(any());
    }
    @Test void concurrentDuplicateProjectionPromotesExistingSource() {
        projectionDocument();var original=material();original.setArchiveStatus("NOT_REQUIRED");
        when(materials.insert(any(DeliveryMaterialDO.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("source"));
        when(materials.selectSourceIdentityForUpdate(any())).thenReturn(original);
        when(materials.requireArchiveIfNotRequired(any())).thenReturn(1);
        assertSame(original,service.registerProjectionFile(requirement(),projectionFact(),"report","PENDING_COMPENSATION"));
        assertEquals("PENDING_COMPENSATION",original.getArchiveStatus());
        verify(materials).requireArchiveIfNotRequired(new DeliveryMaterialArchiveObligationQuery(7L,710L));
    }
    @Test void archiveCompletionAndInvalidityAreNeverDowngradedByReuse() {
        projectionDocument();var original=material();
        when(materials.selectSourceFile(new DeliverySourceFileQuery(7L,99L,70L,2))).thenReturn(original);
        for(String state:List.of("ARCHIVED","INVALID","PENDING_COMPENSATION")) {
            original.setArchiveStatus(state);
            service.registerProjectionFile(requirement(),projectionFact(),"report","PENDING_COMPENSATION");
            assertEquals(state,original.getArchiveStatus());
        }
    }
    @Test void actualNativeOwnerClassificationDoesNotReplaceTemplateRequirementIdentity() {
        var resolver=mock(DeliveryDocumentOriginResolver.class);ReflectionTestUtils.setField(service,"originResolver",resolver);
        var document=new FileEvidenceApi.Document(81L,"SOL","REQUIREMENT_ANALYSIS_REVISION","8","FORM_FIELD_ATTACHMENT/brief","slot",70L,2,"sha","brief.pdf",true);
        when(files.inspectDocument(7L,81L)).thenReturn(document);
        when(resolver.resolve(document)).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider.Scope(99L,"SOL.REQUIREMENT_DOCUMENT","SOL","REQUIREMENT_ANALYSIS",42L,8L));
        var original=material();when(materials.selectSourceFile(new DeliverySourceFileQuery(7L,99L,70L,2))).thenReturn(original);
        service.registerTemplateFrozenDocument(requirement(),"SOL.REQUIREMENT_DOCUMENT",81L,"brief");
        assertEquals("SOL.REQUIREMENT_DOCUMENT",original.getBusinessTypeCode());assertEquals(42L,original.getSourceEntityId());
        assertEquals(8L,original.getSourceRevisionId());assertEquals("REQ-A",original.getTypeCode());assertEquals(901L,original.getRequirementId());
        verify(materials).assignOriginIfMissing(new DeliveryMaterialOriginUpdate(7L,710L,"SOL.REQUIREMENT_DOCUMENT","SOL","REQUIREMENT_ANALYSIS",42L,8L));
    }
    @Test void actualSourceProjectMismatchRejectsBeforeAssociationOrMaterialWrites() {
        var resolver=mock(DeliveryDocumentOriginResolver.class);ReflectionTestUtils.setField(service,"originResolver",resolver);
        var document=new FileEvidenceApi.Document(81L,"SOL","REQUIREMENT_ANALYSIS_REVISION","8","FORM_FIELD_ATTACHMENT/brief","slot",70L,2,"sha","brief.pdf",true);
        when(files.inspectDocument(7L,81L)).thenReturn(document);
        when(resolver.resolve(document)).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider.Scope(100L,"SOL.REQUIREMENT_DOCUMENT","SOL","REQUIREMENT_ANALYSIS",42L,8L));
        assertThrows(BusinessContractException.class,()->service.registerTemplateFrozenDocument(requirement(),"SOL.REQUIREMENT_DOCUMENT",81L,"brief"));
        verifyNoInteractions(links);verify(materials,never()).assignOriginIfMissing(any());verify(materials,never()).insert(any(DeliveryMaterialDO.class));
    }

    @Test void sameSecondNullAliasDoesNotMergeDifferentApprovedBaseline() {
        canonicalResultStore();
        when(provider.identity(7L,99L,"same-second",null)).thenReturn(new DeliveryBusinessObjectEvidenceProvider.Identity("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",1L));
        var first=service.registerTemplateFrozenBusinessResult(requirement(),"project_business_result","same-second",null,"v1");
        when(provider.aliases(7L,99L,"42",2L)).thenReturn(List.of(new DeliveryBusinessObjectEvidenceProvider.Alias("project_business_result","same-second",null)));
        when(materials.selectSourceBusiness(new DeliverySourceBusinessQuery(7L,99L,"project_business_result","same-second",null))).thenReturn(first);
        var second=service.registerBusinessResult("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",2L,"v2",99L);
        assertNotEquals(first.getId(),second.getId());assertNotEquals(first.getSourceIdentityKey(),second.getSourceIdentityKey());
        assertNull(first.getBusinessRevisionNo());assertEquals(1L,first.getSourceRevisionId());assertEquals(2L,second.getSourceRevisionId());
        verify(materials,times(2)).insert(any(DeliveryMaterialDO.class));
    }
    @Test void unprovenLegacyNullRevisionDoesNotAcquireCurrentApprovedIdentity() {
        var original=material();original.setMaterialKind("BUSINESS_RESULT");original.setBusinessObjectType("project_business_result");original.setBusinessObjectId("same-second");original.setSourceIdentityKey("historical-wrapper-key");
        when(provider.supports("solution")).thenReturn(true);
        when(provider.identity(7L,99L,"42",2L)).thenReturn(new DeliveryBusinessObjectEvidenceProvider.Identity("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",2L));
        when(provider.aliases(7L,99L,"42",2L)).thenReturn(List.of(new DeliveryBusinessObjectEvidenceProvider.Alias("project_business_result","same-second",null)));
        when(materials.selectSourceBusiness(new DeliverySourceBusinessQuery(7L,99L,"project_business_result","same-second",null))).thenReturn(original);
        var second=service.registerBusinessResult("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",2L,"v2",99L);
        assertNotSame(original,second);assertNull(original.getSourceRevisionId());assertNull(original.getSourceOwnerModule());assertEquals("historical-wrapper-key",original.getSourceIdentityKey());
        verify(materials,never()).assignSourceIdentityIfMissing(any());
    }

    @Test void oldNullWrapperCannotBeSubmittedAsNewSameSecondApproval() {
        var old=material();old.setMaterialKind("BUSINESS_RESULT");old.setBusinessObjectType("project_business_result");old.setBusinessObjectId("same-second");old.setSourceRevisionId(1L);
        when(provider.supports("project_business_result")).thenReturn(true);
        when(provider.identity(7L,99L,"same-second",null)).thenReturn(new DeliveryBusinessObjectEvidenceProvider.Identity("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",2L));
        assertThrows(BusinessContractException.class,()->service.revalidateActive(old));
        assertEquals(1L,old.getSourceRevisionId());verify(materials,never()).updateById(any(DeliveryMaterialDO.class));
    }
    @Test void explicitCurrentRevisionRawWrapperHashRemainsValidAndIsReused() throws Exception {
        var original=material();original.setMaterialKind("BUSINESS_RESULT");original.setBusinessObjectType("project_business_result");
        original.setBusinessObjectId("type|42|42|formed");original.setBusinessRevisionNo(9L);
        var method=DeliveryMaterialService.class.getDeclaredMethod("sourceKey",Long.class,String.class,Object[].class);method.setAccessible(true);
        String raw=(String)method.invoke(null,99L,"BUSINESS_RESULT",new Object[]{"project_business_result","type|42|42|formed",9L});original.setSourceIdentityKey(raw);
        when(provider.supports(anyString())).thenReturn(true);
        var identity=new DeliveryBusinessObjectEvidenceProvider.Identity("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",9L);
        when(provider.identity(eq(7L),eq(99L),anyString(),eq(9L))).thenReturn(identity);
        when(provider.aliases(7L,99L,"42",9L)).thenReturn(List.of(new DeliveryBusinessObjectEvidenceProvider.Alias("project_business_result","type|42|42|formed",9L)));
        when(materials.selectSourceBusiness(new DeliverySourceBusinessQuery(7L,99L,"project_business_result","type|42|42|formed",9L))).thenReturn(original);
        assertDoesNotThrow(()->service.revalidateActive(original));
        assertSame(original,service.registerBusinessResult("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",9L,"approved",99L));
        assertEquals(raw,original.getSourceIdentityKey());assertEquals(9L,original.getBusinessRevisionNo());
        verify(materials,never()).assignSourceIdentityIfMissing(any());verify(materials,never()).insert(any(DeliveryMaterialDO.class));
    }
    @Test void unrelatedHashCannotMasqueradeAsCurrentRevisionWrapper() {
        var old=material();old.setMaterialKind("BUSINESS_RESULT");old.setBusinessObjectType("project_business_result");old.setBusinessObjectId("same-second");old.setBusinessRevisionNo(9L);old.setSourceIdentityKey("a".repeat(64));
        when(provider.supports("project_business_result")).thenReturn(true);
        when(provider.identity(7L,99L,"same-second",9L)).thenReturn(new DeliveryBusinessObjectEvidenceProvider.Identity("SOL","solution",42L,"IMPLEMENTATION_PLAN","solution","42",9L));
        assertThrows(BusinessContractException.class,()->service.revalidateActive(old));
        verify(materials,never()).updateById(any(DeliveryMaterialDO.class));
    }

    @Test void copiedRaFileKeepsFirstRevisionAndHistoricalTypeAlias() {
        var resolver=mock(DeliveryDocumentOriginResolver.class);ReflectionTestUtils.setField(service,"originResolver",resolver);
        var document=new FileEvidenceApi.Document(81L,"SOL","REQUIREMENT_ANALYSIS_REVISION","12","FORM_FIELD_ATTACHMENT/brief","slot",70L,2,"sha","brief.pdf",true);
        when(files.inspectDocument(7L,81L)).thenReturn(document);
        when(resolver.resolve(document)).thenReturn(new FileDocumentSourceProvider.Scope(99L,"SOL.REQUIREMENT_DOCUMENT","SOL","requirementAnalysis",42L,12L));
        var original=material();original.setSourceOwnerModule("SOL");original.setSourceEntityType("REQUIREMENT_ANALYSIS");original.setSourceEntityId(42L);original.setSourceRevisionId(8L);original.setBusinessTypeCode("SOL.REQUIREMENT_DOCUMENT");
        when(materials.selectSourceFile(new DeliverySourceFileQuery(7L,99L,70L,2))).thenReturn(original);
        assertSame(original,service.registerNativeGeneratedDocument(81L));assertEquals(8L,original.getSourceRevisionId());assertEquals("REQUIREMENT_ANALYSIS",original.getSourceEntityType());
        verify(materials,never()).insert(any(DeliveryMaterialDO.class));
    }

}
