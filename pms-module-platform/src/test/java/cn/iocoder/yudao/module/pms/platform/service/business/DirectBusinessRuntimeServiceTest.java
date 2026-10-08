package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DefaultDeliveryListQuery;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectBusinessRuntimeServiceTest {
    static BusinessModelDescriptor model() {return new BusinessModelDescriptor("IT","note","IT_NOTE",1,BusinessModelKind.AGGREGATE_ROOT,"Note","it:note:query",List.of(),List.of(),List.of(),List.of(),null,new BusinessScopeBinding("project","projectId"));}
    @Test void deliveryRequiresExactIdentityAndAnAvailableFile() {
        var owners=mock(DirectBusinessOwners.class);var owner=mock(ProjectBusinessService.class);
        var materials=mock(DeliveryMaterialMapper.class);var files=mock(FileEvidenceApi.class);
        var type=new ProjectBusinessRuntimeApi.Type("IT","note");
        when(owners.byIdentity("IT","note")).thenReturn(Optional.of(owner));
        when(owner.definition()).thenReturn(model());
        when(owner.runtimeDefinition()).thenReturn(new ProjectBusinessRuntimeApi.Definition(type,"IT_NOTE","Note",Map.of("BUSINESS_RECORD_SAVED","Saved"),null));
        var report=row(1L,"REPORT",101L);var photo=row(2L,"PHOTO",102L);
        when(materials.selectDefaultDeliveryList(any())).thenReturn(List.of(report,photo));
        when(files.inspectDocument(7L,101L)).thenReturn(document(101L,"REPORT",true));
        when(files.inspectDocument(7L,102L)).thenReturn(document(102L,"PHOTO",false));
        var service=new DirectBusinessRuntimeService(owners,materials,files);
        var query=new ProjectBusinessRuntimeApi.Query(7L,20L,type,11L);
        var observed=service.deliveryFacts(query,false);
        assertEquals(true,observed.facts().get(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT"));
        assertEquals(false,observed.facts().get(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"PHOTO"));
        assertEquals(true,observed.facts().get(ProjectBusinessRuntimeApi.DELIVERY_COMPLETE));
        var captured=ArgumentCaptor.forClass(DefaultDeliveryListQuery.class);verify(materials).selectDefaultDeliveryList(captured.capture());
        assertEquals(7L,captured.getValue().getTenantId());assertEquals(20L,captured.getValue().getProjectId());
        assertEquals(11L,captured.getValue().getEntityId());assertEquals("IT_NOTE",captured.getValue().getBusinessType());
        verify(owner).runtimeObservation(query,false);
        var wrongOwner=new FileEvidenceApi.Document(101L,"PLT","DEFAULT_BUSINESS_DELIVERY","20:IT:note:12","REPORT","reference-101",201L,1,"sha","file",true);
        when(files.inspectDocument(7L,101L)).thenReturn(wrongOwner);
        assertFalse(service.deliveryFacts(query,false).facts().get(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT"));
        when(files.inspectDocument(7L,101L)).thenReturn(document(101L,"REPORT",true));
        report.setDeleted(true);assertFalse(service.deliveryFacts(query,false).facts().get(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT"));
        report.setDeleted(false);report.setStatus("WITHDRAWN");assertFalse(service.deliveryFacts(query,false).facts().get(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT"));
        report.setStatus("ACTIVE");report.setFileSha256("different");assertFalse(service.deliveryFacts(query,false).facts().get(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT"));
        report.setFileSha256("sha");
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            when(files.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true,null,1,1,1));
            assertEquals(observed.factVersion(),service.deliveryFacts(query,true).factVersion());
            when(files.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false,"unavailable",1,1,1));
            assertFalse(service.deliveryFacts(query,true).facts().get(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT"));
        } finally {org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
        report.setProjectId(21L);assertThrows(IllegalStateException.class,()->service.deliveryFacts(query,false));
    }
    static FileEvidenceApi.Document document(Long ref,String type,boolean available){return new FileEvidenceApi.Document(ref,"PLT","DEFAULT_BUSINESS_DELIVERY","20:IT:note:11",type,"reference-"+ref,ref+100,1,"sha","file",available);}
    static DeliveryMaterialDO row(Long id,String type,Long ref){var row=new DeliveryMaterialDO();row.setId(id);row.setTenantId(7L);row.setProjectId(20L);row.setEntityId(11L);row.setBusinessTypeCode("IT_NOTE");row.setOwnerModule("IT");row.setEntityType("note");row.setTypeCode(type);row.setFileReferenceId(ref);row.setFileArtifactId(ref+100);row.setFileVersionNo(1);row.setFileSha256("sha");row.setVersion(0L);row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);row.setDeleted(false);return row;}
}
