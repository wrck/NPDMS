package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDisableUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection.InspectionRulePublicationLockProjection;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.audit.InspectionRulePublicationAuditService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleActionPermissionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionRulePublicationServiceImplTest {

    private static final long TENANT_ID = 7L;
    private static final long ACTOR_ID = 9L;
    private static final long RULE_ID = 10L;
    private static final long REVISION_ID = 20L;

    @Mock
    private InspectionRuleRevisionMapper revisionMapper;
    @Mock
    private PlatformCommandExecutionApi commandExecutionApi;
    @Mock
    private InspectionRulePublicationAuditService publicationAuditService;
    @Mock
    private InspectionRuleActionPermissionGuard permissionGuard;

    private InspectionRulePublicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InspectionRulePublicationServiceImpl(
                revisionMapper, commandExecutionApi, publicationAuditService, permissionGuard);
        TenantContextHolder.setTenantId(TENANT_ID);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(ACTOR_ID);
        loginUser.setTenantId(TENANT_ID);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectDisableBeforeDataAccessWithoutDedicatedPermission() {
        doThrow(new ServiceException(1_013_002_009, "forbidden"))
                .when(permissionGuard).checkDisable();

        assertThrows(ServiceException.class, () -> service.disable(
                new InspectionRulePublicationService.DisableCommand(REVISION_ID, 3, "key-1", "corr-1")));

        verify(commandExecutionApi, never()).execute(any(), any(), any(), any(), any());
        verify(revisionMapper, never()).selectById(any());
    }

    @Test
    void shouldDisableCurrentPublishedRevisionThroughPlatformCommand() {
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(revision("PUBLISHED", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(RULE_ID, 0, REVISION_ID, 3,
                        "PUBLISHED", REVISION_ID, 1, 3));
        when(revisionMapper.disablePublishedIfMatch(any())).thenReturn(1);
        executeNewCommand();

        InspectionRulePublicationService.DisableResult result = service.disable(
                new InspectionRulePublicationService.DisableCommand(REVISION_ID, 3, "key-1", "corr-1"));

        assertEquals(REVISION_ID, result.revisionId());
        assertEquals("DISABLED", result.statusCode());
        assertEquals(4, result.version());
        assertFalse(result.replayed());
        ArgumentCaptor<InspectionRuleDisableUpdate> update = ArgumentCaptor.forClass(InspectionRuleDisableUpdate.class);
        verify(revisionMapper).disablePublishedIfMatch(update.capture());
        assertEquals(TENANT_ID, update.getValue().tenantId());
        assertEquals(ACTOR_ID, update.getValue().disabledBy());
        ArgumentCaptor<PlatformCommandExecutionApi.IdempotencyScope> scope =
                ArgumentCaptor.forClass(PlatformCommandExecutionApi.IdempotencyScope.class);
        verify(commandExecutionApi).execute(scope.capture(), any(), eq(InspectionRulePublicationService.DisableResult.class),
                any(), any());
        assertEquals("INSPECTION_RULE_DISABLE", scope.getValue().scopeCode());
        assertEquals("key-1", scope.getValue().key());
    }

    @Test
    void shouldRejectCrossTenantRevisionAsNotFoundWithoutLifecycleWrite() {
        InspectionRuleRevisionDO foreignRevision = revision("PUBLISHED", 3);
        foreignRevision.setTenantId(TENANT_ID + 1);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(foreignRevision);
        executeNewCommand();

        ServiceException failure = assertThrows(ServiceException.class, () -> service.disable(
                new InspectionRulePublicationService.DisableCommand(
                        REVISION_ID, 3, "key-foreign", "corr-foreign")));

        assertEquals(1_013_002_005, failure.getCode());
        verify(revisionMapper, never()).selectPublicationLockForUpdate(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(publicationAuditService).recordRejected(eq(TENANT_ID), eq(ACTOR_ID), eq("corr-foreign"),
                eq("INSPECTION_RULE_DISABLE"), eq(String.valueOf(REVISION_ID)), any());
    }

    @Test
    void shouldFailClosedWhenPermissionDependencyFailsBeforePlatformCommandOrDataAccess() {
        doThrow(new IllegalStateException("PERMISSION_UNAVAILABLE_TEST"))
                .when(permissionGuard).checkDisable();

        assertThrows(IllegalStateException.class, () -> service.disable(
                new InspectionRulePublicationService.DisableCommand(
                        REVISION_ID, 3, "key-permission-error", "corr-permission-error")));

        verify(commandExecutionApi, never()).execute(any(), any(), any(), any(), any());
        verify(revisionMapper, never()).selectById(any());
        verify(publicationAuditService).recordRejected(eq(TENANT_ID), eq(ACTOR_ID), eq("corr-permission-error"),
                eq("INSPECTION_RULE_DISABLE"), eq(String.valueOf(REVISION_ID)), any());
    }

    @Test
    void shouldRejectStaleIfMatchWithoutLifecycleWriteAndRecordSafeAudit() {
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(revision("PUBLISHED", 4));
        executeNewCommand();

        ServiceException failure = assertThrows(ServiceException.class, () -> service.disable(
                new InspectionRulePublicationService.DisableCommand(REVISION_ID, 3, "key-stale", "corr-stale")));

        assertEquals(1_013_002_007, failure.getCode());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        ArgumentCaptor<Map<String, ?>> detail = mapCaptor();
        verify(publicationAuditService).recordRejected(eq(TENANT_ID), eq(ACTOR_ID), eq("corr-stale"),
                eq("INSPECTION_RULE_DISABLE"), eq(String.valueOf(REVISION_ID)), detail.capture());
        assertEquals(String.valueOf(1_013_002_007), detail.getValue().get("errorCode"));
        assertFalse(detail.getValue().containsKey("commandContent"));
        assertFalse(detail.getValue().containsKey("expectedResultRegex"));
    }

    @Test
    void shouldReplayCompletedDisableWithoutSecondLifecycleWriteOrSuccessAudit() {
        when(commandExecutionApi.execute(any(), any(), eq(InspectionRulePublicationService.DisableResult.class),
                any(), any())).thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,
                new InspectionRulePublicationService.DisableResult(REVISION_ID, "DISABLED", 4, false)));

        InspectionRulePublicationService.DisableResult result = service.disable(
                new InspectionRulePublicationService.DisableCommand(REVISION_ID, 3, "key-replay", "corr-replay"));

        assertTrue(result.replayed());
        verify(revisionMapper, never()).selectPublicationLockForUpdate(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(publicationAuditService, never()).recordRejected(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentPayloadAndRecordSafeAudit() {
        when(commandExecutionApi.execute(any(), any(), eq(InspectionRulePublicationService.DisableResult.class),
                any(), any())).thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                PlatformCommandExecutionApi.Decision.CONFLICT, null));

        ServiceException failure = assertThrows(ServiceException.class, () -> service.disable(
                new InspectionRulePublicationService.DisableCommand(REVISION_ID, 3, "key-conflict", "corr-conflict")));

        assertEquals(1_013_002_011, failure.getCode());
        verify(revisionMapper, never()).selectPublicationLockForUpdate(any());
        ArgumentCaptor<Map<String, ?>> detail = mapCaptor();
        verify(publicationAuditService).recordRejected(eq(TENANT_ID), eq(ACTOR_ID), eq("corr-conflict"),
                eq("INSPECTION_RULE_DISABLE"), eq(String.valueOf(REVISION_ID)), detail.capture());
        assertEquals(String.valueOf(1_013_002_011), detail.getValue().get("errorCode"));
        assertEquals(REVISION_ID, detail.getValue().get("revisionId"));
        assertFalse(detail.getValue().containsKey("idempotencyKey"));
    }

    @SuppressWarnings("unchecked")
    private void executeNewCommand() {
        doAnswer(invocation -> {
            Supplier<InspectionRulePublicationService.DisableResult> operation = invocation.getArgument(3);
            Function<InspectionRulePublicationService.DisableResult, PlatformCommandExecutionApi.SuccessFacts> facts =
                    invocation.getArgument(4);
            InspectionRulePublicationService.DisableResult response = operation.get();
            PlatformCommandExecutionApi.SuccessFacts successFacts = facts.apply(response);
            assertEquals("corr-1", successFacts.correlationId());
            assertFalse(successFacts.detailSnapshot().contains("show "));
            assertFalse(successFacts.detailSnapshot().contains("expectedResultRegex"));
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        }).when(commandExecutionApi).execute(any(), any(),
                eq(InspectionRulePublicationService.DisableResult.class), any(), any());
    }

    private InspectionRuleRevisionDO revision(String status, int version) {
        InspectionRuleRevisionDO revision = new InspectionRuleRevisionDO();
        revision.setId(REVISION_ID);
        revision.setRuleId(RULE_ID);
        revision.setTenantId(TENANT_ID);
        revision.setStatusCode(status);
        revision.setVersion(version);
        return revision;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, ?>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
