package cn.iocoder.yudao.module.pms.platform.service.businessmodel;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DefaultDeliveryListQuery;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeclaredBusinessDeliveryBridge;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
/** Shared history authorization/projection; external ports are doubles, SQL is covered separately. */
class DefaultDeliveryHistoryServiceTest {
    @Test void collectionPaginatesAfterOwnerAuthorizationSoHiddenRowsDoNotHideLaterVisibleRows(){
        var catalog=mock(BusinessModelCatalog.class);var callers=mock(BusinessCallerContext.class);var entities=mock(DeclaredBusinessDeliveryBridge.class);
        var projects=mock(ProjectScopeApi.class);var materials=mock(DeliveryMaterialMapper.class);var model=mock(BusinessModelDescriptor.class);
        when(model.ownerModule()).thenReturn("IT");when(model.entityType()).thenReturn("note");when(model.stableCode()).thenReturn("NOTE");
        when(catalog.findByStableCode("NOTE")).thenReturn(Optional.of(model));when(catalog.all()).thenReturn(List.of(model));when(entities.supports("IT","note")).thenReturn(true);
        when(callers.require()).thenReturn(new AbstractBusinessApplicationService.ResolvedCaller(7L,9L,null));
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(99L,1L,Set.of(99L),Set.of()));when(entities.projectId(7L,"IT","note",11L)).thenReturn(99L);
        doThrow(new BusinessContractException("ACCESS_DENIED","private draft")).when(entities).requireDefault(eq(7L),eq(9L),eq("IT"),eq("note"),eq(12L),eq(false),eq(false),isNull());
        var rows=new ArrayList<DeliveryMaterialDO>();
        for(long id=202;id>=1;id--){var row=new DeliveryMaterialDO();row.setId(id);row.setTenantId(7L);row.setProjectId(99L);row.setEntityId(id>1?12L:11L);row.setBusinessTypeCode("NOTE");row.setTypeCode("REPORT");row.setFileReferenceId(id);row.setFileArtifactId(id);row.setFileVersionNo(1);row.setStatus("ACTIVE");row.setDeleted(false);rows.add(row);}
        java.util.function.Function<DefaultDeliveryListQuery,List<DeliveryMaterialDO>> select=query->{int start=(int)query.getOffset(),end=Math.min(rows.size(),start+query.getPageSize());return start>=rows.size()?List.of():rows.subList(start,end);};
        when(materials.selectDefaultDeliveryPage(any())).thenAnswer(call->new PageResult<>(select.apply(call.getArgument(0)),(long)rows.size()));
        when(materials.selectDefaultDeliveryList(any())).thenAnswer(call->select.apply(call.getArgument(0)));
        var service=new DefaultBusinessDeliveryService(catalog,mock(BusinessAccessGuard.class),callers,entities,projects,mock(FileUploadApplicationService.class),mock(FileEvidenceApi.class),materials);
        var page=service.list(99L,"REPORT",null,null,1,1);
        assertEquals(List.of("1"),page.getList().stream().map(row->row.id()).toList());assertEquals(1L,page.getTotal());
        assertTrue(service.list(99L,"REPORT",null,null,2,1).getList().isEmpty());
    }
    @Test void historyUsesExactIdentityAndReadonlyRecordsWithoutChangingCurrentListOrCompletion(){
        var catalog=mock(BusinessModelCatalog.class);var guard=mock(BusinessAccessGuard.class);var callers=mock(BusinessCallerContext.class);
        var entities=mock(DeclaredBusinessDeliveryBridge.class);var projects=mock(ProjectScopeApi.class);var materials=mock(DeliveryMaterialMapper.class);
        var model=mock(BusinessModelDescriptor.class);when(model.ownerModule()).thenReturn("IT");when(model.entityType()).thenReturn("note");when(model.stableCode()).thenReturn("NOTE");
        when(catalog.findByStableCode("NOTE")).thenReturn(Optional.of(model));when(catalog.all()).thenReturn(List.of(model));
        when(callers.require()).thenReturn(new AbstractBusinessApplicationService.ResolvedCaller(7L,9L,null));
        when(entities.supports("IT","note")).thenReturn(true);when(entities.projectId(7L,"IT","note",11L)).thenReturn(99L);
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(99L,1L,Set.of(99L),Set.of()));
        var row=new DeliveryMaterialDO();row.setId(1L);row.setTenantId(7L);row.setProjectId(99L);row.setEntityId(11L);row.setBusinessTypeCode("NOTE");row.setTypeCode("REPORT");row.setOwnerModule("IT");row.setEntityType("note");row.setFileReferenceId(2L);row.setFileArtifactId(3L);row.setFileVersionNo(1);row.setVersion(0L);row.setStatus("ACTIVE");row.setDeleted(false);
        when(materials.selectDefaultDeliveryPage(any())).thenAnswer(call->{var query=call.getArgument(0,DefaultDeliveryListQuery.class);assertEquals(7L,query.getTenantId());assertEquals(99L,query.getProjectId());assertEquals("NOTE",query.getBusinessType());assertEquals(11L,query.getEntityId());return new PageResult<>(List.of(row),1L);});
        var service=new DefaultBusinessDeliveryService(catalog,guard,callers,entities,projects,mock(FileUploadApplicationService.class),mock(FileEvidenceApi.class),materials);
        assertFalse(service.history(new Scope(99L,"NOTE","11","REPORT"),1,20).getList().getFirst().editable());
        verify(materials).selectDefaultDeliveryPage(argThat(DefaultDeliveryListQuery::isIncludeInactive));
        assertTrue(service.list(99L,"REPORT","NOTE","11",1,20).getList().getFirst().editable());
        assertThrows(BusinessContractException.class,()->service.history(new Scope(100L,"NOTE","11","REPORT"),1,20));
        assertThrows(BusinessContractException.class,()->service.history(new Scope(99L,null,null,"REPORT"),1,20));
        doThrow(new BusinessContractException("ACCESS_DENIED","revoked")).when(entities).requireDefault(eq(7L),eq(9L),eq("IT"),eq("note"),eq(11L),eq(false),eq(false),isNull());
        assertThrows(BusinessContractException.class,()->service.history(new Scope(99L,"NOTE","11","REPORT"),1,20));
    }
}
