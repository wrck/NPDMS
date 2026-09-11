package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider.Source;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectContactCustomerUpdate;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

class ProjectCustomerCorrectionServiceTest {
    final ProjectManualCreationService projects = mock(ProjectManualCreationService.class);
    final ProjectMasterMapper mapper = mock(ProjectMasterMapper.class);
    final PermissionCommonApi permissions = mock(PermissionCommonApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final CustomerQueryApi customers = mock(CustomerQueryApi.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final List<ProjectCustomerReferenceProvider> providers = new ArrayList<>();
    ProjectMasterDO project;
    ProjectCustomerCorrectionService service;
    final ProjectCustomerCorrectionService.Actor actor = new ProjectCustomerCorrectionService.Actor(1L, 7L, "test");
    final ProjectCustomerCorrectionService.Command command = new ProjectCustomerCorrectionService.Command(10L, 3, "CUS-NEW", "录入更正", "key");

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(1L);
        project = new ProjectMasterDO(); project.setId(10L); project.setTenantId(1L); project.setVersion(3);
        project.setLifecycleStatus("ACTIVE"); project.setCustomerId(101L); project.setCustomerCode("CUS-OLD"); project.setCustomerName("原客户");
        when(permissions.hasAnyPermissions(7L, ProjectCustomerCorrectionService.PERMISSION)).thenReturn(true);
        when(projects.getProjectForManage(eq(10L), any())).thenReturn(project);
        when(mapper.selectByIdForUpdate(10L)).thenReturn(project);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 1L, Set.of(10L), Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L, 1L, Set.of(10L), Set.of()));
        when(customers.lockCustomerByCode(any())).thenReturn(new CustomerSummaryDTO(102L, 1L, "CUS-NEW", "新客户",
                null, "ENABLED", "PLATFORM", 1L, LocalDateTime.now()));
        when(mapper.correctCustomerIfMatch(any())).thenReturn(1);
        when(commands.execute(any(), anyString(), eq(ProjectCustomerCorrectionService.Result.class), any(), any()))
                .thenAnswer(call -> new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier<ProjectCustomerCorrectionService.Result>) call.getArgument(3)).get()));
        for (Source source : Source.values()) {
            var provider = mock(ProjectCustomerReferenceProvider.class);
            when(provider.source()).thenReturn(source);
            providers.add(provider);
        }
        service = new ProjectCustomerCorrectionService(projects, mapper, permissions, scopes, customers, commands, providers);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void correctsOnlyProjectIdentityAndReturnsBeforeAfterForAudit() {
        var result = service.correct(command, actor);
        assertTrue(result.changed()); assertEquals(4, result.version());
        assertEquals("CUS-OLD", result.previousCustomerCode()); assertEquals("CUS-NEW", result.customerCode());
        verify(mapper).correctCustomerIfMatch(new ProjectContactCustomerUpdate(1L, 10L, 3, 102L, "CUS-NEW", "新客户", "7"));
        assertEquals("CUS-OLD", project.getCustomerCode());
    }
    @Test void referencesAreReportedAndCannotBeOverriddenBySave() {
        when(providers.get(Source.CUSTOMER.ordinal()).countReferences(any())).thenReturn(2L);
        var inspection = service.inspect(10L, actor);
        assertFalse(inspection.canCorrect()); assertEquals(2L, inspection.references().getFirst().count());
        assertEquals(PROJECT_CUSTOMER_REFERENCED.getCode(), assertThrows(ServiceException.class,
                () -> service.correct(command, actor)).getCode());
        verify(mapper, never()).correctCustomerIfMatch(any()); verifyNoInteractions(customers);
    }
    @Test void providerUnavailableOrMissingFailsClosed() {
        when(providers.get(Source.ASSET.ordinal()).countReferences(any())).thenThrow(new IllegalStateException());
        assertEquals(PROJECT_CUSTOMER_REFERENCE_UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> service.correct(command, actor)).getCode());
        doReturn(0L).when(providers.get(Source.ASSET.ordinal())).countReferences(any());
        providers.removeLast();
        assertThrows(ServiceException.class, () -> service.inspect(10L, actor));
        verify(mapper, never()).correctCustomerIfMatch(any());
    }
    @Test void staleVersionAndClosedProjectCannotWrite() {
        project.setVersion(4);
        assertEquals(PROJECT_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.correct(command, actor)).getCode());
        project.setVersion(3); project.setLifecycleStatus("NORMAL_CLOSED");
        assertThrows(ServiceException.class, () -> service.correct(command, actor));
        verify(mapper, never()).correctCustomerIfMatch(any());
    }
    @Test void customerMustBeEnabledAndInCurrentTenant() {
        when(customers.lockCustomerByCode(any())).thenReturn(new CustomerSummaryDTO(102L, 2L, "CUS-NEW", "其他租户",
                null, "ENABLED", "PLATFORM", 1L, LocalDateTime.now()));
        assertEquals(PROJECT_CUSTOMER_UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> service.correct(command, actor)).getCode());
        verify(mapper, never()).correctCustomerIfMatch(any());
    }
    @Test void tenantAndPermissionAreCheckedBeforeIdempotencyOrReferences() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> service.correct(command, actor));
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(7L, ProjectCustomerCorrectionService.PERMISSION)).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.correct(command, actor));
        verifyNoInteractions(commands, customers);
    }
    @Test void replayDoesNotChangeProjectOrReadCustomersAgain() {
        var saved = new ProjectCustomerCorrectionService.Result(10L, 4, 101L, "CUS-OLD", "原客户", 102L,
                "CUS-NEW", "新客户", true, "录入更正");
        when(commands.execute(any(), anyString(), eq(ProjectCustomerCorrectionService.Result.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, saved));
        assertEquals(saved, service.correct(command, actor));
        verify(mapper, never()).correctCustomerIfMatch(any()); verifyNoInteractions(customers);
    }
    @Test void compareAndSetFailureIsNotReportedAsSaved() {
        when(mapper.correctCustomerIfMatch(any())).thenReturn(0);
        assertEquals(PROJECT_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.correct(command, actor)).getCode());
    }
}
