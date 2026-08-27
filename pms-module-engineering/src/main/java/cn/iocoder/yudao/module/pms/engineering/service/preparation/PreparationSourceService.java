package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFact;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSourceReferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationSourceReferenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class PreparationSourceService {

    private static final String SCOPE = "PREPARATION_SOURCE_REFRESH";
    private final PreparationMapper preparationMapper;
    private final PreparationItemMapper itemMapper;
    private final PreparationSourceReferenceMapper sourceMapper;
    private final PreparationSourceProviderRegistry providerRegistry;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final PermissionApi permissionApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;

    public SourceRefreshResult refresh(SourceRefreshCommand command, PreparationItemApplicationService.Actor actor) {
        require(command, actor);
        SourceRefreshResult result;
        try {
            result = transactionTemplate.execute(status -> refreshInTransaction(command, actor));
        } catch (SourceProviderFailure providerFailure) {
            try {
                transactionTemplate.execute(status -> {
                    persistProviderFailure(command, actor, providerFailure.getCause());
                    return null;
                });
            } catch (RuntimeException persistenceFailure) {
                auditRejected(command, actor, persistenceFailure);
                throw persistenceFailure;
            }
            throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        } catch (RuntimeException failure) {
            auditRejected(command, actor, failure);
            throw failure;
        }
        if (result == null || !result.succeeded()) throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        return result;
    }

    private SourceRefreshResult refreshInTransaction(SourceRefreshCommand command,
            PreparationItemApplicationService.Actor actor) {
        PreparationDO located = preparationMapper.selectById(new PreparationRowQuery(actor.tenantId(), command.preparationId()));
        if (located == null) throw exception(PREPARATION_NOT_EXISTS);
        authorize(located.getProjectId(), command.expectedProjectVersion(), actor);
        AtomicReference<PreparationSourceReferenceDO> before = new AtomicReference<>();
        AtomicReference<PreparationSourceFact> sourceFact = new AtomicReference<>();
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), SCOPE, actor.actorId(), command.idempotencyKey()),
                JsonUtils.toJsonString(command), SourceRefreshResult.class,
                () -> refreshOnce(command, actor, located.getProjectId(), before, sourceFact),
                response -> successFacts(actor, command, response, before.get(), sourceFact.get()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        return execution.response();
    }

    private SourceRefreshResult refreshOnce(SourceRefreshCommand command,
            PreparationItemApplicationService.Actor actor, Long projectId,
            AtomicReference<PreparationSourceReferenceDO> before,
            AtomicReference<PreparationSourceFact> sourceFact) {
        LockedSource locked = loadLocked(command, actor, projectId);
        before.set(locked.existing());
        PreparationSourceFact fact;
        try {
            fact = providerRegistry.inspect(new PreparationSourceFactQuery(locked.preparation().getProjectId(),
                    locked.item().getId(), command.sourceTypeCode(), command.sourceObjectType(),
                    command.sourceObjectId(), command.sourceReferenceKey(), locked.item().getSourcePolicySnapshot()));
        } catch (RuntimeException failure) {
            throw new SourceProviderFailure(failure);
        }
        sourceFact.set(fact);
        return persistSuccess(command, actor, locked.preparation(), locked.item(), locked.existing(), fact);
    }

    private void persistProviderFailure(SourceRefreshCommand command,
            PreparationItemApplicationService.Actor actor, RuntimeException failure) {
        PreparationDO located = preparationMapper.selectById(new PreparationRowQuery(actor.tenantId(), command.preparationId()));
        if (located == null) throw exception(PREPARATION_NOT_EXISTS);
        authorize(located.getProjectId(), command.expectedProjectVersion(), actor);
        LockedSource locked = loadLocked(command, actor, located.getProjectId());
        persistFailure(command, actor, locked.preparation(), locked.item(), locked.existing(), failure);
    }

    private LockedSource loadLocked(SourceRefreshCommand command,
            PreparationItemApplicationService.Actor actor, Long projectId) {
        PreparationDO preparation = preparationMapper.selectForUpdate(new PreparationRowQuery(actor.tenantId(), command.preparationId()));
        PreparationItemDO item = itemMapper.selectForUpdate(new PreparationItemRowQuery(
                actor.tenantId(), command.preparationId(), command.itemId()));
        if (preparation == null || item == null || !Integer.valueOf(1).equals(preparation.getCurrentMarker())
                || !Objects.equals(preparation.getProjectId(), projectId)
                || !Objects.equals(preparation.getVersion(), command.expectedPreparationVersion())
                || !Objects.equals(preparation.getInputVersion(), command.expectedInputVersion())
                || !Objects.equals(preparation.getReadinessVersion(), command.expectedReadinessVersion())
                || !Objects.equals(item.getVersion(), command.expectedItemVersion())) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        requireSourcePolicy(item, command.sourceTypeCode());
        List<PreparationSourceReferenceDO> matches = sourceMapper.selectListForUpdate(
                        new PreparationChildrenQuery(actor.tenantId(), preparation.getId())).stream()
                .filter(row -> Objects.equals(row.getItemId(), item.getId()))
                .filter(row -> command.sourceTypeCode().equals(row.getSourceTypeCode()))
                .filter(row -> command.sourceReferenceKey().equals(row.getSourceReferenceKey())).toList();
        if (matches.size() > 1) throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        PreparationSourceReferenceDO existing = matches.isEmpty() ? null : matches.getFirst();
        if (existing == null && command.expectedSourceVersion() != null
                || existing != null && !Objects.equals(existing.getVersion(), command.expectedSourceVersion())) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        if (existing != null && (!command.sourceObjectType().equals(existing.getSourceObjectType())
                || !command.sourceObjectId().equals(existing.getSourceObjectId()))) {
            throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        }
        return new LockedSource(preparation, item, existing);
    }

    private SourceRefreshResult persistSuccess(SourceRefreshCommand command,
            PreparationItemApplicationService.Actor actor, PreparationDO preparation, PreparationItemDO item,
            PreparationSourceReferenceDO existing, PreparationSourceFact fact) {
        LocalDateTime now = LocalDateTime.now();
        PreparationSourceReferenceDO row = existing == null ? newSource(command, actor, preparation, item) : existing;
        if (existing == null) {
            row.setSyncStatusCode("SYNCED"); row.setNormalizedResultCode(fact.normalizedResultCode());
            row.setSourceFactVersion(fact.sourceFactVersion()); row.setSourceWatermark(fact.sourceWatermark());
            row.setLastSuccessResultCode(fact.normalizedResultCode()); row.setLastSuccessFactVersion(fact.sourceFactVersion());
            row.setLastSuccessWatermark(fact.sourceWatermark()); row.setLastSuccessAt(now); row.setLastSyncedAt(now);
            if (sourceMapper.insert(row) != 1 || row.getId() == null) throw new IllegalStateException("PREPARATION_SOURCE_INSERT_FAILED");
        } else if (sourceMapper.updateSyncIfMatch(new PreparationSourceSyncUpdate(actor.tenantId(), preparation.getId(),
                item.getId(), row.getId(), row.getVersion(), "SYNCED", fact.normalizedResultCode(),
                fact.sourceFactVersion(), fact.sourceWatermark(), fact.normalizedResultCode(), fact.sourceFactVersion(),
                fact.sourceWatermark(), now, now, null, String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        invalidate(preparation, actor);
        return new SourceRefreshResult(row.getId(), preparation.getId(), item.getId(), "SYNCED",
                fact.normalizedResultCode(), fact.sourceFactVersion(), fact.sourceWatermark(),
                existing == null ? 0 : existing.getVersion() + 1, preparation.getVersion() + 1,
                preparation.getInputVersion() + 1, true, null);
    }

    private SourceRefreshResult persistFailure(SourceRefreshCommand command,
            PreparationItemApplicationService.Actor actor, PreparationDO preparation, PreparationItemDO item,
            PreparationSourceReferenceDO existing, RuntimeException failure) {
        LocalDateTime now = LocalDateTime.now();
        String failureCode = failure instanceof ServiceException service ? String.valueOf(service.getCode())
                : "SOURCE_PROVIDER_UNAVAILABLE";
        PreparationSourceReferenceDO row = existing == null ? newSource(command, actor, preparation, item) : existing;
        if (existing == null) {
            row.setSyncStatusCode("ERROR"); row.setLastSyncedAt(now); row.setLastSyncErrorCode(failureCode);
            if (sourceMapper.insert(row) != 1 || row.getId() == null) throw new IllegalStateException("PREPARATION_SOURCE_INSERT_FAILED");
        } else if (sourceMapper.updateSyncIfMatch(new PreparationSourceSyncUpdate(actor.tenantId(), preparation.getId(),
                item.getId(), row.getId(), row.getVersion(), "ERROR", null, null, null,
                row.getLastSuccessResultCode(), row.getLastSuccessFactVersion(), row.getLastSuccessWatermark(),
                row.getLastSuccessAt(), now, failureCode, String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        invalidate(preparation, actor);
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), SCOPE,
                "PreparationSource", String.valueOf(row.getId()), "REJECTED", Map.of(
                        "preparationId", preparation.getId(), "itemId", item.getId(),
                        "sourceReferenceKey", command.sourceReferenceKey(), "failureCode", failureCode));
        return new SourceRefreshResult(row.getId(), preparation.getId(), item.getId(), "ERROR", null, null, null,
                existing == null ? 0 : existing.getVersion() + 1, preparation.getVersion() + 1,
                preparation.getInputVersion() + 1, false, failureCode);
    }

    private PreparationSourceReferenceDO newSource(SourceRefreshCommand command,
            PreparationItemApplicationService.Actor actor, PreparationDO preparation, PreparationItemDO item) {
        PreparationSourceReferenceDO row = new PreparationSourceReferenceDO();
        row.setTenantId(actor.tenantId()); row.setPreparationId(preparation.getId()); row.setItemId(item.getId());
        row.setSourceTypeCode(command.sourceTypeCode()); row.setSourceObjectType(command.sourceObjectType());
        row.setSourceObjectId(command.sourceObjectId()); row.setSourceReferenceKey(command.sourceReferenceKey());
        row.setRequiredResultPolicySnapshot(item.getSourcePolicySnapshot()); row.setVersion(0);
        row.setCreator(String.valueOf(actor.actorId())); row.setUpdater(String.valueOf(actor.actorId()));
        return row;
    }

    private void invalidate(PreparationDO preparation, PreparationItemApplicationService.Actor actor) {
        if (preparationMapper.invalidateReadinessIfMatch(new PreparationInputInvalidationUpdate(actor.tenantId(),
                preparation.getId(), preparation.getVersion(), preparation.getInputVersion(),
                preparation.getReadinessVersion(), String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(PreparationItemApplicationService.Actor actor,
            SourceRefreshCommand command, SourceRefreshResult response, PreparationSourceReferenceDO before,
            PreparationSourceFact fact) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", fact.projectId()); detail.put("preparationId", command.preparationId());
        detail.put("itemId", command.itemId()); detail.put("sourceReferenceId", response.sourceReferenceId());
        detail.put("sourceVersionBefore", before == null ? "NONE" : before.getVersion());
        detail.put("sourceVersionAfter", response.sourceVersion()); detail.put("syncStatusAfter", response.syncStatus());
        detail.put("syncStatusBefore", before == null ? "NONE" : before.getSyncStatusCode());
        detail.put("preparationVersionBefore", response.preparationVersion() - 1);
        detail.put("preparationVersionAfter", response.preparationVersion());
        detail.put("inputVersionBefore", response.inputVersion() - 1);
        detail.put("inputVersionAfter", response.inputVersion());
        detail.put("normalizedResultCode", response.normalizedResultCode());
        detail.put("sourceFactVersion", response.sourceFactVersion()); detail.put("sourceWatermark", response.sourceWatermark());
        return new PlatformCommandExecutionApi.SuccessFacts(SCOPE, "PreparationSource",
                String.valueOf(response.sourceReferenceId()), actor.correlationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private void auditRejected(SourceRefreshCommand command, PreparationItemApplicationService.Actor actor,
            RuntimeException failure) {
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), SCOPE,
                "PreparationSource", "NEW", "REJECTED", Map.of(
                        "preparationId", command.preparationId(), "itemId", command.itemId(),
                        "sourceReferenceKey", command.sourceReferenceKey(),
                        "failureCode", failure instanceof ServiceException service
                                ? String.valueOf(service.getCode()) : "PREPARATION_SOURCE_REFRESH_FAILED"));
    }

    private void authorize(Long projectId, Integer projectVersion, PreparationItemApplicationService.Actor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PreparationInitializationService.PERMISSION_MANAGE)) throw exception(FORBIDDEN);
        ProjectScopeResult current = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        if (current == null || current.fullProjectIds() == null || !current.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.actorId(),
                projectId, ProjectScopeApi.ACTION_MANAGE, current.treeVersion()));
        participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(projectId, actor.actorId(),
                projectVersion, "ACTIVE", null, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
    }

    private void requireSourcePolicy(PreparationItemDO item, String sourceTypeCode) {
        Map<String, Object> policy = JsonUtils.parseObject(item.getSourcePolicySnapshot(), Map.class);
        if (policy == null || !"OA_REQUIRED".equals(policy.get("requirementCode")) || !"OA".equals(sourceTypeCode)) {
            throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        }
    }

    private void require(SourceRefreshCommand command, PreparationItemApplicationService.Actor actor) {
        if (command == null || actor == null || actor.tenantId() == null || actor.actorId() == null
                || command.preparationId() == null || command.preparationId() <= 0 || command.itemId() == null
                || command.itemId() <= 0 || command.expectedPreparationVersion() == null
                || command.expectedInputVersion() == null || command.expectedReadinessVersion() == null
                || command.expectedItemVersion() == null || command.expectedProjectVersion() == null
                || blank(command.sourceTypeCode()) || blank(command.sourceObjectType()) || blank(command.sourceObjectId())
                || blank(command.sourceReferenceKey()) || blank(command.idempotencyKey())) throw exception(PREPARATION_COMMAND_INVALID);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record SourceRefreshCommand(Long preparationId, Long itemId, Integer expectedPreparationVersion,
            Integer expectedInputVersion, Integer expectedReadinessVersion, Integer expectedItemVersion,
            Integer expectedSourceVersion, Integer expectedProjectVersion, String sourceTypeCode,
            String sourceObjectType, String sourceObjectId, String sourceReferenceKey, String idempotencyKey) {}

    public record SourceRefreshResult(Long sourceReferenceId, Long preparationId, Long itemId, String syncStatus,
            String normalizedResultCode, String sourceFactVersion, String sourceWatermark, Integer sourceVersion,
            Integer preparationVersion, Integer inputVersion, boolean succeeded, String failureCode) {}

    private record LockedSource(PreparationDO preparation, PreparationItemDO item,
                                PreparationSourceReferenceDO existing) {}

    private static final class SourceProviderFailure extends RuntimeException {
        private SourceProviderFailure(RuntimeException cause) {
            super(cause);
        }

        @Override
        public synchronized RuntimeException getCause() {
            return (RuntimeException) super.getCause();
        }
    }
}
