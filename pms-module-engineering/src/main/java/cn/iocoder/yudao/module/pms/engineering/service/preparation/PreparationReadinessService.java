package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFact;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.*;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormRules;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReadinessCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReadinessResult;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class PreparationReadinessService {

    private static final String IDEMPOTENCY_SCOPE = "PREPARATION_EVALUATE_READINESS";
    private static final int RULE_VERSION = 1;
    private static final String READY = "READY";
    private static final String NOT_READY = "NOT_READY";

    private final PreparationMapper preparationMapper;
    private final PreparationItemMapper itemMapper;
    private final DynamicFormInstanceMapper formMapper;
    private final PreparationSourceReferenceMapper sourceMapper;
    private final PreparationItemWaiverMapper waiverMapper;
    private final PreparationReadinessSnapshotMapper snapshotMapper;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final PermissionApi permissionApi;
    private final FileArtifactApi fileArtifactApi;
    private final PreparationSourceProviderRegistry sourceProviderRegistry;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;

    public PreparationReadinessResult evaluate(PreparationReadinessCommand command,
                                               PreparationItemApplicationService.Actor actor) {
        validate(command, actor);
        try {
            return transactionTemplate.execute(status -> evaluateInTransaction(command, actor));
        } catch (RuntimeException failure) {
            auditRejected(command, actor, failure);
            throw failure;
        }
    }

    private PreparationReadinessResult evaluateInTransaction(PreparationReadinessCommand command,
            PreparationItemApplicationService.Actor actor) {
        PreparationDO located = preparationMapper.selectById(
                new PreparationRowQuery(actor.tenantId(), command.preparationId()));
        if (located == null) throw exception(PREPARATION_NOT_EXISTS);
        ProjectScopeResult scope = authorizeAndLock(located.getProjectId(), command.expectedProjectVersion(), actor);
        AtomicReference<AuditFacts> audit = new AtomicReference<>();
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), IDEMPOTENCY_SCOPE, actor.actorId(), command.idempotencyKey()),
                digest(command), PreparationReadinessResult.class,
                () -> evaluateOnce(command, actor, located.getProjectId(), scope.treeVersion(), audit),
                response -> successFacts(actor, response, audit.get()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
        }
        return execution.response();
    }

    private PreparationReadinessResult evaluateOnce(PreparationReadinessCommand command,
            PreparationItemApplicationService.Actor actor, Long projectId, Long scopeVersion,
            AtomicReference<AuditFacts> audit) {
        LockedFacts locked = loadLocked(actor.tenantId(), command.preparationId());
        PreparationDO preparation = locked.preparation();
        if (!Objects.equals(preparation.getProjectId(), projectId)
                || !Objects.equals(preparation.getVersion(), command.expectedPreparationVersion())
                || !Integer.valueOf(1).equals(preparation.getCurrentMarker())) {
            throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
        }
        Evaluation evaluation = calculate(preparation, locked.items(), locked.forms(), locked.sources(),
                locked.waivers(), scopeVersion, true, false);
        PreparationReadinessSnapshotDO latest = latestSnapshot(actor.tenantId(), preparation);
        if (matchesSnapshot(latest, evaluation)) {
            SiteSurveyReadinessFact fact = response(preparation, latest, scopeVersion,
                    evaluation, Boolean.TRUE.equals(preparation.getSnapshotCurrent()));
            audit.set(new AuditFacts(preparation.getProjectId(), preparation.getId(), latest.getId(),
                    latest.getSnapshotNo(), preparation.getReadinessStatusCode(),
                    preparation.getReadinessStatusCode(), preparation.getReadinessVersion(),
                    preparation.getReadinessVersion(), evaluation.blockers(), true));
            return new PreparationReadinessResult(fact, true);
        }

        String result = evaluation.blockers().isEmpty() ? READY : NOT_READY;
        int snapshotNo = latest == null ? 1 : latest.getSnapshotNo() + 1;
        int versionAfter = preparation.getVersion() + 1;
        int readinessVersionAfter = preparation.getReadinessVersion() + 1;
        PreparationReadinessSnapshotDO snapshot = snapshot(actor, preparation, scopeVersion, evaluation,
                snapshotNo, result, versionAfter, readinessVersionAfter);
        if (snapshotMapper.insert(snapshot) != 1 || snapshot.getId() == null) {
            throw new IllegalStateException("PREPARATION_READINESS_SNAPSHOT_INSERT_FAILED");
        }
        if (preparationMapper.updateReadinessIfMatch(new PreparationReadinessUpdate(actor.tenantId(),
                preparation.getId(), preparation.getVersion(), preparation.getInputVersion(),
                preparation.getReadinessVersion(), result, snapshot.getId(), true,
                String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
        }
        preparation.setVersion(versionAfter);
        preparation.setReadinessVersion(readinessVersionAfter);
        preparation.setReadinessStatusCode(result);
        preparation.setLatestReadinessSnapshotId(snapshot.getId());
        preparation.setSnapshotCurrent(true);
        audit.set(new AuditFacts(projectId, preparation.getId(), snapshot.getId(), snapshotNo,
                latest == null ? NOT_READY : preparationStatusBefore(latest), result,
                readinessVersionAfter - 1, readinessVersionAfter, evaluation.blockers(), false));
        return new PreparationReadinessResult(response(preparation, snapshot, scopeVersion, evaluation, true), false);
    }

    public SiteSurveyReadinessFact inspect(SiteSurveyReadinessQuery query, Long tenantId, Long actorId) {
        requireTrusted(tenantId, actorId);
        PreparationDO preparation = locate(query, tenantId, false);
        ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorId, query.projectId(), ProjectScopeApi.ACTION_VIEW));
        requireScope(scope, query.projectId());
        UnlockedFacts facts = loadUnlocked(tenantId, preparation.getId());
        Evaluation evaluation = calculate(preparation, facts.items(), facts.forms(), facts.sources(),
                facts.waivers(), scope.treeVersion(), false, false);
        PreparationReadinessSnapshotDO latest = latestSnapshot(tenantId, preparation);
        boolean current = Boolean.TRUE.equals(preparation.getSnapshotCurrent())
                && matchesSnapshot(latest, evaluation);
        return response(preparation, latest, scope.treeVersion(), evaluation, current);
    }

    @Transactional
    public SiteSurveyReadinessFact lockAndRevalidate(SiteSurveyReadinessRevalidationQuery query,
                                                      Long tenantId, Long actorId) {
        requireTrusted(tenantId, actorId);
        ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                tenantId, actorId, query.projectId(), ProjectScopeApi.ACTION_VIEW,
                query.expectedProjectScopeVersion()));
        requireScope(scope, query.projectId());
        LockedFacts facts = loadLocked(tenantId, query.preparationId());
        PreparationDO preparation = facts.preparation();
        if (!Objects.equals(preparation.getProjectId(), query.projectId())
                || !Integer.valueOf(1).equals(preparation.getCurrentMarker())
                || !Objects.equals(preparation.getBusinessVersion(), query.expectedBusinessVersion())
                || !Objects.equals(preparation.getInputVersion(), query.expectedInputVersion())
                || !Objects.equals(preparation.getVersion(), query.expectedPreparationVersion())
                || !Objects.equals(preparation.getReadinessVersion(), query.expectedReadinessVersion())
                || !Objects.equals(preparation.getLatestReadinessSnapshotId(), query.expectedSnapshotId())) {
            throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
        }
        Evaluation evaluation = calculate(preparation, facts.items(), facts.forms(), facts.sources(),
                facts.waivers(), scope.treeVersion(), true, true);
        PreparationReadinessSnapshotDO latest = latestSnapshot(tenantId, preparation);
        if (latest == null || !READY.equals(preparation.getReadinessStatusCode())
                || !Boolean.TRUE.equals(preparation.getSnapshotCurrent()) || !evaluation.blockers().isEmpty()
                || !evaluation.vector().equals(query.expectedFactVector())
                || !matchesSnapshot(latest, evaluation)) {
            throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
        }
        return response(preparation, latest, scope.treeVersion(), evaluation, true);
    }

    private ProjectScopeResult authorizeAndLock(Long projectId, Integer projectVersion,
            PreparationItemApplicationService.Actor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PreparationInitializationService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        ProjectScopeResult current = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        requireScope(current, projectId);
        ProjectScopeResult locked = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE, current.treeVersion()));
        requireScope(locked, projectId);
        participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(projectId,
                actor.actorId(), projectVersion, "ACTIVE", null,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        return locked;
    }

    private Evaluation calculate(PreparationDO preparation, List<PreparationItemDO> items,
            List<DynamicFormInstanceDO> forms, List<PreparationSourceReferenceDO> sources,
            List<PreparationItemWaiverDO> waivers, Long scopeVersion,
            boolean locking, boolean failOnExternalError) {
        List<String> blockers = new ArrayList<>();
        if (!"CONFIRMED".equals(preparation.getStatusCode())) blockers.add("PREPARATION_NOT_CONFIRMED");
        Map<Long, DynamicFormInstanceDO> formByItem = new HashMap<>();
        for (DynamicFormInstanceDO form : forms) {
            if (formByItem.put(form.getItemId(), form) != null) blockers.add("FORM_AMBIGUOUS");
        }
        List<ReadinessItemFact> itemFacts = new ArrayList<>();
        List<ReadinessFileFact> fileFacts = new ArrayList<>();
        List<ReadinessSourceFact> sourceFacts = new ArrayList<>();
        for (PreparationItemDO item : items) {
            List<String> itemBlockers = new ArrayList<>();
            DynamicFormInstanceDO form = formByItem.get(item.getId());
            itemFacts.add(itemFact(item, form));
            boolean applicable = "REQUIRED".equals(item.getApplicabilityCode());
            boolean notApplicable = "NOT_APPLICABLE_CONFIRMED".equals(item.getApplicabilityCode());
            if (!"CONFIRMED".equals(item.getConfirmationStatusCode()) || !applicable && !notApplicable) {
                itemBlockers.add("ITEM_NOT_CONFIRMED");
            }
            if (!applicable) {
                blockers.addAll(itemBlockers);
                continue;
            }
            if (item.getAssigneeUserId() == null) itemBlockers.add("ASSIGNEE_REQUIRED");
            if (form == null || !"FROZEN".equals(form.getStatusCode()) || form.getFrozenAt() == null) {
                itemBlockers.add("FORM_NOT_FROZEN");
            } else {
                try {
                    FixedSurveyFormRules.validateAndNormalizeValue(form.getSchemaSnapshot(), form.getValueSnapshot());
                } catch (RuntimeException ignored) {
                    itemBlockers.add("FORM_INVALID");
                }
            }
            inspectFiles(item, fileFacts, itemBlockers, locking, failOnExternalError);
            inspectSource(preparation, item, sources, sourceFacts, itemBlockers, locking, failOnExternalError);
            applyWaivers(item, waivers, itemBlockers);
            blockers.addAll(itemBlockers);
        }
        if (items.isEmpty() || forms.size() != items.size()) blockers.add("ITEM_SET_INVALID");
        List<ReadinessWaiverFact> waiverFacts = waivers.stream().map(this::waiverFact).toList();
        List<String> stableBlockers = blockers.stream().distinct().sorted().toList();
        return new Evaluation(new ReadinessFactVector(preparation.getInputVersion(), scopeVersion,
                itemFacts, fileFacts, sourceFacts, waiverFacts), stableBlockers);
    }

    private void inspectSource(PreparationDO preparation, PreparationItemDO item,
            List<PreparationSourceReferenceDO> sources, List<ReadinessSourceFact> target,
            List<String> blockers, boolean locking, boolean failOnExternalError) {
        String requirement = policyText(item.getSourcePolicySnapshot(), "requirementCode");
        if ("NONE".equals(requirement)) return;
        List<PreparationSourceReferenceDO> itemSources = sources.stream()
                .filter(row -> Objects.equals(row.getItemId(), item.getId())).toList();
        if (itemSources.size() != 1) {
            blockers.add("SOURCE_PROVIDER_UNAVAILABLE");
            itemSources.forEach(row -> target.add(sourceFact(row)));
            return;
        }
        PreparationSourceReferenceDO row = itemSources.getFirst();
        if (!"SYNCED".equals(row.getSyncStatusCode()) || blank(row.getNormalizedResultCode())
                || blank(row.getSourceFactVersion()) || blank(row.getSourceWatermark())) {
            target.add(sourceFact(row));
            blockers.add("SOURCE_NOT_SYNCED");
            return;
        }
        try {
            PreparationSourceFact fact = locking
                    ? sourceProviderRegistry.lockAndRevalidate(new PreparationSourceFactRevalidationQuery(
                    preparation.getProjectId(), item.getId(), row.getSourceTypeCode(), row.getSourceObjectType(),
                    row.getSourceObjectId(), row.getSourceReferenceKey(), item.getSourcePolicySnapshot(),
                    row.getNormalizedResultCode(), row.getSourceFactVersion(), row.getSourceWatermark()))
                    : sourceProviderRegistry.inspect(new PreparationSourceFactQuery(preparation.getProjectId(),
                    item.getId(), row.getSourceTypeCode(), row.getSourceObjectType(), row.getSourceObjectId(),
                    row.getSourceReferenceKey(), item.getSourcePolicySnapshot()));
            target.add(sourceFact(row, fact));
            if (!Objects.equals(row.getNormalizedResultCode(), fact.normalizedResultCode())
                    || !Objects.equals(row.getSourceFactVersion(), fact.sourceFactVersion())
                    || !Objects.equals(row.getSourceWatermark(), fact.sourceWatermark())) {
                blockers.add("SOURCE_FACT_CHANGED");
            }
            if (!fact.requirementSatisfied()) blockers.add("SOURCE_RESULT_UNSATISFIED");
        } catch (RuntimeException failure) {
            if (failOnExternalError) throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
            target.add(sourceFact(row));
            blockers.add("SOURCE_PROVIDER_UNAVAILABLE");
        }
    }

    private void applyWaivers(PreparationItemDO item, List<PreparationItemWaiverDO> waivers,
            List<String> blockers) {
        LocalDateTime now = LocalDateTime.now();
        waivers.stream().filter(row -> item.getItemCode().equals(row.getItemCode()))
                .filter(row -> "APPROVED".equals(row.getStatusCode()))
                .filter(row -> !now.isBefore(row.getValidFrom()) && !now.isAfter(row.getValidUntil()))
                .forEach(row -> {
                    try {
                        List<String> waived = JsonUtils.parseArray(row.getBlockerCodesSnapshot(), String.class);
                        blockers.removeIf(waived::contains);
                    } catch (RuntimeException ignored) {
                        // 非法冻结豁免不产生替代效果。
                    }
                });
    }

    private void inspectFiles(PreparationItemDO item, List<ReadinessFileFact> target,
            List<String> blockers, boolean locking, boolean failOnExternalError) {
        boolean required = Boolean.TRUE.equals(policyBoolean(item.getEvidencePolicySnapshot(), "required"));
        List<EvidenceFact> frozen;
        try {
            frozen = blank(item.getEvidenceReferenceSnapshot()) ? List.of()
                    : JsonUtils.parseArray(item.getEvidenceReferenceSnapshot(), EvidenceFact.class);
        } catch (RuntimeException failure) {
            blockers.add("FILE_FACT_INVALID");
            return;
        }
        if (required && frozen.isEmpty()) blockers.add("EVIDENCE_REQUIRED");
        for (EvidenceFact fact : frozen) {
            try {
                FileArtifactVersionFact current = locking
                        ? fileArtifactApi.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(
                        fact.artifactId(), fact.versionNo(), PreparationFilePolicyProvider.OWNER_CONTEXT,
                        PreparationFilePolicyProvider.OBJECT_TYPE, String.valueOf(item.getId()),
                        PreparationFilePolicyProvider.PURPOSE_CODE, fact.referenceKey(), FileActionCodes.READ,
                        fact.fileFactVersion(), fact.scopeVersion()))
                        : fileArtifactApi.inspect(new FileArtifactVersionQuery(fact.artifactId(), fact.versionNo(),
                        PreparationFilePolicyProvider.OWNER_CONTEXT, PreparationFilePolicyProvider.OBJECT_TYPE,
                        String.valueOf(item.getId()), PreparationFilePolicyProvider.PURPOSE_CODE,
                        fact.referenceKey(), FileActionCodes.READ));
                if (!exact(fact, current)) {
                    blockers.add("FILE_FACT_CHANGED");
                }
                if (current != null) target.add(fileFact(item.getId(), current));
            } catch (RuntimeException failure) {
                if (failOnExternalError) throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
                blockers.add("FILE_FACT_UNAVAILABLE");
            }
        }
    }

    private boolean exact(EvidenceFact frozen, FileArtifactVersionFact current) {
        return current != null && Objects.equals(current.artifactId(), frozen.artifactId())
                && Objects.equals(current.versionNo(), frozen.versionNo())
                && Objects.equals(current.referenceKey(), frozen.referenceKey())
                && Objects.equals(current.fileFactVersion(), frozen.fileFactVersion())
                && Objects.equals(current.scopeVersion(), frozen.scopeVersion())
                && "AVAILABLE".equals(current.availabilityStatus())
                && "ACTIVE".equals(current.referenceStatus());
    }

    private LockedFacts loadLocked(Long tenantId, Long preparationId) {
        PreparationDO preparation = preparationMapper.selectForUpdate(new PreparationRowQuery(tenantId, preparationId));
        if (preparation == null) throw exception(PREPARATION_NOT_EXISTS);
        PreparationChildrenQuery query = new PreparationChildrenQuery(tenantId, preparationId);
        List<PreparationItemDO> items = itemMapper.selectListForUpdate(query);
        Set<String> itemCodes = items.stream().map(PreparationItemDO::getItemCode).collect(java.util.stream.Collectors.toSet());
        return new LockedFacts(preparation, items, formMapper.selectListForUpdate(query),
                sourceMapper.selectListForUpdate(query), waiverMapper.selectBusinessListForUpdate(
                new PreparationWaiverBusinessQuery(tenantId, preparation.getProjectId(), itemCodes)));
    }

    private UnlockedFacts loadUnlocked(Long tenantId, Long preparationId) {
        PreparationChildrenQuery query = new PreparationChildrenQuery(tenantId, preparationId);
        List<PreparationItemDO> items = itemMapper.selectList(query);
        Set<String> itemCodes = items.stream().map(PreparationItemDO::getItemCode).collect(java.util.stream.Collectors.toSet());
        PreparationDO preparation = preparationMapper.selectById(new PreparationRowQuery(tenantId, preparationId));
        if (preparation == null) throw exception(PREPARATION_NOT_EXISTS);
        return new UnlockedFacts(items, formMapper.selectList(query), sourceMapper.selectList(query),
                waiverMapper.selectBusinessList(new PreparationWaiverBusinessQuery(
                        tenantId, preparation.getProjectId(), itemCodes)));
    }

    private PreparationDO locate(SiteSurveyReadinessQuery query, Long tenantId, boolean lock) {
        PreparationDO row = query.preparationId() == null
                ? (lock ? preparationMapper.selectCurrentForUpdate(new PreparationCurrentQuery(tenantId,
                query.projectId(), PreparationInitializationService.PREPARATION_TYPE))
                : preparationMapper.selectCurrent(new PreparationCurrentQuery(tenantId,
                query.projectId(), PreparationInitializationService.PREPARATION_TYPE)))
                : (lock ? preparationMapper.selectForUpdate(new PreparationRowQuery(tenantId, query.preparationId()))
                : preparationMapper.selectById(new PreparationRowQuery(tenantId, query.preparationId())));
        if (row == null) throw exception(PREPARATION_NOT_EXISTS);
        if (!Objects.equals(row.getProjectId(), query.projectId()) || !Integer.valueOf(1).equals(row.getCurrentMarker())) {
            throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
        }
        return row;
    }

    private PreparationReadinessSnapshotDO latestSnapshot(Long tenantId, PreparationDO preparation) {
        if (preparation.getLatestReadinessSnapshotId() == null) return null;
        PreparationReadinessSnapshotDO row = snapshotMapper.selectById(new PreparationSnapshotRowQuery(
                tenantId, preparation.getId(), preparation.getLatestReadinessSnapshotId()));
        if (row == null) throw exception(PREPARATION_READINESS_VERSION_CONFLICT);
        return row;
    }

    private PreparationReadinessSnapshotDO snapshot(PreparationItemApplicationService.Actor actor,
            PreparationDO preparation, Long scopeVersion, Evaluation evaluation, int snapshotNo,
            String result, int preparationVersionAfter, int readinessVersionAfter) {
        PreparationReadinessSnapshotDO row = new PreparationReadinessSnapshotDO();
        row.setTenantId(actor.tenantId()); row.setPreparationId(preparation.getId()); row.setSnapshotNo(snapshotNo);
        row.setResultCode(result); row.setRuleVersion(RULE_VERSION); row.setProjectScopeVersion(scopeVersion);
        row.setInputVersion(preparation.getInputVersion()); row.setPreparationVersion(preparationVersionAfter);
        row.setReadinessVersion(readinessVersionAfter);
        row.setItemFactsSnapshot(JsonUtils.toJsonString(evaluation.vector().itemFacts()));
        row.setFileFactsSnapshot(JsonUtils.toJsonString(evaluation.vector().fileFacts()));
        row.setSourceFactsSnapshot(JsonUtils.toJsonString(evaluation.vector().sourceFacts()));
        row.setWaiverFactsSnapshot(JsonUtils.toJsonString(evaluation.vector().waiverFacts()));
        row.setBlockersSnapshot(JsonUtils.toJsonString(evaluation.blockers()));
        row.setEvaluatedBy(actor.actorId()); row.setEvaluatedAt(LocalDateTime.now());
        row.setCreator(String.valueOf(actor.actorId())); row.setCreateTime(LocalDateTime.now());
        return row;
    }

    private ReadinessFactVector vector(PreparationReadinessSnapshotDO snapshot) {
        return new ReadinessFactVector(snapshot.getInputVersion(), snapshot.getProjectScopeVersion(),
                JsonUtils.parseArray(snapshot.getItemFactsSnapshot(), ReadinessItemFact.class),
                JsonUtils.parseArray(snapshot.getFileFactsSnapshot(), ReadinessFileFact.class),
                JsonUtils.parseArray(snapshot.getSourceFactsSnapshot(), ReadinessSourceFact.class),
                JsonUtils.parseArray(snapshot.getWaiverFactsSnapshot(), ReadinessWaiverFact.class));
    }

    private boolean matchesSnapshot(PreparationReadinessSnapshotDO snapshot, Evaluation evaluation) {
        if (snapshot == null || !evaluation.vector().equals(vector(snapshot))) return false;
        try {
            List<String> frozenBlockers = JsonUtils.parseArray(snapshot.getBlockersSnapshot(), String.class);
            String result = evaluation.blockers().isEmpty() ? READY : NOT_READY;
            return evaluation.blockers().equals(frozenBlockers) && result.equals(snapshot.getResultCode());
        } catch (RuntimeException invalidSnapshot) {
            return false;
        }
    }

    private SiteSurveyReadinessFact response(PreparationDO preparation, PreparationReadinessSnapshotDO latest,
            Long scopeVersion, Evaluation evaluation, boolean current) {
        String result = current && latest != null && READY.equals(latest.getResultCode())
                && evaluation.blockers().isEmpty() ? READY : NOT_READY;
        return new SiteSurveyReadinessFact(preparation.getProjectId(), preparation.getId(),
                preparation.getBusinessVersion(), preparation.getStatusCode(), result,
                latest == null ? null : latest.getId(), latest == null ? null : latest.getSnapshotNo(),
                preparation.getInputVersion(), preparation.getVersion(), preparation.getReadinessVersion(),
                scopeVersion, current, evaluation.blockers(), evaluation.vector());
    }

    private ReadinessItemFact itemFact(PreparationItemDO item, DynamicFormInstanceDO form) {
        return new ReadinessItemFact(item.getId(), item.getItemCode(), item.getVersion(),
                item.getApplicabilityCode(), item.getConfirmationStatusCode(), item.getOutsourced(),
                item.getAssigneeUserId(), form == null ? null : form.getId(), form == null ? null : form.getFormCode(),
                form == null ? null : form.getFormVersion(), form == null ? null : form.getVersion(),
                form == null ? null : form.getStatusCode());
    }

    private ReadinessFileFact fileFact(Long itemId, FileArtifactVersionFact fact) {
        FileFactVersion version = fact.fileFactVersion();
        return new ReadinessFileFact(itemId, fact.artifactId(), fact.versionNo(), fact.referenceKey(),
                version.artifactVersion(), version.referenceVersion(), version.availabilityVersion(),
                fact.scopeVersion(), fact.availabilityStatus(), fact.referenceStatus());
    }

    private ReadinessSourceFact sourceFact(PreparationSourceReferenceDO row) {
        return new ReadinessSourceFact(row.getId(), row.getItemId(), row.getSourceTypeCode(),
                row.getSourceReferenceKey(), row.getNormalizedResultCode(), row.getSourceFactVersion(),
                row.getSourceWatermark(), row.getSyncStatusCode(), row.getVersion());
    }

    private ReadinessSourceFact sourceFact(PreparationSourceReferenceDO row, PreparationSourceFact fact) {
        return new ReadinessSourceFact(row.getId(), row.getItemId(), row.getSourceTypeCode(),
                row.getSourceReferenceKey(), fact.normalizedResultCode(), fact.sourceFactVersion(),
                fact.sourceWatermark(), row.getSyncStatusCode(), row.getVersion());
    }

    private ReadinessWaiverFact waiverFact(PreparationItemWaiverDO row) {
        return new ReadinessWaiverFact(row.getId(), row.getItemId(), row.getItemCode(), row.getWaiverNo(),
                row.getStatusCode(), row.getBlockerCodesSnapshot(), row.getValidFrom(), row.getValidUntil(),
                row.getVersion());
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(PreparationItemApplicationService.Actor actor,
            PreparationReadinessResult result, AuditFacts audit) {
        if (audit == null) throw new IllegalStateException("PREPARATION_READINESS_AUDIT_MISSING");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", audit.projectId()); detail.put("preparationId", audit.preparationId());
        detail.put("snapshotId", audit.snapshotId()); detail.put("snapshotNo", audit.snapshotNo());
        detail.put("readinessStatusBefore", audit.statusBefore()); detail.put("readinessStatusAfter", audit.statusAfter());
        detail.put("readinessVersionBefore", audit.readinessVersionBefore());
        detail.put("readinessVersionAfter", audit.readinessVersionAfter());
        detail.put("blockerCodes", audit.blockers()); detail.put("replayed", audit.replayed());
        detail.put("inputVersion", result.readiness().inputVersion());
        detail.put("preparationVersion", result.readiness().preparationVersion());
        detail.put("projectScopeVersion", result.readiness().projectScopeVersion());
        return new PlatformCommandExecutionApi.SuccessFacts("PREPARATION_EVALUATE_READINESS", "Preparation",
                String.valueOf(audit.preparationId()), actor.correlationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private void auditRejected(PreparationReadinessCommand command,
            PreparationItemApplicationService.Actor actor, RuntimeException failure) {
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "PREPARATION_EVALUATE_READINESS", "Preparation", String.valueOf(command.preparationId()),
                "REJECTED", Map.of("preparationId", command.preparationId(),
                        "failureCode", failure instanceof ServiceException se ? String.valueOf(se.getCode())
                                : "PREPARATION_READINESS_FAILED"));
    }

    private String digest(PreparationReadinessCommand command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    JsonUtils.toJsonString(Map.of("preparationId", command.preparationId(),
                            "expectedPreparationVersion", command.expectedPreparationVersion(),
                            "expectedProjectVersion", command.expectedProjectVersion()))
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private Boolean policyBoolean(String json, String key) {
        Object value = JsonUtils.parseObject(json, Map.class).get(key);
        return value instanceof Boolean flag ? flag : null;
    }

    private String policyText(String json, String key) {
        Object value = JsonUtils.parseObject(json, Map.class).get(key);
        return value instanceof String text && !text.isBlank() ? text : "INVALID";
    }

    private String preparationStatusBefore(PreparationReadinessSnapshotDO latest) {
        return latest.getResultCode() == null ? NOT_READY : latest.getResultCode();
    }

    private void requireScope(ProjectScopeResult scope, Long projectId) {
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(projectId)) throw exception(PREPARATION_PROJECT_FACT_INVALID);
    }

    private void requireTrusted(Long tenantId, Long actorId) {
        if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
    }

    private void validate(PreparationReadinessCommand command, PreparationItemApplicationService.Actor actor) {
        requireTrusted(actor == null ? null : actor.tenantId(), actor == null ? null : actor.actorId());
        if (command == null || command.preparationId() == null || command.preparationId() <= 0
                || command.expectedPreparationVersion() == null || command.expectedPreparationVersion() < 0
                || command.expectedProjectVersion() == null || command.expectedProjectVersion() < 0
                || blank(command.idempotencyKey())) throw exception(PREPARATION_COMMAND_INVALID);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record EvidenceFact(Long artifactId, Integer versionNo, String referenceKey,
                                FileFactVersion fileFactVersion, Long scopeVersion) {}
    private record Evaluation(ReadinessFactVector vector, List<String> blockers) {}
    private record LockedFacts(PreparationDO preparation, List<PreparationItemDO> items,
                               List<DynamicFormInstanceDO> forms, List<PreparationSourceReferenceDO> sources,
                               List<PreparationItemWaiverDO> waivers) {}
    private record UnlockedFacts(List<PreparationItemDO> items, List<DynamicFormInstanceDO> forms,
                                 List<PreparationSourceReferenceDO> sources,
                                 List<PreparationItemWaiverDO> waivers) {}
    private record AuditFacts(Long projectId, Long preparationId, Long snapshotId, Integer snapshotNo,
                              String statusBefore, String statusAfter, Integer readinessVersionBefore,
                              Integer readinessVersionAfter, List<String> blockers, boolean replayed) {}
}
