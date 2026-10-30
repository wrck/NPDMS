package cn.iocoder.yudao.module.pms.project.api.contact;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectAncestorQueryApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorResult;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectContactContextApiImplTest {
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectAncestorQueryApi ancestors = mock(ProjectAncestorQueryApi.class);
    private final AuthorizationGrantApi grants = mock(AuthorizationGrantApi.class);
    private final cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService projectRoles = mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.class);
    private final CustomerQueryApi customers = mock(CustomerQueryApi.class);
    private final ProjectPartyMapper parties=mock(ProjectPartyMapper.class);
    private final ProjectContactContextApiImpl api = new ProjectContactContextApiImpl(projects,members,scopes,ancestors,grants,customers,projectRoles,parties);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var row = new ProjectMasterDO(); row.setId(7L); row.setTenantId(1L); row.setCustomerId(8L); row.setVersion(2L); row.setLifecycleStatus("ACTIVE");
        when(projects.selectById(7L)).thenReturn(row); when(projects.selectByIdForUpdate(7L)).thenReturn(row);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(7L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(7L),Set.of()));
        when(ancestors.getAncestors(any())).thenReturn(new ProjectAncestorResult(7L,7L,1L,List.of()));
        when(grants.listEffective(any())).thenReturn(List.of());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void superAdminCanMaintainContactsWithoutPretendingToBeAProjectManager() {
        when(projectRoles.isTenantSuperAdmin(1L, 3L)).thenReturn(true);
        assertTrue(api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L)).canManage());
        verify(members, never()).selectParticipantFacts(any());
        assertThrows(RuntimeException.class, () -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,1L)));
    }
    @Test void visibleServiceRoleAloneDoesNotGrantContactMaintenance() {
        when(members.selectParticipantFacts(any())).thenReturn(List.of());
        assertFalse(api.inspect(new ProjectContactContextApi.Query(1L,3L,7L)).canManage());
        assertThrows(RuntimeException.class, () -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L)));
    }
    @Test void managerCanMaintainOnlyWithCurrentProjectVersion() {
        when(members.selectParticipantFacts(any())).thenReturn(List.of(new ProjectMemberAssignmentDO()));
        var context = api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L));
        assertTrue(context.canManage()); assertEquals(8L, context.customerId());
        assertThrows(RuntimeException.class, () -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,1L)));
        assertThrows(RuntimeException.class, () -> api.inspect(new ProjectContactContextApi.Query(2L,3L,7L)));
    }

    @Test void closedProjectManagerKeepsHistoryReadButCannotWrite() {
        when(members.selectParticipantFacts(any())).thenReturn(List.of(new ProjectMemberAssignmentDO()));
        var closed = new ProjectMasterDO(); closed.setId(7L); closed.setTenantId(1L); closed.setCustomerId(8L); closed.setVersion(2L); closed.setLifecycleStatus("NORMAL_CLOSED");
        when(projects.selectById(7L)).thenReturn(closed); when(projects.selectByIdForUpdate(7L)).thenReturn(closed);
        var context = api.inspect(new ProjectContactContextApi.Query(1L,3L,7L));
        assertFalse(context.canManage()); assertTrue(context.canViewHistory());
        assertThrows(RuntimeException.class, () -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L)));
    }

    private ProjectPartyDO finalParty() {
        var row=new ProjectPartyDO();row.setId(99L);row.setTenantId(1L);row.setProjectId(7L);
        row.setPartyName("实际最终用户");row.setSourceRecordKey("51922");row.setStatus("ACTIVE");row.setDeleted(false);
        when(parties.selectHeaderFinalCustomers(any())).thenReturn(List.of(row));
        when(parties.selectSourceForUpdate(any())).thenReturn(row);return row;
    }
    @Test void historicalContactCanUseClosedProjectWithoutEnablingOrdinaryMaintenance() {
        when(projectRoles.isTenantSuperAdmin(1L,3L)).thenReturn(true);finalParty();
        projects.selectById(7L).setLifecycleStatus("NORMAL_CLOSED");
        assertEquals("51922",api.lockHistoricalFinalCustomer(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L)).sourceProjectKey());
        assertThrows(RuntimeException.class,()->api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L)));
        assertThrows(RuntimeException.class,()->api.lockHistoricalFinalCustomer(new ProjectContactContextApi.WriteQuery(1L,3L,7L,1L)));
    }
    @Test void historicalFinalCustomerDoesNotRequireAnUnrelatedCrmCustomerLookup() {
        when(projectRoles.isTenantSuperAdmin(1L,3L)).thenReturn(true); finalParty();
        var project = projects.selectById(7L);
        project.setCustomerId(null); project.setCustomerCode("UNRESOLVED-HISTORICAL-CUSTOMER");
        project.setLifecycleStatus("NORMAL_CLOSED");
        assertEquals(99L, api.inspectFinalCustomer(new ProjectContactContextApi.Query(1L,3L,7L)).id());
        assertEquals(99L, api.lockHistoricalFinalCustomer(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L)).id());
        verifyNoInteractions(customers);
    }
    @Test void finalCustomerUsesExactProjectPartyAndRefusesAmbiguityOrForeignTenant() {
        when(projectRoles.isTenantSuperAdmin(1L,3L)).thenReturn(true);var row=finalParty();
        assertEquals(99L,api.inspectFinalCustomer(new ProjectContactContextApi.Query(1L,3L,7L)).id());
        when(parties.selectHeaderFinalCustomers(any())).thenReturn(List.of(row,row));
        assertThrows(RuntimeException.class,()->api.inspectFinalCustomer(new ProjectContactContextApi.Query(1L,3L,7L)));
        when(parties.selectHeaderFinalCustomers(any())).thenReturn(List.of(row));row.setTenantId(2L);
        assertThrows(RuntimeException.class,()->api.lockHistoricalFinalCustomer(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L)));
    }
    @Test void finalCustomerHistoryDoesNotGrantManagementToAVisibleViewer() {
        finalParty();
        assertThrows(RuntimeException.class,()->api.lockHistoricalFinalCustomer(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2L)));
        verify(parties,never()).selectSourceForUpdate(any());
    }
    @Test void resolvesCodeOnlyCustomerForContactReadAndWriteWithoutChangingProject() {
        var row = projects.selectById(7L);
        row.setCustomerId(null); row.setCustomerCode("CUS-8");
        when(projectRoles.isTenantSuperAdmin(1L, 3L)).thenReturn(true);
        when(customers.getCustomerByCode(any())).thenReturn(new cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO(
                8L, 1L, "CUS-8", "客户", null, "ENABLED", "CRM_SYNC", 0L, null));
        assertEquals(8L, api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L)).customerId());
        assertEquals(8L, api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 2L)).customerId());
        verify(customers, times(2)).getCustomerByCode(new cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery("CUS-8", 3L));
        assertNull(row.getCustomerId());
        verify(projects, never()).associateCustomerIfMissing(any());
    }

    @Test void doesNotGuessMissingCustomerOrBypassCustomerScope() {
        var row = projects.selectById(7L);
        row.setCustomerId(null); row.setCustomerCode("CUS-8");
        assertThrows(RuntimeException.class, () -> api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L)));
        when(customers.getCustomerByCode(any())).thenThrow(new IllegalStateException("scope denied"));
        assertThrows(IllegalStateException.class, () -> api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L)));
        verify(projects, never()).associateCustomerIfMissing(any());
    }

    @Test void preservesStoredCustomerIdentityWithoutLookingUpCode() {
        assertEquals(8L, api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L)).customerId());
        verifyNoInteractions(customers);
    }

    @Test void keepsTrulyUnassociatedProjectAvailableForExplicitAssociation() {
        var row = projects.selectById(7L); row.setCustomerId(null);
        assertNull(api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L)).customerId());
        verifyNoInteractions(customers);
    }

    @Test void explicitAssociationPersistsTheCodeResolvedCustomerInsteadOfReturningAnUnwrittenSuccess() {
        var row = projects.selectById(7L); row.setCustomerId(null); row.setCustomerCode("CUS-8");
        when(projectRoles.isTenantSuperAdmin(1L, 3L)).thenReturn(true);
        var customer = new cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO(
                8L, 1L, "CUS-8", "客户", null, "ENABLED", "CRM_SYNC", 0L, null);
        when(customers.getCustomerByCode(any())).thenReturn(customer);
        when(customers.getCustomer(8L)).thenReturn(customer);
        when(projects.associateCustomerIfMissing(any())).thenReturn(1);
        var result = api.associateCustomer(new ProjectContactContextApi.AssociateQuery(1L, 3L, 7L, 2L, 8L));
        assertEquals(8L, result.customerId()); assertEquals(3L, result.projectVersion());
        verify(projects).associateCustomerIfMissing(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectContactCustomerUpdate(
                1L, 7L, 2L, 8L, "CUS-8", "客户", "3"));
    }

    @Test void explicitAssociationDoesNotRewriteAnExistingCustomerOrChangeAResolvedIdentity() {
        when(projectRoles.isTenantSuperAdmin(1L, 3L)).thenReturn(true);
        assertEquals(2L, api.associateCustomer(new ProjectContactContextApi.AssociateQuery(1L, 3L, 7L, 2L, 8L)).projectVersion());
        assertThrows(RuntimeException.class, () -> api.associateCustomer(
                new ProjectContactContextApi.AssociateQuery(1L, 3L, 7L, 2L, 9L)));
        verify(projects, never()).associateCustomerIfMissing(any());
    }
}
