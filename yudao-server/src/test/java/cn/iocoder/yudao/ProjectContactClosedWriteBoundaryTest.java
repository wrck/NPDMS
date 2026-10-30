package cn.iocoder.yudao;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ContactHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectContactHistoryState;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.*;
import cn.iocoder.yudao.module.pms.customer.service.contact.*;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.contact.*;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectAncestorQueryApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorResult;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** Real CUS service + PROJ Owner API; DAL/authorization providers are isolated mocks. */
class ProjectContactClosedWriteBoundaryTest {
    private final ProjectMasterMapper rows = mock(ProjectMasterMapper.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectAncestorQueryApi ancestors = mock(ProjectAncestorQueryApi.class);
    private final AuthorizationGrantApi grants = mock(AuthorizationGrantApi.class);
    private final CustomerQueryApi customers = mock(CustomerQueryApi.class);
    private final ProjectTreeScopeService roles = mock(ProjectTreeScopeService.class);
    private final ProjectManagementQualificationApi management = new ProjectManagementQualificationApiImpl(
            new ProjectParticipantFactApiImpl(rows,members), mock(ProjectRegionalManagerFactApi.class), rows,
            mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService.class));
    private final ProjectContactContextApiImpl api = new ProjectContactContextApiImpl(rows, members, scopes, ancestors, grants, customers, roles,mock(ProjectPartyMapper.class),management);
    private final CustomerContactMasterMapper masters = mock(CustomerContactMasterMapper.class);
    private final ProjectCustomerContactMapper contacts = mock(ProjectCustomerContactMapper.class);
    private final ContactHistoryMapper history = mock(ContactHistoryMapper.class);
    private final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    private final CustomerQueryService directory = mock(CustomerQueryService.class);
    private final CustomerScopeContextService directoryScopes = mock(CustomerScopeContextService.class);
    private final ProjectContactRuleWakeup wakeup = mock(ProjectContactRuleWakeup.class);
    private final ProjectCustomerContactService service = new ProjectCustomerContactService(api, masters, contacts, history, commands, directory, directoryScopes, wakeup);
    private final CustomerContactMasterService.Actor actor = new CustomerContactMasterService.Actor(1L, 3L);
    private final ProjectMasterDO project = new ProjectMasterDO();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(3L).setTenantId(1L), new MockHttpServletRequest());
        project.setId(7L); project.setTenantId(1L); project.setCustomerId(8L);
        project.setVersion(2L); project.setLifecycleStatus("ACTIVE");
        when(rows.selectById(7L)).thenReturn(project);
        when(rows.selectByIdForUpdate(7L)).thenReturn(project);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L, 4L, Set.of(7L), Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L, 4L, Set.of(7L), Set.of()));
        when(ancestors.getAncestors(any())).thenReturn(new ProjectAncestorResult(7L, 7L, 4L, List.of()));
        when(grants.listEffective(any())).thenReturn(List.of());
        var customer = new CustomerMasterDO(); customer.setId(8L); customer.setTenantId(1L); customer.setLifecycleStatus("ENABLED");
        when(directory.get(eq(1L), eq(8L), any())).thenReturn(customer);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }

    private void authority(String kind) {
        when(roles.isTenantSuperAdmin(1L, 3L)).thenReturn("ADMIN".equals(kind));
        var member = new ProjectMemberAssignmentDO(); member.setTenantId(1L);member.setProjectId(7L);member.setUserId(3L);
        member.setMemberRole("PROJECT_MANAGER");member.setAssignmentType("PRIMARY");
        member.setStatus("ACTIVE");member.setAuthorizationConfirmed(true);
        when(members.selectAuthorizationParticipantFacts(any())).thenReturn("PM".equals(kind) ? List.of(member) : List.of());
        when(members.selectAuthorizationParticipantFactsForUpdate(any())).thenReturn("PM".equals(kind) ? List.of(member) : List.of());
        if ("GRANT".equals(kind)) {
            var grant = mock(AuthorizationGrantDTO.class);
            when(grant.resourceId()).thenReturn(7L);
            when(grant.scopeCode()).thenReturn("PROJECT_ONLY");
            when(grants.listEffective(any())).thenReturn(List.of(grant));
        }
    }

    private void denied(org.junit.jupiter.api.function.Executable operation) {
        assertEquals(PROJECT_TREE_SCOPE_FORBIDDEN.getCode(), assertThrows(ServiceException.class, operation).getCode());
    }

    private void noContactEffects() { verifyNoInteractions(masters, contacts, history, commands, wakeup); }

    @ParameterizedTest
    @CsvSource({"NORMAL_CLOSED,PM", "NO_TRACKING_CLOSED,PM", "EXCEPTION_CLOSED,PM",
            "NORMAL_CLOSED,ADMIN", "NO_TRACKING_CLOSED,ADMIN", "EXCEPTION_CLOSED,ADMIN",
            "NORMAL_CLOSED,GRANT", "NO_TRACKING_CLOSED,GRANT", "EXCEPTION_CLOSED,GRANT"})
    void everyClosedLifecycleBlocksAllContactWritesAndCustomerAssociation(String status, String kind) {
        project.setLifecycleStatus(status); authority(kind);
        var context = api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L));
        assertFalse(context.canManage()); assertTrue(context.canViewHistory());
        denied(() -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 2L)));
        var values = new ContactValues("客户联系人", null, null, "13800138000", null, null, null);
        var edit = new ProjectContactWrite(7L, 10L, null, 2, 1, values, false, 0, true);
        denied(() -> service.create(actor, new ProjectContactWrite(7L, null, 9L, 2, null, null, false, 0, false), "reference"));
        denied(() -> service.create(actor, new ProjectContactWrite(7L, null, null, 2, null, values, false, 0, false), "new-master"));
        denied(() -> service.update(actor, edit));
        denied(() -> service.update(actor, new ProjectContactWrite(7L, 10L, null, 2, 1, values, false, 1, true)));
        denied(() -> service.delete(actor, edit));
        denied(() -> service.restore(actor, 7L, 10L, 2, 1, 0));
        denied(() -> service.restore(actor, 7L, 10L, 2, 1, 1));
        // Both already-bound idempotency and a missing binding must still pass the same write guard.
        denied(() -> service.associateCustomer(actor, 7L, 2, 8L));
        project.setCustomerId(null);
        denied(() -> service.associateCustomer(actor, 7L, 2, 8L));
        verify(rows, never()).associateCustomerIfMissing(any());
        verifyNoInteractions(customers);
        noContactEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PM", "ADMIN", "GRANT"})
    void activeLifecycleKeepsEveryExistingManagerRouteWritable(String kind) {
        authority(kind);
        assertTrue(api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L)).canManage());
        assertTrue(api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 2L)).canManage());
        project.setCustomerId(null);
        when(customers.getCustomer(8L)).thenReturn(new CustomerSummaryDTO(8L, 1L, "CUS-8", "客户", null, "ENABLED", "CRM_SYNC", 0L, null));
        when(rows.associateCustomerIfMissing(any())).thenReturn(1);
        var bound = service.associateCustomer(actor, 7L, 2, 8L);
        assertEquals(8L, bound.customerId()); assertEquals(3L, bound.projectVersion()); assertTrue(bound.canManage());
        verify(rows).associateCustomerIfMissing(argThat(update -> update.projectId().equals(7L) && update.customerId().equals(8L)));
        noContactEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NORMAL_CLOSED", "NO_TRACKING_CLOSED", "EXCEPTION_CLOSED"})
    void closedManagerReadsExistingAndHistoricalContactsButCannotRestoreFromHistory(String status) {
        authority("PM"); project.setLifecycleStatus(status);
        when(contacts.selectPage(any())).thenReturn(PageResult.empty());
        var entry = new ContactHistoryDO(); entry.setProjectRelationId(10L); entry.setActionCode("DELETE");
        entry.setBeforeValues("{\"id\":10,\"version\":2}");
        when(history.selectProjectPage(any())).thenReturn(new PageResult<>(List.of(entry), 1L));
        var state = new ProjectContactHistoryState(); state.setId(10L); state.setVersion(3); state.setRestorable(true);
        when(contacts.selectHistoryStates(any())).thenReturn(List.of(state));
        var read = service.page(actor, 7L, null, null, new PageParam());
        assertTrue(read.context().project().canViewHistory()); assertFalse(read.context().project().canManage());
        verify(contacts).selectPage(argThat(query -> query.status() == null));
        assertEquals(1L, service.history(actor, 7L, new PageParam()).getTotal());
        assertFalse(entry.getRestorable());
        assertThrows(ServiceException.class, () -> service.sources(actor, 7L, null, new PageParam()));
        verifyNoInteractions(masters, commands, wakeup);
        verify(history, never()).insert(any(ContactHistoryDO.class));
    }

    @Test void closingBetweenInspectAndWriteUsesLockedCurrentLifecycle() {
        authority("PM"); assertTrue(api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L)).canManage());
        var closed = new ProjectMasterDO(); closed.setId(7L); closed.setTenantId(1L); closed.setCustomerId(8L);
        closed.setVersion(2L); closed.setLifecycleStatus("EXCEPTION_CLOSED");
        when(rows.selectByIdForUpdate(7L)).thenReturn(closed);
        denied(() -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 2L)));
        verify(rows).selectByIdForUpdate(7L); noContactEffects();
    }

    @Test void activeWriteStillRejectsStaleMissingVersionTenantAndLostScope() {
        authority("PM");
        assertEquals(PROJECT_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 1L))).getCode());
        assertEquals(PROJECT_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, null))).getCode());
        denied(() -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(2L, 3L, 7L, 2L)));
        project.setTenantId(2L);
        denied(() -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 2L)));
        project.setTenantId(1L);
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L, 5L, Set.of(), Set.of()));
        denied(() -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 2L)));
        noContactEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "NORMAL_CLOSED", "NO_TRACKING_CLOSED", "EXCEPTION_CLOSED"})
    void ordinaryViewerGainsNeitherHistoryNorMaintenance(String status) {
        project.setLifecycleStatus(status);
        var context = api.inspect(new ProjectContactContextApi.Query(1L, 3L, 7L));
        assertFalse(context.canManage()); assertFalse(context.canViewHistory());
        denied(() -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L, 3L, 7L, 2L)));
        assertThrows(ServiceException.class, () -> service.history(actor, 7L, new PageParam()));
        noContactEffects();
    }
}
