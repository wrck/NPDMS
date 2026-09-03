package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.RecordAssetProductTypeSourceFailureCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProductTypeImportServiceTest {

    @Mock private SecurityFrameworkService securityFrameworkService;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private AssetProductTypeImportWriter importWriter;
    @Mock private AssetProductTypeSourceFailureWriter sourceFailureWriter;
    @Mock private AssetProductTypeConflictRecordService conflictRecordService;
    @Mock private AssetProductTypeAuditService auditService;

    private AssetProductTypeImportService service;

    @BeforeEach
    void setUp() {
        service = new AssetProductTypeImportService(securityFrameworkService, commandExecutionApi,
                importWriter, sourceFailureWriter, conflictRecordService, auditService);
        TenantContextHolder.setTenantId(1L);
        LoginUser user = new LoginUser();
        user.setId(9L);
        user.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectDirectServiceCallWithoutDedicatedPermissionBeforePersistence() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.importProductType(command()));

        verifyNoInteractions(commandExecutionApi, importWriter, conflictRecordService);
    }

    @Test
    void shouldRejectMissingAuthenticationBeforePermissionAndPersistence() {
        SecurityContextHolder.clearContext();

        assertThrows(RuntimeException.class, () -> service.importProductType(command()));

        verifyNoInteractions(securityFrameworkService, commandExecutionApi, importWriter,
                sourceFailureWriter, conflictRecordService, auditService);
    }

    @Test
    void shouldRejectMissingTenantBeforePlatformAndPersistence() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(true);
        TenantContextHolder.clear();

        assertThrows(RuntimeException.class, () -> service.importProductType(command()));

        verifyNoInteractions(commandExecutionApi, importWriter, sourceFailureWriter, conflictRecordService, auditService);
    }

    @Test
    void shouldRejectMismatchedLoginTenantBeforePlatformAndPersistence() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(true);
        TenantContextHolder.setTenantId(2L);

        assertThrows(RuntimeException.class, () -> service.importProductType(command()));

        verifyNoInteractions(commandExecutionApi, importWriter, sourceFailureWriter, conflictRecordService, auditService);
    }

    @Test
    void shouldRejectSourceFailureWithoutDedicatedPermissionBeforePersistence() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(false);
        RecordAssetProductTypeSourceFailureCommand failure =
                new RecordAssetProductTypeSourceFailureCommand("op-failure", "CRM", "source-1", "TIMEOUT");

        assertThrows(RuntimeException.class, () -> service.recordSourceFailure(failure));

        verifyNoInteractions(sourceFailureWriter, commandExecutionApi, importWriter, conflictRecordService, auditService);
    }

    @Test
    void shouldUseAuthenticatedActorAndTenantForPlatformIdempotency() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(true);
        ImportAssetProductTypeResult imported = new ImportAssetProductTypeResult(11L, 12L, "TYPE-A", false);
        when(importWriter.importOnce(1L, 9L, command())).thenReturn(imported);
        when(commandExecutionApi.execute(any(), anyString(), eq(ImportAssetProductTypeResult.class), any(), any()))
                .thenAnswer(invocation -> new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.NEW,
                        invocation.<Supplier<ImportAssetProductTypeResult>>getArgument(3).get()));

        ImportAssetProductTypeResult result = service.importProductType(command());

        assertEquals(11L, result.productTypeId());
        var scope = org.mockito.ArgumentCaptor.forClass(PlatformCommandExecutionApi.IdempotencyScope.class);
        verify(commandExecutionApi).execute(scope.capture(), anyString(), eq(ImportAssetProductTypeResult.class),
                any(), any());
        assertEquals(1L, scope.getValue().tenantId());
        assertEquals(9L, scope.getValue().actorId());
        assertEquals("idem-1", scope.getValue().key());
    }

    @Test
    void shouldReturnCompletedReplayWithoutCallingWriter() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(true);
        when(commandExecutionApi.execute(any(), anyString(), eq(ImportAssetProductTypeResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,
                        new ImportAssetProductTypeResult(11L, 12L, "TYPE-A", false)));

        ImportAssetProductTypeResult result = service.importProductType(command());

        assertTrue(result.replayed());
        verifyNoInteractions(importWriter, conflictRecordService);
    }

    @Test
    void shouldPersistConflictAfterBusinessTransactionFails() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(true);
        AssetProductTypeImportRejectedException conflict = AssetProductTypeImportRejectedException.sourceConflict(
                command(), "TYPE-B", LocalDateTime.of(2026, 8, 30, 9, 0));
        when(commandExecutionApi.execute(any(), anyString(), eq(ImportAssetProductTypeResult.class), any(), any()))
                .thenThrow(conflict);

        assertThrows(RuntimeException.class, () -> service.importProductType(command()));

        verify(conflictRecordService).record(1L, 9L, conflict);
        verify(auditService, never()).recordRejected(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldMapPlatformIdempotencyConflictWithoutWritingBusinessFacts() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(true);
        when(commandExecutionApi.execute(any(), anyString(), eq(ImportAssetProductTypeResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        assertThrows(RuntimeException.class, () -> service.importProductType(command()));

        verifyNoInteractions(importWriter, conflictRecordService);
        verify(auditService).recordRejected(eq(1L), eq(9L), eq("op-1"),
                eq("IDEMPOTENCY_CONFLICT"), anyString(), any());
    }

    @Test
    void shouldRecordSourceFailureWithoutCreatingReplacementFacts() {
        when(securityFrameworkService.hasPermission(AssetProductTypeImportService.CONTROLLED_IMPORT_PERMISSION))
                .thenReturn(true);
        RecordAssetProductTypeSourceFailureCommand failure =
                new RecordAssetProductTypeSourceFailureCommand("op-failure", "CRM", "source-1", "TIMEOUT");

        service.recordSourceFailure(failure);

        verify(sourceFailureWriter).markFailed(1L, 9L, failure);
        verifyNoInteractions(auditService);
        verifyNoInteractions(commandExecutionApi, importWriter, conflictRecordService);
    }

    private ImportAssetProductTypeCommand command() {
        return new ImportAssetProductTypeCommand(
                "op-1", "idem-1", "TYPE-A", "类型A", true,
                "CRM", "source-1", "v1", LocalDateTime.of(2026, 8, 30, 10, 0),
                "a".repeat(64), List.of());
    }
}
