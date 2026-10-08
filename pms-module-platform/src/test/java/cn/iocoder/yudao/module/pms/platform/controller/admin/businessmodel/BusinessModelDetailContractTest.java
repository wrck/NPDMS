package cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ExecutionBackendCapability;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BusinessModelDetailContractTest {
    final BusinessModelCatalog catalog=mock(BusinessModelCatalog.class);
    final BusinessAccessGuard guard=mock(BusinessAccessGuard.class);
    @SuppressWarnings("unchecked")
    final ObjectProvider<ExecutionBackendCapability> backends=mock(ObjectProvider.class);
    final BusinessModelController controller=new BusinessModelController(catalog,guard,mock(BusinessEntityAccessPort.class),mock(BusinessOperationDispatcher.class),backends);
    final BusinessModelDescriptor descriptor=new BusinessModelDescriptor("SOL","requirementAnalysis","RA",1,null,"RA",null,List.of(),List.of(),List.of(new BusinessOperationDescriptor("save",1,"save",BusinessOperationDescriptor.StandardOperationKind.UPDATE)),List.of(),"sol_requirement_analysis");
    @Test void actualDetailEndpointSerializesViewCodeAndKeepsReadonlyOperation() throws Exception {
        when(catalog.require("SOL","requirementAnalysis")).thenReturn(descriptor);
        doThrow(new BusinessContractException("OWNER_READONLY","readonly")).when(guard).requireWritable(eq(descriptor),any(),eq("operation:save"));
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            TenantContextHolder.setTenantId(1L);
            MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/v1/pms/business-models/SOL/requirementAnalysis"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewCode").value("sol_requirement_analysis"))
                    .andExpect(jsonPath("$.data.operations[0].executable").value(false));
            verify(guard).requireReadable(eq(descriptor),any(),eq("detail"));
        } finally { TenantContextHolder.clear(); }
    }
    @Test void detailPermissionDenialIsPropagatedBeforeReturningAnyView() {
        when(catalog.require("SOL","requirementAnalysis")).thenReturn(descriptor);
        var denial=new BusinessContractException("OWNER_PERMISSION_DENIED","denied");
        doThrow(denial).when(guard).requireReadable(eq(descriptor),any(),eq("detail"));
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            TenantContextHolder.setTenantId(1L);
            assertSame(denial,assertThrows(BusinessContractException.class,()->controller.detail("SOL","requirementAnalysis")));
            verify(guard,never()).requireWritable(any(),any(),any());
        } finally { TenantContextHolder.clear(); }
    }
    @Test void publicPageUsesInheritedQueryForExplicitDefaultModel() throws Exception {
        var access=mock(BusinessEntityAccessPort.class); var dispatcher=mock(BusinessOperationDispatcher.class);
        var endpoint=new BusinessModelController(catalog,guard,access,dispatcher,backends);
        var model=new BusinessModelDescriptor("IT","note","IT_NOTE",1,BusinessModelKind.AGGREGATE_ROOT,"记录","it:note:query",
                List.of(),List.of(),List.of(),List.of(),null,new BusinessScopeBinding("project","projectId"));
        when(catalog.require("IT","note")).thenReturn(model);
        var query=new BusinessEntityPageQuery(null,"IT","note",List.of(),20,null);
        when(dispatcher.query(query)).thenReturn(new BusinessEntitySlice(List.of(),null,
                cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness.COMPLETE,null));
        MockMvcBuilders.standaloneSetup(endpoint).build().perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/pms/business-models/IT/note/page")
                    .contentType("application/json").content("{\"pageSize\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completeness").value("COMPLETE"));
        verify(dispatcher).query(query); verifyNoInteractions(access);
    }
    @Test void legacyPageKeepsItsExistingReadPort() {
        var access=mock(BusinessEntityAccessPort.class); var dispatcher=mock(BusinessOperationDispatcher.class);
        var endpoint=new BusinessModelController(catalog,guard,access,dispatcher,backends);
        when(catalog.require("SOL","requirementAnalysis")).thenReturn(descriptor);
        var request=new BusinessModelController.PageQueryReqVO();request.setPageSize(20);
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);TenantContextHolder.setTenantId(1L);
            endpoint.page("SOL","requirementAnalysis",request);
            verify(access).query(eq(new BusinessEntityPageQuery(null,"SOL","requirementAnalysis",List.of(),20,null)),any());
            verifyNoInteractions(dispatcher);
        } finally {TenantContextHolder.clear();}
    }

}
