package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommerceAuthorityImportApplicationServiceTest {

    @Mock private CommerceAuthorityIngestApi authorityIngestApi;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    private CommerceAuthorityImportApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CommerceAuthorityImportApplicationService(
                authorityIngestApi, new AuthorityPayloadCanonicalizer(), commandExecutionApi);
    }

    @Test
    void shouldExecuteUnifiedBatchOnceWithSafeAuditFacts() {
        CommerceAuthorityBatchResult ownerResult = new CommerceAuthorityBatchResult(
                "op-1", "batch-1", CommerceAuthorityBatchResult.Decision.ACCEPTED);
        when(authorityIngestApi.ingestBatch(any())).thenReturn(ownerResult);
        executeNormally();

        CommerceAuthorityBatchResult result = service.execute(command("op-1", "40.000"), actor());

        assertSame(ownerResult, result);
        ArgumentCaptor<CommerceAuthorityBatchCommand> command = ArgumentCaptor.forClass(
                CommerceAuthorityBatchCommand.class);
        verify(authorityIngestApi).ingestBatch(command.capture());
        assertEquals("ERP", command.getValue().sourceSystem());
        assertEquals(new BigDecimal("40.000"), command.getValue().orderLines().getFirst().openQuantity());
        ArgumentCaptor<PlatformCommandExecutionApi.IdempotencyScope> scope = ArgumentCaptor.forClass(
                PlatformCommandExecutionApi.IdempotencyScope.class);
        verify(commandExecutionApi).execute(scope.capture(), anyString(), eq(CommerceAuthorityBatchResult.class),
                any(), any());
        assertEquals(new PlatformCommandExecutionApi.IdempotencyScope(
                0L, CommerceAuthorityImportApplicationService.SCOPE, 99L, "op-1"), scope.getValue());
    }

    @Test
    void shouldReturnEventReplayWithoutCallingOwnerAgain() {
        CommerceAuthorityBatchResult original = new CommerceAuthorityBatchResult(
                "op-1", "batch-1", CommerceAuthorityBatchResult.Decision.ACCEPTED);
        when(commandExecutionApi.execute(any(), anyString(), eq(CommerceAuthorityBatchResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, original));

        CommerceAuthorityBatchResult replay = service.execute(command("op-1", "40"), actor());

        assertEquals(CommerceAuthorityBatchResult.Decision.EVENT_REPLAYED, replay.decision());
        verifyNoInteractions(authorityIngestApi);
    }

    @Test
    void shouldRejectDifferentPayloadOrInProgressWithoutCallingOwner() {
        for (var decision : List.of(PlatformCommandExecutionApi.Decision.CONFLICT,
                PlatformCommandExecutionApi.Decision.IN_PROGRESS)) {
            reset(commandExecutionApi, authorityIngestApi);
            when(commandExecutionApi.execute(any(), anyString(), eq(CommerceAuthorityBatchResult.class), any(), any()))
                    .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(decision, null));

            ServiceException failure = assertThrows(ServiceException.class,
                    () -> service.execute(command("op-1", "40"), actor()));

            assertEquals(decision == PlatformCommandExecutionApi.Decision.CONFLICT
                    ? PLATFORM_COMMAND_KEY_CONFLICT.getCode() : PLATFORM_COMMAND_IN_PROGRESS.getCode(),
                    failure.getCode());
            verifyNoInteractions(authorityIngestApi);
        }
    }

    @Test
    void shouldRejectTenantOverrideBeforePlatformExecution() {
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(command(2L, "op-1", "40"), actor()));
        verifyNoInteractions(commandExecutionApi, authorityIngestApi);
    }

    @SuppressWarnings("unchecked")
    private void executeNormally() {
        when(commandExecutionApi.execute(any(), anyString(), eq(CommerceAuthorityBatchResult.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<CommerceAuthorityBatchResult> owner = invocation.getArgument(3);
                    Function<CommerceAuthorityBatchResult, PlatformCommandExecutionApi.SuccessFacts> facts =
                            invocation.getArgument(4);
                    CommerceAuthorityBatchResult result = owner.get();
                    PlatformCommandExecutionApi.SuccessFacts audit = facts.apply(result);
                    assertTrue(audit.detailSnapshot().contains("\"eventId\":\"op-1\""));
                    assertTrue(audit.detailSnapshot().contains("\"sourceSystem\":\"ERP\""));
                    assertFalse(audit.detailSnapshot().contains("ITEM-SECRET"));
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, result);
                });
    }

    private CommerceAuthorityBatchCommand command(String eventId, String openQuantity) {
        return command(0L, eventId, openQuantity);
    }

    private CommerceAuthorityBatchCommand command(Long tenantId, String eventId, String openQuantity) {
        LocalDateTime sourceTime = LocalDateTime.of(2026, 8, 29, 12, 0);
        CommerceSalesOrderFact order = new CommerceSalesOrderFact(
                "ORDER-1", null, "2", "DPTECH-DEMO", "FPROJ002-V18-ORDER", "SEED",
                null, null, null, null, CommerceSourceLifecycleStatus.ACTIVE, sourceTime);
        CommerceOrderLineFact line = new CommerceOrderLineFact(
                "LINE-A", null, "2", "ORDER-1", "LINE-AVAILABLE", "ITEM-SECRET", "设备",
                "F-COM001-PRODUCT-A", null, new BigDecimal("50"), new BigDecimal(openQuantity),
                BigDecimal.ZERO, "SET", 0, "CONFIRMED", CommerceSourceLifecycleStatus.ACTIVE, sourceTime);
        return new CommerceAuthorityBatchCommand(tenantId, eventId, "batch-1", "ERP", "wm-1",
                List.of(), List.of(order), List.of(line), List.of(), sourceTime, "corr-1");
    }

    private CommerceAuthorityImportApplicationService.Actor actor() {
        return new CommerceAuthorityImportApplicationService.Actor(0L, 99L);
    }
}
