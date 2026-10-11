package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactCompletionApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.ProjectCustomerContactMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectContactCompletionApiImplTest {
    CustomerMasterMapper customers = mock(CustomerMasterMapper.class);
    ProjectCustomerContactMapper contacts = mock(ProjectCustomerContactMapper.class);
    ProjectContactCompletionApiImpl api = new ProjectContactCompletionApiImpl(customers, contacts);
    ProjectContactCompletionApi.Query query = new ProjectContactCompletionApi.Query(1L, 7L, 8L, "customer-code");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var customer = new CustomerMasterDO(); customer.setId(8L); customer.setTenantId(1L);
        when(customers.selectById(8L)).thenReturn(customer);
        when(contacts.selectCompletionForUpdate(any())).thenReturn(List.of());
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    ProjectCustomerContactDO record() {
        var row = new ProjectCustomerContactDO(); row.setId(10L); row.setVersion(2); row.setTenantId(1L);
        row.setProjectId(7L); row.setCustomerId(8L); row.setStatus(0); row.setName("联系人"); row.setMobile("13800138000");
        row.setPrimaryFlag(false); return row;
    }
    @Test void oneEnabledProjectRecordCompletesWithoutPrimaryFlagOrNewCreation() {
        when(contacts.selectCompletionForUpdate(any())).thenReturn(List.of(record()));
        var result = api.lockAndInspect(query);
        assertTrue(result.completed()); assertEquals(List.of(new ProjectContactCompletionApi.Evidence(10L,2)), result.records());
        verify(contacts).selectCompletionForUpdate(argThat(q -> q.tenantId()==1L && q.projectId()==7L && q.customerId()==8L));
        verify(customers, never()).selectByTenantIdAndCode(any(), anyString());
    }
    @Test void noProjectRelationDoesNotUseCustomerAddressBook() { assertFalse(api.lockAndInspect(query).completed()); }
    @Test void disabledDeletedAndMalformedRecordsDoNotComplete() {
        var disabled=record(); disabled.setStatus(1);
        var deleted=record(); deleted.setDeleted(true);
        var invalid=record(); invalid.setMobile(null);
        when(contacts.selectCompletionForUpdate(any())).thenReturn(List.of(disabled,deleted,invalid));
        assertFalse(api.lockAndInspect(query).completed());
    }
    @Test void foreignProjectTenantOrCustomerFailsClosed() {
        for (int kind=0;kind<3;kind++) {
            var foreign=record(); if(kind==0)foreign.setProjectId(99L); if(kind==1)foreign.setTenantId(99L); if(kind==2)foreign.setCustomerId(99L);
            when(contacts.selectCompletionForUpdate(any())).thenReturn(List.of(foreign));
            assertThrows(IllegalArgumentException.class,()->api.lockAndInspect(query));
        }
    }
    @Test void crossTenantCallNeverReadsBusinessTables() {
        assertThrows(IllegalArgumentException.class,()->api.lockAndInspect(new ProjectContactCompletionApi.Query(2L,7L,8L,null)));
        verifyNoInteractions(customers,contacts);
    }
    @Test void stableCodeFallbackResolvesCustomerWithoutChangingProject() {
        var customer=customers.selectById(8L);
        when(customers.selectByTenantIdAndCode(1L,"customer-code")).thenReturn(customer);
        when(contacts.selectCompletionForUpdate(any())).thenReturn(List.of(record()));
        assertTrue(api.lockAndInspect(new ProjectContactCompletionApi.Query(1L,7L,null,"customer-code")).completed());
    }
    @Test void missingAssociationDoesNotBroadenQuery() {
        assertFalse(api.lockAndInspect(new ProjectContactCompletionApi.Query(1L,7L,null,null)).completed());
        verifyNoInteractions(contacts);
    }
    @Test void ownerReadFailureIsNotACompletedFact() {
        when(contacts.selectCompletionForUpdate(any())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,()->api.lockAndInspect(query));
    }
}
