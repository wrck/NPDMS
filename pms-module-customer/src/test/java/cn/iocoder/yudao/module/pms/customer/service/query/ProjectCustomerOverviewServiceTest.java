package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerContactAccessService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.MASKED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectCustomerOverviewServiceTest {
    private final ProjectContactContextApi projects = mock(ProjectContactContextApi.class);
    private final CustomerMasterMapper customers = mock(CustomerMasterMapper.class);
    private final CustomerContactAccessService contacts = mock(CustomerContactAccessService.class);
    private final ProjectCustomerOverviewService service = new ProjectCustomerOverviewService(projects, customers,
            new CustomerResponseService(new CustomerFieldMaskingService()), contacts);
    private final ProjectCustomerOverviewService.Query query = new ProjectCustomerOverviewService.Query(1L, 31L, 100L);
    private final ProjectContactContextApi.Query projectQuery = new ProjectContactContextApi.Query(1L, 31L, 100L);
    private CustomerMasterDO customer;

    @BeforeEach
    void setUp() {
        when(projects.inspect(projectQuery)).thenReturn(context(100L, 200L));
        customer = new CustomerMasterDO();
        customer.setId(200L); customer.setTenantId(1L); customer.setCode("C-200");
        customer.setName("项目关联客户"); customer.setLifecycleStatus("ENABLED");
        customer.setContactPhone("13812345678"); customer.setContactEmail("example@gmail.com");
        when(customers.selectById(200L)).thenReturn(customer);
        when(contacts.resolve(31L, true)).thenReturn(MASKED);
    }

    @Test
    void projectScopeAllowsBoundCustomerWithoutGrantingGlobalCustomerScope() {
        var result = service.get(query);
        assertEquals(200L, result.getId());
        assertEquals("138****5678", result.getContactPhone());
        assertEquals("e****@gmail.com", result.getContactEmail());
        verify(projects).inspect(projectQuery);
        verify(customers).selectById(200L);
    }

    @Test
    void ownerScopeDenialStopsBeforeCustomerLookup() {
        when(projects.inspect(projectQuery)).thenThrow(new SecurityException("project access denied"));
        assertThrows(SecurityException.class, () -> service.get(query));
        verifyNoInteractions(customers, contacts);
    }

    @Test
    void mismatchedProjectIdentityCannotReadCustomer() {
        when(projects.inspect(projectQuery)).thenReturn(context(101L, 200L));
        assertThrows(ServiceException.class, () -> service.get(query));
        verifyNoInteractions(customers, contacts);
    }

    @Test
    void unassociatedProjectReturnsEmptyWithoutCustomerLookup() {
        when(projects.inspect(projectQuery)).thenReturn(context(100L, null));
        assertNull(service.get(query));
        verifyNoInteractions(customers, contacts);
    }

    @Test
    void unavailableOwnerDoesNotTurnIntoGlobalLookup() {
        when(projects.inspect(projectQuery)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.get(query));
        verifyNoInteractions(customers, contacts);
    }

    @Test
    void crossTenantCustomerIsRejectedBeforeSerialization() {
        customer.setTenantId(2L);
        assertThrows(ServiceException.class, () -> service.get(query));
        verifyNoInteractions(contacts);
    }

    @Test
    void missingBoundCustomerDoesNotReturnProjectSnapshotAsMasterData() {
        when(customers.selectById(200L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.get(query));
        verifyNoInteractions(contacts);
    }

    @Test
    void disabledCustomerRemainsVisibleThroughExistingProjectAssociation() {
        customer.setLifecycleStatus("DISABLED");
        assertEquals("DISABLED", service.get(query).getLifecycleStatus());
    }

    private ProjectContactContextApi.Context context(Long projectId, Long customerId) {
        return new ProjectContactContextApi.Context(projectId, customerId, 3L, "ACTIVE", true);
    }
}
