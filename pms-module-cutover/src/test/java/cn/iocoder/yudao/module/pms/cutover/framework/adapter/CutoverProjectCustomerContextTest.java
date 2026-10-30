package cn.iocoder.yudao.module.pms.cutover.framework.adapter;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverProjectCustomerContextTest {
    final ProjectBusinessContextApi projects = mock(ProjectBusinessContextApi.class);
    final CustomerQueryApi customers = mock(CustomerQueryApi.class);
    final CustomerCodeQuery customerQuery = new CustomerCodeQuery("SYNTHETIC", 7L);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    private ProjectBusinessContextApi.Context context(Long customerId) {
        return ProjectBusinessContextApi.Context.builder().tenantId(1L).projectId(9L).projectVersion(2L)
                .scopeVersion(3L).customerId(customerId).customerCode("SYNTHETIC").customerName("合成客户").build();
    }
    private CutoverProjectContextPort port(Long customerId) {
        when(projects.inspect(any())).thenReturn(context(customerId));
        when(projects.lockAndRevalidate(any())).thenReturn(context(customerId));
        return new CutoverOwnerAdapters().cutoverProjectContextPort(projects, customers);
    }
    @Test void resolvesCodeOnlyProjectThroughAuthorizedCustomerQueryWithoutWritingProject() {
        var port = port(null);
        when(customers.getCustomerByCode(customerQuery)).thenReturn(new CustomerSummaryDTO(11L,1L,"SYNTHETIC","合成客户",null,"ENABLED",null,1L,null));
        var fact = port.inspect(new CutoverProjectContextPort.Inspection(1L,9L,3L));
        assertEquals(11L,fact.customerId());
        verify(customers).getCustomerByCode(customerQuery);
        verifyNoMoreInteractions(customers);
    }
    @Test void existingCustomerIdRemainsTheOwnerFact() {
        assertEquals(12L,port(12L).inspect(new CutoverProjectContextPort.Inspection(1L,9L,3L)).customerId());
        verifyNoInteractions(customers);
    }
    @Test void transactionRevalidationLocksTheSameStableCustomerCode() {
        var port = port(null);
        when(customers.lockCustomerByCode(customerQuery)).thenReturn(new CustomerSummaryDTO(11L,1L,"SYNTHETIC","合成客户",null,"ENABLED",null,1L,null));
        var expected = CutoverProjectContextPort.ProjectContextFact.builder().tenantId(1L).projectId(9L)
                .projectVersion(2).projectScopeVersion(3L).customerId(11L).customerCode("SYNTHETIC").build();
        assertEquals(11L,port.lockAndRevalidate(expected).customerId());
        verify(customers).lockCustomerByCode(customerQuery);
        verifyNoMoreInteractions(customers);
    }
    @Test void customerPermissionFailureIsNotTreatedAsUnconfiguredLevel() {
        var port = port(null);
        var denied = new IllegalStateException("permission denied");
        when(customers.getCustomerByCode(customerQuery)).thenThrow(denied);
        assertSame(denied,assertThrows(IllegalStateException.class,()->port.inspect(new CutoverProjectContextPort.Inspection(1L,9L,3L))));
    }
    @Test void differentTenantCustomerCannotBecomeTheProjectReference() {
        var port = port(null);
        when(customers.getCustomerByCode(customerQuery)).thenReturn(new CustomerSummaryDTO(11L,2L,"SYNTHETIC","合成客户",null,"ENABLED",null,1L,null));
        assertThrows(RuntimeException.class,()->port.inspect(new CutoverProjectContextPort.Inspection(1L,9L,3L)));
    }
}
