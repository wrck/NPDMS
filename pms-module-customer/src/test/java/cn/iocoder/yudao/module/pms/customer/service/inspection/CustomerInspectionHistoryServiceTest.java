package cn.iocoder.yudao.module.pms.customer.service.inspection;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.inspection.CustomerInspectionHistoryApi.*;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.inspection.CustomerInspectionHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerInspectionHistoryServiceTest {
    @Mock CustomerInspectionHistoryMapper histories;
    @Mock CustomerInspectionReferenceMapper references;
    @Mock CustomerQueryApi customers;
    @Mock CustomerQueryService customerQueries;
    @Mock CustomerScopeContextService customerScopes;
    @Mock ProjectBusinessContextApi projects;
    @Mock ProjectScopeApi projectScopes;
    @Mock PermissionApi permissions;
    @InjectMocks CustomerInspectionHistoryService service;
    MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach void identity() {
        TenantContextHolder.setTenantId(1L);
        security = mockStatic(SecurityFrameworkUtils.class); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
    }
    @AfterEach void clear() { security.close(); TenantContextHolder.clear(); }

    @Test void crossTenantArchiveIsRejectedBeforeAnyOwnerLookup() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class, () -> service.register(command()));
        verifyNoInteractions(histories, references, projects, customers);
    }
    @Test void aServiceManagerOutsideTheProjectCannotRegisterHistory() {
        authorizeProject();
        when(projects.lockServiceManagers(any())).thenReturn(new ProjectBusinessContextApi.ServiceManagers(2L, List.of(8L)));
        assertThrows(RuntimeException.class, () -> service.register(command()));
        verifyNoInteractions(histories, references, customers);
    }
    @Test void missingCustomerScopePreventsRegistration() {
        authorizeProject();
        when(projects.lockServiceManagers(any())).thenReturn(new ProjectBusinessContextApi.ServiceManagers(2L, List.of(7L)));
        assertThrows(RuntimeException.class, () -> service.register(command()));
        verifyNoInteractions(histories, references);
    }
    @Test void exactRetryReusesImmutableHistoryAndChangedSourceIsRejected() {
        authorizeProject();
        when(projects.lockServiceManagers(any())).thenReturn(new ProjectBusinessContextApi.ServiceManagers(2L, List.of(7L)));
        when(customers.lockCustomerByCode(any())).thenReturn(new CustomerSummaryDTO(30L, 1L, "C30", "原客户", "原客户", "ENABLED", "LOCAL", 3L, null));
        var stored = new AtomicReference<CustomerInspectionHistoryDO>();
        when(histories.selectByTenantAndTask(1L, 10L)).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> { var row = invocation.<CustomerInspectionHistoryDO>getArgument(0); row.setId(100L); stored.set(row); return 1; })
                .when(histories).insert(any(CustomerInspectionHistoryDO.class));
        var first = service.register(command());
        assertEquals(first, service.register(command()));
        assertEquals(30L, stored.get().getCustomerId());
        // 当前项目客户已经变化，不将历史巡检偷偷迁至新客户。
        assertNotEquals(300L, stored.get().getCustomerId());
        var changed = ArchiveCommand.builder().tenantId(1L).actorId(7L).customerId(30L).customerCode("C30").projectId(20L)
                .taskId(10L).archivedTaskVersion(6L).taskCode("INS10").taskName("被改写的名称").reportId(40L)
                .references(command().references()).build();
        assertThrows(RuntimeException.class, () -> service.register(changed));
        verify(histories, times(1)).insert(any(CustomerInspectionHistoryDO.class));
        verify(references, times(2)).insert(any(cn.iocoder.yudao.module.pms.customer.dal.dataobject.inspection.CustomerInspectionReferenceDO.class));
    }
    @Test void duplicateOrMissingReportReferencesAreNotAccepted() {
        when(permissions.hasAnyPermissions(7L, "pms:inspection-task:archive")).thenReturn(true);
        var invalid = ArchiveCommand.builder().tenantId(1L).actorId(7L).customerId(30L).customerCode("C30").projectId(20L)
                .taskId(10L).archivedTaskVersion(6L).taskCode("INS10").taskName("巡检").reportId(40L)
                .references(List.of(new SourceReference(Kind.DEVICE, 50L))).build();
        assertThrows(IllegalArgumentException.class, () -> service.register(invalid));
        verifyNoInteractions(histories, references, projects, customers);
    }
    @Test void placeholderProjectCannotRegisterHistory() {
        when(permissions.hasAnyPermissions(7L, "pms:inspection-task:archive")).thenReturn(true);
        when(projectScopes.resolveCurrent(scopeQuery())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(), Set.of(20L)));
        assertThrows(RuntimeException.class, () -> service.register(command()));
        verifyNoInteractions(histories, references, projects, customers);
    }
    @Test void changedProjectScopeStopsArchiveBeforeAnyHistoryWrite() {
        when(permissions.hasAnyPermissions(7L, "pms:inspection-task:archive")).thenReturn(true);
        when(projectScopes.resolveCurrent(scopeQuery())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
        var conflict = new IllegalStateException("项目树版本已变化");
        when(projects.inspect(any())).thenAnswer(invocation -> {
            assertEquals(projectQuery(), invocation.getArgument(0));
            throw conflict;
        });
        assertSame(conflict, assertThrows(IllegalStateException.class, () -> service.register(command())));
        verify(projects, never()).lockAndRevalidate(any());
        verifyNoInteractions(histories, references, customers);
    }
    private void authorizeProject() {
        when(permissions.hasAnyPermissions(7L, "pms:inspection-task:archive")).thenReturn(true);
        when(projectScopes.resolveCurrent(scopeQuery())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
        var project = ProjectBusinessContextApi.Context.builder().tenantId(1L).projectId(20L).projectVersion(2L).scopeVersion(3L)
                .projectCode("P20").projectName("项目").customerId(300L).customerCode("C300").customerName("后来关联的客户").build();
        when(projects.inspect(any())).thenAnswer(invocation -> {
            assertEquals(projectQuery(), invocation.getArgument(0));
            return project;
        });
        when(projects.lockAndRevalidate(any())).thenAnswer(invocation -> {
            assertEquals(ProjectBusinessContextApi.Revalidation.builder().query(projectQuery()).expectedProjectVersion(2L).build(),
                    invocation.getArgument(0));
            return project;
        });
    }
    private ProjectCurrentScopeQuery scopeQuery() {
        return ProjectCurrentScopeQuery.builder().tenantId(1L).subjectUserId(7L).anchorProjectId(20L)
                .actionCode(ProjectScopeApi.ACTION_VIEW).build();
    }
    private ProjectBusinessContextApi.Query projectQuery() {
        return ProjectBusinessContextApi.Query.builder().tenantId(1L).actorId(7L).projectId(20L).expectedScopeVersion(3L).build();
    }
    private ArchiveCommand command() {
        return ArchiveCommand.builder().tenantId(1L).actorId(7L).customerId(30L).customerCode("C30").projectId(20L)
                .taskId(10L).archivedTaskVersion(6L).taskCode("INS10").taskName("巡检").reportId(40L)
                .references(List.of(new SourceReference(Kind.REPORT, 40L), new SourceReference(Kind.DEVICE, 50L))).build();
    }
}
