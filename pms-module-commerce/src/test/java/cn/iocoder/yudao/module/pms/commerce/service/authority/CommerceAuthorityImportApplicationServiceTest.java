package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityWriteApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.AuthorityWriteResult;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityWriteCommand;
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

    @Mock private CommerceAuthorityWriteApi authorityWriteApi;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    private CommerceAuthorityImportApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CommerceAuthorityImportApplicationService(authorityWriteApi, commandExecutionApi);
    }

    @Test
    void shouldNormalizeAndExecuteOwnerOnceWithSafeAuditFacts() {
        AuthorityWriteResult ownerResult = new AuthorityWriteResult("batch-1", false, 0, 1, 1);
        when(authorityWriteApi.apply(any())).thenReturn(ownerResult);
        executeNormally();

        AuthorityWriteResult result = service.execute(command("op-1", "40.000"), actor());

        assertSame(ownerResult, result);
        ArgumentCaptor<CommerceAuthorityWriteCommand> command = ArgumentCaptor.forClass(
                CommerceAuthorityWriteCommand.class);
        verify(authorityWriteApi).apply(command.capture());
        assertEquals("ERP", command.getValue().salesOrderLines().getFirst().sourceSystem());
        assertEquals(new BigDecimal("40"), command.getValue().salesOrderLines().getFirst().openQuantity());
        ArgumentCaptor<PlatformCommandExecutionApi.IdempotencyScope> scope = ArgumentCaptor.forClass(
                PlatformCommandExecutionApi.IdempotencyScope.class);
        verify(commandExecutionApi).execute(scope.capture(), anyString(), eq(AuthorityWriteResult.class),
                any(), any());
        assertEquals(new PlatformCommandExecutionApi.IdempotencyScope(
                0L, CommerceAuthorityImportApplicationService.SCOPE, 99L, "op-1"), scope.getValue());
    }

    @Test
    void shouldReturnReplayWithoutCallingOwnerAgain() {
        AuthorityWriteResult original = new AuthorityWriteResult("batch-1", false, 0, 1, 1);
        when(commandExecutionApi.execute(any(), anyString(), eq(AuthorityWriteResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, original));

        AuthorityWriteResult replay = service.execute(command("op-1", "40"), actor());

        assertTrue(replay.replayed());
        verifyNoInteractions(authorityWriteApi);
    }

    @Test
    void shouldRejectDifferentPayloadOrInProgressWithoutCallingOwner() {
        for (var decision : List.of(PlatformCommandExecutionApi.Decision.CONFLICT,
                PlatformCommandExecutionApi.Decision.IN_PROGRESS)) {
            reset(commandExecutionApi, authorityWriteApi);
            when(commandExecutionApi.execute(any(), anyString(), eq(AuthorityWriteResult.class), any(), any()))
                    .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(decision, null));

            ServiceException failure = assertThrows(ServiceException.class,
                    () -> service.execute(command("op-1", "40"), actor()));

            assertEquals(decision == PlatformCommandExecutionApi.Decision.CONFLICT
                    ? PLATFORM_COMMAND_KEY_CONFLICT.getCode() : PLATFORM_COMMAND_IN_PROGRESS.getCode(),
                    failure.getCode());
            verifyNoInteractions(authorityWriteApi);
        }
    }

    @Test
    void shouldRejectEmptyBatchOrClientTenantOverrideBeforePlatformExecution() {
        CommerceAuthorityWriteCommand empty = new CommerceAuthorityWriteCommand(
                0L, "batch", "op", List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.execute(empty, actor()));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new CommerceAuthorityWriteCommand(
                        2L, "batch", "op", List.of(), command("op", "40").salesOrders(),
                        List.of()), actor()));
        verifyNoInteractions(commandExecutionApi, authorityWriteApi);
    }

    @SuppressWarnings("unchecked")
    private void executeNormally() {
        when(commandExecutionApi.execute(any(), anyString(), eq(AuthorityWriteResult.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<AuthorityWriteResult> owner = invocation.getArgument(3);
                    Function<AuthorityWriteResult, PlatformCommandExecutionApi.SuccessFacts> facts =
                            invocation.getArgument(4);
                    AuthorityWriteResult result = owner.get();
                    PlatformCommandExecutionApi.SuccessFacts audit = facts.apply(result);
                    assertTrue(audit.detailSnapshot().contains("\"operationId\":\"op-1\""));
                    assertTrue(audit.detailSnapshot().contains("\"sourceSystem\":\"ERP\""));
                    assertFalse(audit.detailSnapshot().contains("ITEM-SECRET"));
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, result);
                });
    }

    private CommerceAuthorityWriteCommand command(String operationId, String openQuantity) {
        LocalDateTime sourceTime = LocalDateTime.of(2026, 8, 29, 12, 0);
        return new CommerceAuthorityWriteCommand(0L, " batch-1 ", operationId, List.of(),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderSourceRecord(
                        " ERP ", " ORDER-1 ", " 2 ", " DPTECH-DEMO ", " SEED ",
                        " FPROJ002-V18-ORDER ", " ENABLED ", sourceTime)),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord(
                        " ERP ", " LINE-A ", " 2 ", " ORDER-1 ", " LINE-AVAILABLE ",
                        " ITEM-SECRET ", "设备", "F-COM001-PRODUCT-A", new BigDecimal("50"),
                        new BigDecimal(openQuantity), BigDecimal.ZERO, " SET ", 0,
                        " CONFIRMED ", " ENABLED ", sourceTime)));
    }

    private CommerceAuthorityImportApplicationService.Actor actor() {
        return new CommerceAuthorityImportApplicationService.Actor(0L, 99L);
    }
}
