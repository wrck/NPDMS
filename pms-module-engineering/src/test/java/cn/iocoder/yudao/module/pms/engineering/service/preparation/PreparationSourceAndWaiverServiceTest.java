package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.api.source.PreparationSourceFactProvider;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_SOURCE_UNAVAILABLE;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PreparationSourceAndWaiverServiceTest {

    @Test
    void waiverPageProjectsOnlyCurrentlyAuthorizedRowActions() {
        PreparationMapper preparationMapper = mock(PreparationMapper.class);
        PreparationItemMapper itemMapper = mock(PreparationItemMapper.class);
        PreparationItemWaiverMapper waiverMapper = mock(PreparationItemWaiverMapper.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        PreparationWaiverService service = new PreparationWaiverService(preparationMapper, itemMapper, waiverMapper,
                scopeApi, participantApi, permissionApi, mock(PlatformCommandExecutionApi.class),
                mock(OperationAuditApi.class), immediateTransaction());
        PreparationDO preparation = preparation();
        PreparationItemDO item = item();
        PreparationItemWaiverDO waiver = waiver();
        waiver.setPreparationId(99L);
        waiver.setStatusCode("DRAFT");
        waiver.setApplicantUserId(7L);
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(waiverMapper.selectPage(any())).thenReturn(List.of(waiver));
        when(permissionApi.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(participantApi.inspect(any())).thenReturn(new ProjectParticipantFact(
                10L, 7L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), null,
                "ACTIVE", "S1", 3, 4L));

        var page = service.page(1L, 2L, null, 20, actor(7L));

        assertEquals(99L, page.items().getFirst().preparationId());
        assertEquals(List.of("SUBMIT", "WITHDRAW"), page.items().getFirst().allowedActions());
    }

    @Test
    void waiverPageRequiresCurrentFrozenApprovalRoleAndExcludesApplicant() {
        PreparationMapper preparationMapper = mock(PreparationMapper.class);
        PreparationItemMapper itemMapper = mock(PreparationItemMapper.class);
        PreparationItemWaiverMapper waiverMapper = mock(PreparationItemWaiverMapper.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        PreparationWaiverService service = new PreparationWaiverService(preparationMapper, itemMapper, waiverMapper,
                scopeApi, participantApi, permissionApi, mock(PlatformCommandExecutionApi.class),
                mock(OperationAuditApi.class), immediateTransaction());
        PreparationItemWaiverDO applicant = waiver();
        applicant.setId(41L);
        applicant.setApplicantUserId(7L);
        PreparationItemWaiverDO reviewable = waiver();
        reviewable.setId(42L);
        reviewable.setPreparationId(99L);
        reviewable.setApplicantUserId(8L);
        when(preparationMapper.selectById(any())).thenReturn(preparation());
        when(itemMapper.selectList(any())).thenReturn(List.of(item()));
        when(waiverMapper.selectPage(any())).thenReturn(List.of(applicant, reviewable));
        when(permissionApi.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(participantApi.inspect(any())).thenReturn(new ProjectParticipantFact(
                10L, 7L, Set.of(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1), null,
                "ACTIVE", "S1", 3, 4L));

        var page = service.page(1L, 2L, null, 20, actor(7L));

        assertTrue(page.items().getFirst().allowedActions().isEmpty());
        assertEquals(List.of("APPROVE", "REJECT"), page.items().get(1).allowedActions());

        when(participantApi.inspect(any())).thenThrow(new IllegalStateException("participant unavailable"));
        assertTrue(service.page(1L, 2L, null, 20, actor(7L)).items().get(1).allowedActions().isEmpty());
    }

    @Test
    void registryRejectsMissingProviderAndAcceptsExactFact() {
        PreparationSourceFactQuery query = new PreparationSourceFactQuery(
                10L, 20L, "OA", "REQUEST", "OA-1", "REF-1", "{}");
        ServiceException missing = assertThrows(ServiceException.class,
                () -> new PreparationSourceProviderRegistry(List.of()).inspect(query));
        assertEquals(PREPARATION_SOURCE_UNAVAILABLE.getCode(), missing.getCode());

        PreparationSourceFact expected = new PreparationSourceFact(
                10L, 20L, "OA", "REQUEST", "OA-1", "REF-1", "APPROVED", "F1", "W1", true);
        PreparationSourceFactProvider provider = mock(PreparationSourceFactProvider.class);
        when(provider.sourceTypeCode()).thenReturn("OA");
        when(provider.inspect(query)).thenReturn(expected);
        assertEquals(expected, new PreparationSourceProviderRegistry(List.of(provider)).inspect(query));
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerFailureCommitsErrorFactAndRejectedAudit() {
        PreparationMapper preparationMapper = mock(PreparationMapper.class);
        PreparationItemMapper itemMapper = mock(PreparationItemMapper.class);
        PreparationSourceReferenceMapper sourceMapper = mock(PreparationSourceReferenceMapper.class);
        PreparationSourceProviderRegistry registry = mock(PreparationSourceProviderRegistry.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        PlatformCommandExecutionApi commandApi = mock(PlatformCommandExecutionApi.class);
        OperationAuditApi auditApi = mock(OperationAuditApi.class);
        TransactionTemplate transaction = immediateTransaction();
        PreparationSourceService service = new PreparationSourceService(preparationMapper, itemMapper, sourceMapper,
                registry, scopeApi, participantApi, permissionApi, commandApi, auditApi, transaction);
        PreparationDO preparation = preparation();
        PreparationItemDO item = item();
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation);
        when(itemMapper.selectForUpdate(any())).thenReturn(item);
        when(sourceMapper.selectListForUpdate(any())).thenReturn(List.of());
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(scopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(sourceMapper.insert(any())).thenAnswer(invocation -> {
            PreparationSourceReferenceDO row = invocation.getArgument(0); row.setId(31L); return 1;
        });
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        when(registry.inspect(any())).thenThrow(exception(PREPARATION_SOURCE_UNAVAILABLE));
        when(commandApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            return operation.get();
        });

        var command = new PreparationSourceService.SourceRefreshCommand(1L, 2L, 0, 0, 0, 0,
                null, 3, "OA", "REQUEST", "OA-1", "REF-1", "K1");
        assertThrows(ServiceException.class, () -> service.refresh(command, actor(7L)));

        ArgumentCaptor<PreparationSourceReferenceDO> inserted = ArgumentCaptor.forClass(PreparationSourceReferenceDO.class);
        verify(sourceMapper).insert(inserted.capture());
        assertEquals("ERROR", inserted.getValue().getSyncStatusCode());
        assertNull(inserted.getValue().getNormalizedResultCode());
        verify(auditApi).record(eq(0L), eq(7L), eq("C1"), eq("PREPARATION_SOURCE_REFRESH"),
                eq("PreparationSource"), eq("31"), eq("REJECTED"), any());
        verify(commandApi).execute(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulRefreshPersistsCurrentAndLastSuccessFacts() {
        PreparationMapper preparationMapper = mock(PreparationMapper.class);
        PreparationItemMapper itemMapper = mock(PreparationItemMapper.class);
        PreparationSourceReferenceMapper sourceMapper = mock(PreparationSourceReferenceMapper.class);
        PreparationSourceProviderRegistry registry = mock(PreparationSourceProviderRegistry.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        PlatformCommandExecutionApi commandApi = mock(PlatformCommandExecutionApi.class);
        OperationAuditApi auditApi = mock(OperationAuditApi.class);
        PreparationSourceService service = new PreparationSourceService(preparationMapper, itemMapper, sourceMapper,
                registry, scopeApi, participantApi, permissionApi, commandApi, auditApi, immediateTransaction());
        PreparationDO preparation = preparation();
        PreparationItemDO item = item();
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation);
        when(itemMapper.selectForUpdate(any())).thenReturn(item);
        when(sourceMapper.selectListForUpdate(any())).thenReturn(List.of());
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(scopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(sourceMapper.insert(any())).thenAnswer(invocation -> {
            PreparationSourceReferenceDO row = invocation.getArgument(0); row.setId(31L); return 1;
        });
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        when(registry.inspect(any())).thenReturn(new PreparationSourceFact(
                10L, 2L, "OA", "REQUEST", "OA-1", "REF-1", "ARRIVED", "F1", "W1", true));
        when(commandApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            Object response = operation.get();
            facts.apply(response);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, response);
        });

        var result = service.refresh(new PreparationSourceService.SourceRefreshCommand(1L, 2L, 0, 0, 0, 0,
                null, 3, "OA", "REQUEST", "OA-1", "REF-1", "K1"), actor(7L));

        assertTrue(result.succeeded());
        ArgumentCaptor<PreparationSourceReferenceDO> inserted = ArgumentCaptor.forClass(PreparationSourceReferenceDO.class);
        verify(sourceMapper).insert(inserted.capture());
        assertEquals("ARRIVED", inserted.getValue().getNormalizedResultCode());
        assertEquals("F1", inserted.getValue().getSourceFactVersion());
        assertEquals("W1", inserted.getValue().getSourceWatermark());
        assertEquals("ARRIVED", inserted.getValue().getLastSuccessResultCode());
        assertEquals("F1", inserted.getValue().getLastSuccessFactVersion());
        assertEquals("W1", inserted.getValue().getLastSuccessWatermark());
    }

    @Test
    @SuppressWarnings("unchecked")
    void laterProviderFailureClearsCurrentAndPreservesLastSuccess() {
        PreparationMapper preparationMapper = mock(PreparationMapper.class);
        PreparationItemMapper itemMapper = mock(PreparationItemMapper.class);
        PreparationSourceReferenceMapper sourceMapper = mock(PreparationSourceReferenceMapper.class);
        PreparationSourceProviderRegistry registry = mock(PreparationSourceProviderRegistry.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        PlatformCommandExecutionApi commandApi = mock(PlatformCommandExecutionApi.class);
        OperationAuditApi auditApi = mock(OperationAuditApi.class);
        PreparationSourceService service = new PreparationSourceService(preparationMapper, itemMapper, sourceMapper,
                registry, scopeApi, participantApi, permissionApi, commandApi, auditApi, immediateTransaction());
        PreparationDO preparation = preparation();
        PreparationItemDO item = item();
        PreparationSourceReferenceDO existing = new PreparationSourceReferenceDO();
        existing.setId(31L); existing.setPreparationId(1L); existing.setItemId(2L);
        existing.setSourceTypeCode("OA"); existing.setSourceObjectType("REQUEST");
        existing.setSourceObjectId("OA-1"); existing.setSourceReferenceKey("REF-1");
        existing.setSyncStatusCode("SYNCED"); existing.setNormalizedResultCode("ARRIVED");
        existing.setSourceFactVersion("F1"); existing.setSourceWatermark("W1");
        existing.setLastSuccessResultCode("ARRIVED"); existing.setLastSuccessFactVersion("F1");
        existing.setLastSuccessWatermark("W1"); existing.setLastSuccessAt(LocalDateTime.now().minusMinutes(5));
        existing.setVersion(4);
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation);
        when(itemMapper.selectForUpdate(any())).thenReturn(item);
        when(sourceMapper.selectListForUpdate(any())).thenReturn(List.of(existing));
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(scopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(sourceMapper.updateSyncIfMatch(any())).thenReturn(1);
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        when(registry.inspect(any())).thenThrow(exception(PREPARATION_SOURCE_UNAVAILABLE));
        when(commandApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            return operation.get();
        });

        var command = new PreparationSourceService.SourceRefreshCommand(1L, 2L, 0, 0, 0, 0,
                4, 3, "OA", "REQUEST", "OA-1", "REF-1", "K1");
        assertThrows(ServiceException.class, () -> service.refresh(command, actor(7L)));

        ArgumentCaptor<cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationSourceSyncUpdate> update =
                ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationSourceSyncUpdate.class);
        verify(sourceMapper).updateSyncIfMatch(update.capture());
        assertEquals("ERROR", update.getValue().syncStatusCode());
        assertNull(update.getValue().normalizedResultCode());
        assertNull(update.getValue().sourceFactVersion());
        assertNull(update.getValue().sourceWatermark());
        assertEquals("ARRIVED", update.getValue().lastSuccessResultCode());
        assertEquals("F1", update.getValue().lastSuccessFactVersion());
        assertEquals("W1", update.getValue().lastSuccessWatermark());
    }

    @Test
    void completedSourceRefreshReplaysBeforeMutableVersionChecks() {
        PreparationMapper preparationMapper = mock(PreparationMapper.class);
        PreparationItemMapper itemMapper = mock(PreparationItemMapper.class);
        PreparationSourceReferenceMapper sourceMapper = mock(PreparationSourceReferenceMapper.class);
        PreparationSourceProviderRegistry registry = mock(PreparationSourceProviderRegistry.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        PlatformCommandExecutionApi commandApi = mock(PlatformCommandExecutionApi.class);
        OperationAuditApi auditApi = mock(OperationAuditApi.class);
        PreparationSourceService service = new PreparationSourceService(preparationMapper, itemMapper, sourceMapper,
                registry, scopeApi, participantApi, permissionApi, commandApi, auditApi, immediateTransaction());
        when(preparationMapper.selectById(any())).thenReturn(preparation());
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(scopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        var replay = new PreparationSourceService.SourceRefreshResult(
                31L, 1L, 2L, "SYNCED", "APPROVED", "F1", "W1", 1, 1, 1, true, null);
        when(commandApi.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, replay));

        var command = new PreparationSourceService.SourceRefreshCommand(1L, 2L, 0, 0, 0, 0,
                null, 3, "OA", "REQUEST", "OA-1", "REF-1", "K1");

        assertEquals(replay, service.refresh(command, actor(7L)));
        verify(preparationMapper, never()).selectForUpdate(any());
        verifyNoInteractions(itemMapper, sourceMapper, registry);
    }

    @Test
    @SuppressWarnings("unchecked")
    void approvalUsesFrozenRoleAndWaiverVersionCas() {
        PreparationMapper preparationMapper = mock(PreparationMapper.class);
        PreparationItemMapper itemMapper = mock(PreparationItemMapper.class);
        PreparationItemWaiverMapper waiverMapper = mock(PreparationItemWaiverMapper.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        PlatformCommandExecutionApi commandApi = mock(PlatformCommandExecutionApi.class);
        OperationAuditApi auditApi = mock(OperationAuditApi.class);
        PreparationWaiverService service = new PreparationWaiverService(preparationMapper, itemMapper, waiverMapper,
                scopeApi, participantApi, permissionApi, commandApi, auditApi, immediateTransaction());
        PreparationDO preparation = preparation();
        PreparationItemDO item = item();
        PreparationItemWaiverDO waiver = waiver();
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation);
        when(itemMapper.selectForUpdate(any())).thenReturn(item);
        when(waiverMapper.selectBusinessListForUpdate(any())).thenReturn(List.of(waiver));
        when(permissionApi.hasAnyPermissions(7L, PreparationWaiverService.PERMISSION_APPROVE)).thenReturn(true);
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(scopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(waiverMapper.updateStatusIfMatch(any())).thenReturn(1);
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        when(commandApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            Object response = operation.get();
            facts.apply(response);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, response);
        });
        var command = new PreparationWaiverService.WaiverCommand("APPROVE", 1L, 2L, 41L,
                0, 0, 0, 0, 2, 3, List.of(), null, null, null,
                null, null, "accepted", "K2");

        var result = service.execute(command, actor(7L));

        assertEquals("APPROVED", result.status());
        ArgumentCaptor<ProjectParticipantFactRevalidationQuery> participant =
                ArgumentCaptor.forClass(ProjectParticipantFactRevalidationQuery.class);
        verify(participantApi).lockAndRevalidate(participant.capture());
        assertEquals(Set.of(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1),
                participant.getValue().requiredRoleCodes());
        verify(waiverMapper).updateStatusIfMatch(argThat(update -> Integer.valueOf(2).equals(update.expectedVersion())));
    }

    @Test
    void completedWaiverActionReplaysBeforeMutableVersionChecks() {
        PreparationMapper preparationMapper = mock(PreparationMapper.class);
        PreparationItemMapper itemMapper = mock(PreparationItemMapper.class);
        PreparationItemWaiverMapper waiverMapper = mock(PreparationItemWaiverMapper.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        PlatformCommandExecutionApi commandApi = mock(PlatformCommandExecutionApi.class);
        OperationAuditApi auditApi = mock(OperationAuditApi.class);
        PreparationWaiverService service = new PreparationWaiverService(preparationMapper, itemMapper, waiverMapper,
                scopeApi, participantApi, permissionApi, commandApi, auditApi, immediateTransaction());
        when(preparationMapper.selectById(any())).thenReturn(preparation());
        when(permissionApi.hasAnyPermissions(7L, PreparationWaiverService.PERMISSION_APPROVE)).thenReturn(true);
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        when(scopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        var replay = new PreparationWaiverService.WaiverResult(41L, 1, "APPROVED", 3, 1, 1);
        when(commandApi.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, replay));
        var command = new PreparationWaiverService.WaiverCommand("APPROVE", 1L, 2L, 41L,
                0, 0, 0, 0, 2, 3, List.of(), null, null, null,
                null, null, "accepted", "K2");

        assertEquals(replay, service.execute(command, actor(7L)));
        verify(preparationMapper, never()).selectForUpdate(any());
        verifyNoInteractions(itemMapper, waiverMapper, participantApi);
    }

    private static TransactionTemplate immediateTransaction() {
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        when(transaction.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        return transaction;
    }

    private static PreparationItemApplicationService.Actor actor(Long actorId) {
        return new PreparationItemApplicationService.Actor(0L, actorId, "C1");
    }

    private static PreparationDO preparation() {
        PreparationDO row = new PreparationDO();
        row.setId(1L); row.setProjectId(10L); row.setCurrentMarker(1); row.setVersion(0);
        row.setInputVersion(0); row.setReadinessVersion(0); row.setReadinessStatusCode("NOT_READY");
        return row;
    }

    private static PreparationItemDO item() {
        PreparationItemDO row = new PreparationItemDO();
        row.setId(2L); row.setPreparationId(1L); row.setItemCode("FIBER"); row.setVersion(0);
        row.setSourcePolicySnapshot("{\"requirementCode\":\"OA_REQUIRED\"}");
        row.setWaiverPolicySnapshot("{\"allowed\":true,\"approvalRoleCode\":\"SERVICE_MANAGER_L1\"}");
        return row;
    }

    private static PreparationItemWaiverDO waiver() {
        PreparationItemWaiverDO row = new PreparationItemWaiverDO();
        row.setId(41L); row.setPreparationId(1L); row.setItemId(2L); row.setItemCode("FIBER");
        row.setWaiverNo(1); row.setStatusCode("PENDING_APPROVAL"); row.setVersion(2);
        row.setApplicantUserId(8L); row.setApprovalRoleCode(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1);
        row.setBlockerCodesSnapshot("[\"SOURCE_PROVIDER_UNAVAILABLE\"]");
        row.setReason("reason"); row.setRisk("risk"); row.setCompensation("compensation");
        row.setValidFrom(LocalDateTime.now().minusHours(1)); row.setValidUntil(LocalDateTime.now().plusHours(1));
        return row;
    }
}
