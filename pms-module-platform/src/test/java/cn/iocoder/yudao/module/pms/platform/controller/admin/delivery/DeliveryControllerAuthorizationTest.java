package cn.iocoder.yudao.module.pms.platform.controller.admin.delivery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliverySubmissionMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DeliveryControllerAuthorizationTest {
    DeliveryCatalogService catalog=mock(DeliveryCatalogService.class);
    DeliveryMaterialService materials=mock(DeliveryMaterialService.class);
    DeliveryRequirementService requirements=mock(DeliveryRequirementService.class);
    DeliveryOwnerAccess owner=mock(DeliveryOwnerAccess.class);
    DeliverySubmissionMapper submissions=mock(DeliverySubmissionMapper.class);
    cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi files=mock(cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.class);
    DeliveryController controller=new DeliveryController(catalog,materials,requirements,owner,submissions,files);
    DeliveryRequirementDO requirement() {
        var row=new DeliveryRequirementDO();row.setId(9L);row.setOwnerModule("ACC");
        row.setEntityType("project_deliverable");row.setEntityId(42L);row.setTypeCode("REPORT");
        row.setRequirementKind(DeliveryRequirementDO.KIND_TEMPLATE_FROZEN);
        when(requirements.requireRequirement(9L)).thenReturn(row);return row;
    }
    @Test void historicalSubmissionReadChecksOwnerBeforeDirectMapper() {
        requirement();when(owner.require("ACC","project_deliverable",42L,"REPORT",false,false))
            .thenThrow(new BusinessContractException("DELIVERY_ACCESS_DENIED","scope"));
        assertThrows(BusinessContractException.class,()->controller.listSubmissions(9L));
        verifyNoInteractions(submissions);
    }
    @Test void authorizedTemplateHistoryRemainsReadable() {
        requirement();when(submissions.selectByRequirement(9L)).thenReturn(List.of());
        assertTrue(controller.listSubmissions(9L).getData().isEmpty());
        verify(owner).require("ACC","project_deliverable",42L,"REPORT",false,false);
    }
    @Test void unauthorizedOwnerListNeverQueriesMaterialMapperService() {
        when(owner.require("SOL","solution",5L,null,false,false)).thenThrow(new BusinessContractException("DELIVERY_ACCESS_DENIED","scope"));
        assertThrows(BusinessContractException.class,()->controller.listMaterials("SOL","solution",5L,null));
        verifyNoInteractions(materials);
    }
    @Test void templateCannotUseCatalogConfirmationBypass() {
        requirement();assertThrows(BusinessContractException.class,()->controller.confirm(9L));
        verify(requirements,never()).confirm(anyLong());
    }
    @Test void catalogConfirmationRetainsAuthorizedPath() {
        var row=requirement();row.setRequirementKind(DeliveryRequirementDO.KIND_CATALOG);
        when(requirements.confirm(9L)).thenReturn(new DeliveryRequirementService.RequirementView(row,1,1,"MATERIAL"));
        controller.confirm(9L);verify(requirements).confirm(9L);
    }
    @Test void unauthorizedCompletionNeverEvaluatesBusinessOrFileFacts() {
        requirement();when(owner.require("ACC","project_deliverable",42L,"REPORT",false,false))
            .thenThrow(new BusinessContractException("DELIVERY_ACCESS_DENIED","scope"));
        assertThrows(BusinessContractException.class,()->controller.completion(9L));
        verify(requirements,never()).evaluateCompletion(anyLong());
    }
    @Test void authorizedCompletionRetainsTemplateOwnerRulePath() {
        requirement();when(requirements.evaluateCompletion(9L)).thenReturn(
            new DeliveryRequirementService.CompletionFact(9L,1,1,false,false,"OWNER_CONFIRMATION_PENDING"));
        var fact=controller.completion(9L).getData();
        assertFalse(fact.satisfied());
        verify(owner).require("ACC","project_deliverable",42L,"REPORT",false,false);
        verify(requirements).evaluateCompletion(9L);
    }
    @Test void materialViewRetainsOriginalFileOwnerForControlledFileAccess() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            var row=new DeliveryMaterialDO();row.setId(5L);row.setFileReferenceId(6L);
            row.setMaterialKind(DeliveryMaterialDO.KIND_FILE);
            when(materials.listByEntity("ACC","project_deliverable",42L,"REPORT")).thenReturn(List.of(row));
            when(files.inspectDocument(1L,6L)).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Document(
                6L,"ACC","ACCEPTANCE_REPORT_VERSION","101","REPORT_ATTACHMENT","original-slot",7L,2,"hash","report.pdf",true));
            var key=controller.listMaterials("ACC","project_deliverable",42L,"REPORT").getData().getFirst().getFileBusinessKey();
            assertEquals("ACC",key.ownerContext());
            assertEquals("ACCEPTANCE_REPORT_VERSION",key.objectType());
            assertEquals("original-slot",key.referenceKey());
            var order=inOrder(owner,materials,files);
            order.verify(owner).require("ACC","project_deliverable",42L,"REPORT",false,false);
            order.verify(materials).listByEntity("ACC","project_deliverable",42L,"REPORT");
            order.verify(files).inspectDocument(1L,6L);
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }
}
