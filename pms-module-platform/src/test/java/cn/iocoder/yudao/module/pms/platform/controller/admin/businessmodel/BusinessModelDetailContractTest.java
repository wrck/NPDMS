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
}
