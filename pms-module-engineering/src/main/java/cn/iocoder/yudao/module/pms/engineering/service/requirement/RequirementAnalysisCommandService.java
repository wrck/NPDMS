package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.xss.core.clean.JsoupXssCleaner;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisAttachmentReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisCompleteUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisContentUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisEffectiveClearUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionListQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionPatchUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionRowQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.requirement.RequirementAnalysisCatalog;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionItem;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ExistingFileReferenceTarget;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_CONTENT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_DRAFT_CONFLICT;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_FILE_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_VERSION_NOT_MATCH;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;

@Service
@RequiredArgsConstructor
@Deprecated // 使用RequirementAnalysisDynamicFormCommandService；固定章节写模型不得扩展。
public class RequirementAnalysisCommandService {

    private static final String ACTIVE = "ACTIVE";
    private static final String INITIAL_STAGE = "S1";
    private static final String OWNER_CONTEXT = "SOL";
    private static final String OBJECT_TYPE = "REQUIREMENT_ANALYSIS_SECTION";
    private static final String PURPOSE = "SECTION_ATTACHMENT";

    private final PreparationMapper preparationMapper;
    private final RequirementAnalysisRootMapper rootMapper;
    private final RequirementAnalysisSectionMapper sectionMapper;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final FileArtifactApi fileArtifactApi;
    private final PermissionApi permissionApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final XssCleaner xssCleaner = new JsoupXssCleaner();
    private final TransactionTemplate transactionTemplate;

    public CommandResult createInitial(CreateCommand command, Actor actor) {
        try {
            requireCreate(command, actor);
        } catch (RuntimeException failure) {
            auditRejected(actor, "REQUIREMENT_ANALYSIS_INITIALIZE", null,
                    command == null ? null : command.projectId(), failure);
            throw failure;
        }
        return executeSafely(actor, "REQUIREMENT_ANALYSIS_INITIALIZE", command.idempotencyKey(), command,
                null, command.projectId(),
                () -> createInitialInTransaction(command, actor));
    }

    public CommandResult patch(PatchCommand command, Actor actor) {
        try {
            requirePatch(command, actor);
        } catch (RuntimeException failure) {
            auditRejected(actor, "REQUIREMENT_ANALYSIS_PATCH",
                    command == null ? null : command.preparationId(), null, failure);
            throw failure;
        }
        try {
            return transactionTemplate.execute(status -> patchInTransaction(command, actor));
        } catch (RuntimeException failure) {
            auditRejected(actor, "REQUIREMENT_ANALYSIS_PATCH", command.preparationId(), null, failure);
            throw failure;
        }
    }

    public CommandResult complete(CompleteCommand command, Actor actor) {
        try {
            requireComplete(command, actor);
        } catch (RuntimeException failure) {
            auditRejected(actor, "REQUIREMENT_ANALYSIS_COMPLETE",
                    command == null ? null : command.preparationId(), null, failure);
            throw failure;
        }
        return executeSafely(actor, "REQUIREMENT_ANALYSIS_COMPLETE", command.idempotencyKey(), command,
                command.preparationId(), null,
                () -> completeInTransaction(command, actor));
    }

    public CommandResult createRevision(CreateRevisionCommand command, Actor actor) {
        try {
            requireRevision(command, actor);
        } catch (RuntimeException failure) {
            auditRejected(actor, "REQUIREMENT_ANALYSIS_CREATE_DRAFT",
                    command == null ? null : command.preparationId(), null, failure);
            throw failure;
        }
        return executeSafely(actor, "REQUIREMENT_ANALYSIS_CREATE_DRAFT", command.idempotencyKey(), command,
                command.preparationId(), null,
                () -> createRevisionInTransaction(command, actor));
    }

    private CommandOutcome createInitialInTransaction(CreateCommand command, Actor actor) {
        Authorization authorization = lockManager(command.projectId(), actor, true, true,
                command.expectedProjectVersion());
        RequirementAnalysisProjectQuery projectQuery = new RequirementAnalysisProjectQuery(
                actor.tenantId(), command.projectId());
        if (rootMapper.selectDraftForUpdate(projectQuery) != null
                || rootMapper.selectEffectiveForUpdate(projectQuery) != null) {
            throw exception(REQUIREMENT_ANALYSIS_DRAFT_CONFLICT);
        }
        List<RequirementAnalysisCatalog.SectionDefinition> definitions = parseBinding(authorization.binding());
        PreparationDO root = newRoot(actor, authorization.binding(), command.projectId(), 1, null);
        insertRoot(root);
        insertSections(actor, root, definitions, null);
        return outcome(root, new AuditTransition(null, root.getId(), null, null,
                AuditState.empty(), AuditState.of(root)));
    }

    private CommandResult patchInTransaction(PatchCommand command, Actor actor) {
        PreparationDO inspected = rootMapper.selectById(
                new RequirementAnalysisRowQuery(actor.tenantId(), command.preparationId()));
        if (inspected == null) throw exception(REQUIREMENT_STATUS_INVALID);
        lockManager(inspected.getProjectId(), actor, false, false, command.expectedProjectVersion());
        PreparationDO root = rootMapper.selectForUpdate(
                new RequirementAnalysisRowQuery(actor.tenantId(), command.preparationId()));
        requireDraft(root, command.expectedPreparationVersion(), command.expectedContentVersion());
        RequirementAnalysisSectionDO section = sectionMapper.selectForUpdate(new RequirementAnalysisSectionRowQuery(
                actor.tenantId(), root.getId(), command.sectionId()));
        if (section == null) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        boolean updateValue = command.submittedFields().contains("value");
        boolean updateAttachments = command.submittedFields().contains("attachments");
        if (!updateValue && !updateAttachments || command.submittedFields().stream()
                .anyMatch(field -> !Set.of("value", "attachments").contains(field))) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
        String normalizedValue = updateValue ? normalizeValue(section, command.value()) : section.getValueSnapshot();
        String normalizedAttachments = updateAttachments
                ? lockSubmittedAttachmentSet(section, command.attachments())
                : section.getAttachmentReferenceSnapshot();
        if (Objects.equals(normalizedValue, section.getValueSnapshot())
                && Objects.equals(normalizedAttachments, section.getAttachmentReferenceSnapshot())) {
            return result(root);
        }
        String actorText = String.valueOf(actor.actorId());
        if (sectionMapper.patchIfMatch(new RequirementAnalysisSectionPatchUpdate(
                actor.tenantId(), root.getId(), section.getId(), section.getVersion(),
                updateValue, normalizedValue, updateAttachments, normalizedAttachments, actorText)) != 1
                || rootMapper.incrementContentIfMatch(new RequirementAnalysisContentUpdate(
                actor.tenantId(), root.getId(), root.getVersion(), root.getContentVersion(), actorText)) != 1) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        root.setVersion(root.getVersion() + 1);
        root.setContentVersion(root.getContentVersion() + 1);
        recordPatchSuccess(command, root, section, normalizedAttachments, actor);
        return result(root);
    }

    private CommandOutcome completeInTransaction(CompleteCommand command, Actor actor) {
        PreparationDO inspected = rootMapper.selectById(
                new RequirementAnalysisRowQuery(actor.tenantId(), command.preparationId()));
        if (inspected == null) throw exception(REQUIREMENT_STATUS_INVALID);
        RequirementAnalysisProjectQuery projectQuery = new RequirementAnalysisProjectQuery(
                actor.tenantId(), inspected.getProjectId());
        PreparationDO currentEffectiveBeforeLock = rootMapper.selectEffective(projectQuery);
        Authorization authorization = lockManager(inspected.getProjectId(), actor,
                currentEffectiveBeforeLock == null, true, command.expectedProjectVersion());
        PreparationDO root = rootMapper.selectForUpdate(
                new RequirementAnalysisRowQuery(actor.tenantId(), command.preparationId()));
        requireDraft(root, command.expectedVersion(), command.expectedContentVersion());
        PreparationDO effective = rootMapper.selectEffectiveForUpdate(projectQuery);
        List<RequirementAnalysisSectionDO> sections = sectionMapper.selectListForUpdate(
                new RequirementAnalysisSectionListQuery(actor.tenantId(), root.getId()));
        requireCompleteContent(sections);
        lockAllAttachmentSets(sections);
        AuditState before = AuditState.of(root);
        Long effectiveBefore = effective == null ? null : effective.getId();
        String actorText = String.valueOf(actor.actorId());
        if (effective != null && rootMapper.clearEffectiveIfMatch(new RequirementAnalysisEffectiveClearUpdate(
                actor.tenantId(), effective.getId(), effective.getVersion(), actorText)) != 1) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        LocalDateTime completedAt = LocalDateTime.now();
        if (rootMapper.completeDraftIfMatch(new RequirementAnalysisCompleteUpdate(actor.tenantId(), root.getId(),
                root.getVersion(), root.getContentVersion(), actor.actorId(), completedAt, actorText)) != 1) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        root.setStatusCode("COMPLETED");
        root.setDraftMarker(null);
        root.setEffectiveMarker(1);
        root.setCompletedBy(actor.actorId());
        root.setCompletedAt(completedAt);
        root.setVersion(root.getVersion() + 1);
        requireBindingMatchesRoot(authorization.binding(), root);
        return outcome(root, new AuditTransition(root.getId(), null, effectiveBefore, root.getId(),
                before, AuditState.of(root)));
    }

    private CommandOutcome createRevisionInTransaction(CreateRevisionCommand command, Actor actor) {
        PreparationDO inspected = rootMapper.selectById(
                new RequirementAnalysisRowQuery(actor.tenantId(), command.preparationId()));
        if (inspected == null) throw exception(REQUIREMENT_STATUS_INVALID);
        Authorization authorization = lockManager(inspected.getProjectId(), actor, false, false,
                command.expectedProjectVersion());
        RequirementAnalysisProjectQuery projectQuery = new RequirementAnalysisProjectQuery(
                actor.tenantId(), inspected.getProjectId());
        if (rootMapper.selectDraftForUpdate(projectQuery) != null) {
            throw exception(REQUIREMENT_ANALYSIS_DRAFT_CONFLICT);
        }
        PreparationDO source = rootMapper.selectEffectiveForUpdate(projectQuery);
        if (source == null || !Objects.equals(source.getId(), command.preparationId())
                || !"COMPLETED".equals(source.getStatusCode())
                || !Objects.equals(source.getVersion(), command.expectedVersion())
                || !Objects.equals(source.getContentVersion(), command.expectedContentVersion())) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        List<RequirementAnalysisSectionDO> sourceSections = sectionMapper.selectListForUpdate(
                new RequirementAnalysisSectionListQuery(actor.tenantId(), source.getId()));
        PreparationDO draft = copyRoot(actor, source);
        insertRoot(draft);
        Map<Long, RequirementAnalysisSectionDO> copiedBySource = insertCopiedSections(actor, draft, sourceSections);
        List<AttachExistingFileVersionItem> attachItems = new ArrayList<>();
        Map<String, RequirementAnalysisSectionDO> targetByReference = new LinkedHashMap<>();
        for (RequirementAnalysisSectionDO sourceSection : sourceSections) {
            RequirementAnalysisSectionDO targetSection = copiedBySource.get(sourceSection.getId());
            for (RequirementAnalysisQueryService.AttachmentFact fact : parseAttachments(
                    sourceSection.getAttachmentReferenceSnapshot())) {
                String targetKey = UUID.randomUUID().toString();
                attachItems.add(new AttachExistingFileVersionItem(
                        revalidation(sourceSection, fact, FileActionCodes.READ),
                        new ExistingFileReferenceTarget(OWNER_CONTEXT, OBJECT_TYPE,
                                String.valueOf(targetSection.getId()), PURPOSE, targetKey,
                                authorization.scopeVersion())));
                targetByReference.put(targetKey, targetSection);
            }
        }
        if (!attachItems.isEmpty()) {
            List<FileArtifactVersionFact> attached = fileArtifactApi.attachExistingVersions(
                    new AttachExistingFileVersionsCommand(command.idempotencyKey(), attachItems));
            Map<Long, List<RequirementAnalysisQueryService.AttachmentFact>> factsBySection = new LinkedHashMap<>();
            for (FileArtifactVersionFact fact : attached) {
                RequirementAnalysisSectionDO target = targetByReference.get(fact.referenceKey());
                if (target == null) throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
                factsBySection.computeIfAbsent(target.getId(), ignored -> new ArrayList<>()).add(toSnapshot(fact));
            }
            for (RequirementAnalysisSectionDO target : copiedBySource.values()) {
                List<RequirementAnalysisQueryService.AttachmentFact> facts = factsBySection
                        .getOrDefault(target.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(RequirementAnalysisQueryService.AttachmentFact::referenceKey))
                        .toList();
                if (sectionMapper.patchIfMatch(new RequirementAnalysisSectionPatchUpdate(actor.tenantId(), draft.getId(),
                        target.getId(), target.getVersion(), false, target.getValueSnapshot(), true,
                        JsonUtils.toJsonString(facts), String.valueOf(actor.actorId()))) != 1) {
                    throw exception(REQUIREMENT_VERSION_NOT_MATCH);
                }
            }
        }
        return outcome(draft, new AuditTransition(null, draft.getId(), source.getId(), source.getId(),
                AuditState.of(source), AuditState.of(draft)));
    }

    private Authorization lockManager(Long projectId, Actor actor, boolean requireS1, boolean withBinding,
                                      Integer expectedProjectVersion) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), RequirementAnalysisQueryService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        var currentScope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        if (currentScope == null || currentScope.treeVersion() == null) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
        var lockedScope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE,
                currentScope.treeVersion()));
        if (lockedScope.fullProjectIds() == null || !lockedScope.fullProjectIds().contains(projectId)) {
            throw exception(FORBIDDEN);
        }
        ProjectParticipantFact inspected = participantFactApi.inspect(new ProjectParticipantFactQuery(
                projectId, actor.actorId(), Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                LocalDateTime.now()));
        if (expectedProjectVersion != null && !Objects.equals(expectedProjectVersion, inspected.projectVersion())) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        ProjectParticipantFact participant = participantFactApi.lockAndRevalidate(
                new ProjectParticipantFactRevalidationQuery(projectId, actor.actorId(), inspected.projectVersion(),
                        ACTIVE, requireS1 ? INITIAL_STAGE : null,
                        Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        if (!Objects.equals(participant.userId(), actor.actorId())) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
        ProjectWorkBindingFact binding = null;
        if (withBinding) {
            ProjectWorkBindingFact inspectedBinding = workBindingFactApi.inspect(new ProjectWorkBindingFactQuery(
                    projectId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
            binding = workBindingFactApi.lockAndRevalidate(new ProjectWorkBindingFactRevalidationQuery(
                    projectId, inspectedBinding.projectTaskId(), inspectedBinding.executionContractId(),
                    inspectedBinding.projectTaskVersion(), inspectedBinding.contractVersion(),
                    inspectedBinding.projectVersion(), ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
        }
        return new Authorization(participant.projectVersion(), lockedScope.treeVersion(), binding);
    }

    private List<RequirementAnalysisCatalog.SectionDefinition> parseBinding(ProjectWorkBindingFact binding) {
        try {
            if (binding == null || !ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.workBindingTypeCode()
                    .equals(binding.workBindingTypeCode())
                    || !ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetContextCode()
                    .equals(binding.targetContextCode())
                    || !ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectType()
                    .equals(binding.targetObjectType())
                    || !ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectKey()
                    .equals(binding.targetObjectKey())) {
                throw new IllegalArgumentException("wrong PRE-04 binding target");
            }
            return RequirementAnalysisCatalog.parse(binding.bindingParameterSnapshot());
        } catch (RuntimeException failure) {
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        }
    }

    private void requireBindingMatchesRoot(ProjectWorkBindingFact binding, PreparationDO root) {
        parseBinding(binding);
        if (!Objects.equals(root.getTemplateId(), binding.templateTaskDefinitionId())
                || !Objects.equals(root.getTemplateRevisionId(), binding.templateRevisionId())) {
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        }
    }

    private PreparationDO newRoot(Actor actor, ProjectWorkBindingFact binding, Long projectId,
                                  int businessVersion, Long sourceId) {
        LocalDateTime now = LocalDateTime.now();
        PreparationDO row = new PreparationDO();
        row.setTenantId(actor.tenantId());
        row.setProjectId(projectId);
        row.setPreparationTypeCode(RequirementAnalysisQueryService.TYPE);
        row.setBusinessVersion(businessVersion);
        row.setSourcePreparationId(sourceId);
        row.setDraftMarker(1);
        row.setTemplateId(binding.templateTaskDefinitionId());
        row.setTemplateRevisionId(binding.templateRevisionId());
        row.setTemplateSnapshot(binding.bindingParameterSnapshot());
        row.setFixedFormCatalogVersion(RequirementAnalysisCatalog.CATALOG_VERSION);
        row.setStatusCode("DRAFT");
        row.setReadinessStatusCode("NOT_READY");
        row.setInputVersion(0);
        row.setReadinessVersion(0);
        row.setSnapshotCurrent(false);
        row.setContentVersion(0);
        row.setVersion(0);
        row.setCreator(String.valueOf(actor.actorId()));
        row.setUpdater(String.valueOf(actor.actorId()));
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private PreparationDO copyRoot(Actor actor, PreparationDO source) {
        LocalDateTime now = LocalDateTime.now();
        PreparationDO row = new PreparationDO();
        row.setTenantId(actor.tenantId());
        row.setProjectId(source.getProjectId());
        row.setPreparationTypeCode(RequirementAnalysisQueryService.TYPE);
        row.setBusinessVersion(source.getBusinessVersion() + 1);
        row.setSourcePreparationId(source.getId());
        row.setDraftMarker(1);
        row.setTemplateId(source.getTemplateId());
        row.setTemplateRevisionId(source.getTemplateRevisionId());
        row.setTemplateSnapshot(source.getTemplateSnapshot());
        row.setFixedFormCatalogVersion(source.getFixedFormCatalogVersion());
        row.setStatusCode("DRAFT");
        row.setReadinessStatusCode("NOT_READY");
        row.setInputVersion(0);
        row.setReadinessVersion(0);
        row.setSnapshotCurrent(false);
        row.setContentVersion(0);
        row.setVersion(0);
        row.setCreator(String.valueOf(actor.actorId()));
        row.setUpdater(String.valueOf(actor.actorId()));
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private void insertRoot(PreparationDO root) {
        if (preparationMapper.insert(root) != 1 || root.getId() == null) {
            throw new IllegalStateException("REQUIREMENT_ANALYSIS_ROOT_INSERT_FAILED");
        }
    }

    private void insertSections(Actor actor, PreparationDO root,
                                List<RequirementAnalysisCatalog.SectionDefinition> definitions,
                                Map<String, RequirementAnalysisSectionDO> sources) {
        for (RequirementAnalysisCatalog.SectionDefinition definition : definitions) {
            RequirementAnalysisSectionDO source = sources == null ? null : sources.get(definition.sectionCode());
            RequirementAnalysisSectionDO row = section(actor, root, definition, source);
            if (sectionMapper.insert(row) != 1 || row.getId() == null) {
                throw new IllegalStateException("REQUIREMENT_ANALYSIS_SECTION_INSERT_FAILED");
            }
        }
    }

    private Map<Long, RequirementAnalysisSectionDO> insertCopiedSections(
            Actor actor, PreparationDO draft, List<RequirementAnalysisSectionDO> sources) {
        Map<Long, RequirementAnalysisSectionDO> result = new LinkedHashMap<>();
        for (RequirementAnalysisSectionDO source : sources) {
            RequirementAnalysisCatalog.SectionDefinition definition = new RequirementAnalysisCatalog.SectionDefinition(
                    source.getSectionCode(), source.getSectionName(), source.getSectionKindCode(),
                    source.getFieldTypeCode(), Boolean.TRUE.equals(source.getRequiredFlag()),
                    source.getDictionaryType(), source.getSortOrder(), List.of());
            RequirementAnalysisSectionDO target = section(actor, draft, definition, source);
            if (sectionMapper.insert(target) != 1 || target.getId() == null) {
                throw new IllegalStateException("REQUIREMENT_ANALYSIS_SECTION_COPY_FAILED");
            }
            result.put(source.getId(), target);
        }
        return result;
    }

    private RequirementAnalysisSectionDO section(Actor actor, PreparationDO root,
                                                  RequirementAnalysisCatalog.SectionDefinition definition,
                                                  RequirementAnalysisSectionDO source) {
        LocalDateTime now = LocalDateTime.now();
        RequirementAnalysisSectionDO row = new RequirementAnalysisSectionDO();
        row.setTenantId(actor.tenantId());
        row.setPreparationId(root.getId());
        row.setSourceSectionId(source == null ? null : source.getId());
        row.setSectionCode(definition.sectionCode());
        row.setSectionName(definition.sectionName());
        row.setSectionKindCode(definition.sectionKindCode());
        row.setFieldTypeCode(definition.fieldTypeCode());
        row.setRequiredFlag(definition.required());
        row.setDictionaryType(definition.dictionaryType());
        row.setSortOrder(definition.sortOrder());
        row.setSchemaSnapshot(source == null
                ? RequirementAnalysisCatalog.schemaSnapshot(definition) : source.getSchemaSnapshot());
        row.setValueSnapshot(source == null ? "null" : source.getValueSnapshot());
        row.setAttachmentReferenceSnapshot("[]");
        row.setVersion(0);
        row.setCreator(String.valueOf(actor.actorId()));
        row.setUpdater(String.valueOf(actor.actorId()));
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private String normalizeValue(RequirementAnalysisSectionDO section, JsonNode value) {
        String type = section.getFieldTypeCode();
        if (value == null || value.isNull()) return "null";
        try {
            return switch (type) {
                case "RICH_TEXT" -> {
                    if (!value.isTextual()) throw new IllegalArgumentException();
                    var body = Jsoup.parseBodyFragment(xssCleaner.clean(value.asText())).body();
                    body.select("img,video,audio,source,picture,iframe,object,embed").remove();
                    yield JsonUtils.toJsonString(body.html());
                }
                case "TEXT" -> {
                    if (!value.isTextual()) throw new IllegalArgumentException();
                    yield JsonUtils.toJsonString(xssCleaner.clean(value.asText()).trim());
                }
                case "NUMBER" -> {
                    if (!value.isNumber()) throw new IllegalArgumentException();
                    yield value.toString();
                }
                case "BOOLEAN" -> {
                    if (!value.isBoolean()) throw new IllegalArgumentException();
                    yield value.toString();
                }
                case "SINGLE_SELECT" -> {
                    if (!value.isTextual() || !optionCodes(section).contains(value.asText())) {
                        throw new IllegalArgumentException();
                    }
                    yield JsonUtils.toJsonString(value.asText());
                }
                case "MULTI_SELECT" -> {
                    if (!value.isArray()) throw new IllegalArgumentException();
                    Set<String> allowed = optionCodes(section);
                    List<String> selected = new ArrayList<>();
                    value.forEach(item -> {
                        if (!item.isTextual() || !allowed.contains(item.asText())
                                || !selected.add(item.asText())) throw new IllegalArgumentException();
                    });
                    selected.sort(String::compareTo);
                    yield JsonUtils.toJsonString(selected);
                }
                default -> throw new IllegalArgumentException();
            };
        } catch (RuntimeException invalid) {
            throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
        }
    }

    private Set<String> optionCodes(RequirementAnalysisSectionDO section) {
        Map<?, ?> schema = JsonUtils.parseObject(section.getSchemaSnapshot(), Map.class);
        Object value = schema == null ? null : schema.get("optionSnapshot");
        if (!(value instanceof List<?> options)) throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
        Set<String> codes = new HashSet<>();
        for (Object item : options) {
            if (!(item instanceof Map<?, ?> option) || !(option.get("code") instanceof String code)) {
                throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
            }
            codes.add(code);
        }
        return Set.copyOf(codes);
    }

    private String lockSubmittedAttachmentSet(RequirementAnalysisSectionDO section,
                                              List<RequirementAnalysisAttachmentReqVO> requested) {
        List<RequirementAnalysisAttachmentReqVO> rows = requested == null ? List.of() : requested;
        Set<String> keys = new HashSet<>();
        List<RequirementAnalysisQueryService.AttachmentFact> submitted = new ArrayList<>();
        for (RequirementAnalysisAttachmentReqVO row : rows) {
            if (row == null || row.getArtifactId() == null || row.getVersionNo() == null
                    || row.getFileFactVersion() == null || row.getScopeVersion() == null
                    || row.getReferenceKey() == null || !keys.add(row.getReferenceKey())) {
                throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
            }
            requireUuid(row.getReferenceKey());
            submitted.add(new RequirementAnalysisQueryService.AttachmentFact(row.getArtifactId(),
                    row.getVersionNo(), row.getReferenceKey(), row.getFileFactVersion(), row.getScopeVersion()));
        }
        submitted.sort(Comparator.comparing(RequirementAnalysisQueryService.AttachmentFact::referenceKey));
        Map<Long, FileReferenceSetFact> locked = inspectAndLockAttachmentSets(
                Map.of(section.getId(), List.copyOf(submitted)));
        FileReferenceSetFact set = locked.get(section.getId());
        if (set == null) throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
        return JsonUtils.toJsonString(set.activeFacts().stream().map(this::toSnapshot).toList());
    }

    private void requireCompleteContent(List<RequirementAnalysisSectionDO> sections) {
        if (sections == null || sections.size() < 11) throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
        for (RequirementAnalysisSectionDO section : sections) {
            if (!Boolean.TRUE.equals(section.getRequiredFlag())) continue;
            JsonNode value = JsonUtils.parseTree(section.getValueSnapshot());
            if (requiredValueMissing(section, value)) {
                throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
            }
            normalizeValue(section, value);
        }
    }

    private boolean requiredValueMissing(RequirementAnalysisSectionDO section, JsonNode value) {
        if (value == null || value.isNull()) return true;
        return switch (section.getFieldTypeCode()) {
            case "RICH_TEXT" -> !value.isTextual() || !hasVisibleRichText(value.asText());
            case "TEXT" -> !value.isTextual() || value.asText().isBlank();
            case "NUMBER" -> !value.isNumber();
            case "BOOLEAN" -> !value.isBoolean();
            case "SINGLE_SELECT" -> !value.isTextual() || value.asText().isBlank();
            case "MULTI_SELECT" -> !value.isArray() || value.isEmpty();
            default -> true;
        };
    }

    private boolean hasVisibleRichText(String html) {
        return Jsoup.parseBodyFragment(html).text().codePoints().anyMatch(codePoint ->
                !Character.isWhitespace(codePoint) && codePoint != 0x00A0 && codePoint != 0x200B
                        && codePoint != 0x200C && codePoint != 0x200D && codePoint != 0xFEFF);
    }

    private void lockAllAttachmentSets(List<RequirementAnalysisSectionDO> sections) {
        Map<Long, List<RequirementAnalysisQueryService.AttachmentFact>> expected = new LinkedHashMap<>();
        for (RequirementAnalysisSectionDO section : sections) {
            expected.put(section.getId(), parseAttachments(section.getAttachmentReferenceSnapshot()));
        }
        inspectAndLockAttachmentSets(expected);
    }

    private Map<Long, FileReferenceSetFact> inspectAndLockAttachmentSets(
            Map<Long, List<RequirementAnalysisQueryService.AttachmentFact>> expectedBySection) {
        List<FileReferenceSetKey> setKeys = expectedBySection.keySet().stream()
                .map(this::referenceSetKey).sorted().toList();
        List<FileReferenceSetFact> inspected;
        try {
            inspected = fileArtifactApi.inspectReferenceSets(
                    new FileReferenceSetCollectionQuery(setKeys, FileActionCodes.READ));
        } catch (RuntimeException unavailable) {
            throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
        }
        Map<Long, FileReferenceSetFact> inspectedBySection = requireExactSets(
                setKeys, inspected, expectedBySection);
        List<FileReferenceSetExpectation> expectations = setKeys.stream().map(key -> {
            FileReferenceSetFact fact = inspectedBySection.get(Long.valueOf(key.objectId()));
            return new FileReferenceSetExpectation(key, fact.scopeVersion(), fact.activeFacts());
        }).toList();
        List<FileReferenceSetFact> locked;
        try {
            locked = fileArtifactApi.lockAndRevalidateReferenceSets(
                    new FileReferenceSetCollectionRevalidationQuery(expectations, FileActionCodes.READ));
        } catch (RuntimeException unavailable) {
            throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
        }
        return requireExactSets(setKeys, locked, expectedBySection);
    }

    private Map<Long, FileReferenceSetFact> requireExactSets(
            List<FileReferenceSetKey> keys, List<FileReferenceSetFact> facts,
            Map<Long, List<RequirementAnalysisQueryService.AttachmentFact>> expectedBySection) {
        if (facts == null || facts.size() != keys.size()) {
            throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
        }
        Map<Long, FileReferenceSetFact> bySection = new LinkedHashMap<>();
        for (FileReferenceSetFact fact : facts) {
            Long sectionId;
            try {
                sectionId = Long.valueOf(fact.key().objectId());
            } catch (RuntimeException invalid) {
                throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
            }
            if (!keys.contains(fact.key()) || bySection.put(sectionId, fact) != null
                    || !sameAttachmentSet(expectedBySection.get(sectionId), fact.activeFacts())) {
                throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
            }
        }
        return Map.copyOf(bySection);
    }

    private boolean sameAttachmentSet(List<RequirementAnalysisQueryService.AttachmentFact> expected,
                                      List<FileArtifactVersionFact> current) {
        if (expected == null || current == null || expected.size() != current.size()) return false;
        List<RequirementAnalysisQueryService.AttachmentFact> normalizedExpected = expected.stream()
                .sorted(Comparator.comparing(RequirementAnalysisQueryService.AttachmentFact::referenceKey)).toList();
        List<RequirementAnalysisQueryService.AttachmentFact> normalizedCurrent = current.stream()
                .map(this::toSnapshot)
                .sorted(Comparator.comparing(RequirementAnalysisQueryService.AttachmentFact::referenceKey)).toList();
        return normalizedExpected.equals(normalizedCurrent);
    }

    private FileReferenceSetKey referenceSetKey(Long sectionId) {
        return new FileReferenceSetKey(OWNER_CONTEXT, OBJECT_TYPE, String.valueOf(sectionId), PURPOSE);
    }

    private FileArtifactVersionRevalidationQuery revalidation(
            RequirementAnalysisSectionDO section,
            RequirementAnalysisQueryService.AttachmentFact fact,
            String requiredAction) {
        return new FileArtifactVersionRevalidationQuery(fact.artifactId(), fact.versionNo(),
                OWNER_CONTEXT, OBJECT_TYPE, String.valueOf(section.getId()), PURPOSE,
                fact.referenceKey(), requiredAction, fact.fileFactVersion(), fact.scopeVersion());
    }

    private List<RequirementAnalysisQueryService.AttachmentFact> parseAttachments(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return List.of();
        List<RequirementAnalysisQueryService.AttachmentFact> facts = JsonUtils.parseArray(
                snapshot, RequirementAnalysisQueryService.AttachmentFact.class);
        return facts == null ? List.of() : facts;
    }

    private RequirementAnalysisQueryService.AttachmentFact toSnapshot(FileArtifactVersionFact fact) {
        return new RequirementAnalysisQueryService.AttachmentFact(fact.artifactId(), fact.versionNo(),
                fact.referenceKey(), fact.fileFactVersion(), fact.scopeVersion());
    }

    private void requireDraft(PreparationDO root, Integer expectedVersion, Integer expectedContentVersion) {
        if (root == null || !"DRAFT".equals(root.getStatusCode())
                || !Integer.valueOf(1).equals(root.getDraftMarker())
                || !Objects.equals(root.getVersion(), expectedVersion)
                || !Objects.equals(root.getContentVersion(), expectedContentVersion)) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
    }

    private <T> CommandResult executeSafely(Actor actor, String scope, String key, T request,
                                            Long preparationId, Long projectId,
                                            Supplier<CommandOutcome> operation) {
        try {
            return transactionTemplate.execute(status -> {
                AtomicReference<AuditTransition> transition = new AtomicReference<>();
                var execution = commandExecutionApi.execute(
                        new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(), scope,
                                actor.actorId(), key), digest(request), CommandResult.class, () -> {
                            CommandOutcome outcome = operation.get();
                            transition.set(outcome.auditTransition());
                            return outcome.result();
                        }, result -> successFacts(scope, key, actor, result, transition.get()));
                if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                    throw exception(PLATFORM_COMMAND_KEY_CONFLICT);
                }
                if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
                    throw exception(PLATFORM_COMMAND_IN_PROGRESS);
                }
                return execution.response();
            });
        } catch (RuntimeException failure) {
            auditRejected(actor, scope, preparationId, projectId, failure);
            throw failure;
        }
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            String scope, String operationId, Actor actor, CommandResult result, AuditTransition transition) {
        if (transition == null) throw new IllegalStateException("REQUIREMENT_ANALYSIS_AUDIT_TRANSITION_MISSING");
        CommandAuditDetail detail = new CommandAuditDetail(operationId, result.projectId(), result.preparationId(),
                transition.draftBefore(), transition.draftAfter(), transition.effectiveBefore(),
                transition.effectiveAfter(), transition.before().status(), transition.after().status(),
                transition.before().businessVersion(), transition.after().businessVersion(),
                transition.before().contentVersion(), transition.after().contentVersion(),
                transition.before().aggregateVersion(), transition.after().aggregateVersion(),
                auditSections(actor.tenantId(), result.preparationId()));
        return new PlatformCommandExecutionApi.SuccessFacts(scope, "RequirementAnalysis",
                String.valueOf(result.preparationId()), actor.correlationId(),
                JsonUtils.toJsonString(detail), null, null);
    }

    private void recordPatchSuccess(PatchCommand command, PreparationDO root,
                                    RequirementAnalysisSectionDO section, String attachments, Actor actor) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", root.getProjectId());
        detail.put("preparationId", root.getId());
        detail.put("sectionId", section.getId());
        detail.put("sectionCode", section.getSectionCode());
        detail.put("submittedFields", command.submittedFields());
        detail.put("preparationVersionBefore", command.expectedPreparationVersion());
        detail.put("preparationVersionAfter", root.getVersion());
        detail.put("contentVersionBefore", command.expectedContentVersion());
        detail.put("contentVersionAfter", root.getContentVersion());
        detail.put("sectionVersionBefore", section.getVersion());
        detail.put("sectionVersionAfter", section.getVersion() + 1);
        detail.put("attachments", attachmentAudit(attachments));
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "REQUIREMENT_ANALYSIS_PATCH", "RequirementAnalysis", String.valueOf(root.getId()),
                "SUCCESS", Map.copyOf(detail));
    }

    private List<Map<String, Object>> auditSections(Long tenantId, Long preparationId) {
        return sectionMapper.selectList(new RequirementAnalysisSectionListQuery(
                        tenantId, preparationId)).stream()
                .sorted(Comparator.comparing(RequirementAnalysisSectionDO::getSectionCode))
                .map(section -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("sectionId", section.getId());
                    detail.put("sectionCode", section.getSectionCode());
                    detail.put("sectionVersion", section.getVersion());
                    detail.put("attachments", attachmentAudit(section.getAttachmentReferenceSnapshot()));
                    return Map.copyOf(detail);
                }).toList();
    }

    private List<Map<String, Object>> attachmentAudit(String snapshot) {
        return parseAttachments(snapshot).stream().map(fact -> Map.<String, Object>of(
                "artifactId", fact.artifactId(), "versionNo", fact.versionNo(),
                "referenceKey", fact.referenceKey(), "fileFactVersion", fact.fileFactVersion(),
                "scopeVersion", fact.scopeVersion())).toList();
    }

    private void auditRejected(Actor actor, String operation, Long preparationId, Long projectId,
                               RuntimeException failure) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        if (projectId != null) detail.put("projectId", projectId);
        if (preparationId != null) detail.put("preparationId", preparationId);
        detail.put("errorCode", failure instanceof ServiceException serviceException
                ? String.valueOf(serviceException.getCode()) : "REQUIREMENT_ANALYSIS_COMMAND_FAILED");
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), operation,
                "RequirementAnalysis", preparationId != null ? String.valueOf(preparationId)
                        : projectId != null ? "PROJECT:" + projectId : "UNKNOWN",
                "REJECTED", Map.copyOf(detail));
    }

    private CommandResult result(PreparationDO root) {
        return new CommandResult(root.getId(), root.getProjectId(), root.getBusinessVersion(),
                root.getStatusCode(), root.getContentVersion(), root.getVersion());
    }

    private CommandOutcome outcome(PreparationDO root, AuditTransition transition) {
        return new CommandOutcome(result(root), transition);
    }

    private String digest(Object request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void requireCreate(CreateCommand command, Actor actor) {
        requireActor(actor);
        if (command == null || command.projectId() == null || command.projectId() <= 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
    }

    private void requirePatch(PatchCommand command, Actor actor) {
        requireActor(actor);
        if (command == null || command.preparationId() == null || command.preparationId() <= 0
                || command.sectionId() == null || command.sectionId() <= 0
                || command.expectedPreparationVersion() == null || command.expectedPreparationVersion() < 0
                || command.expectedContentVersion() == null || command.expectedContentVersion() < 0
                || command.submittedFields() == null || command.submittedFields().isEmpty()) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
    }

    private void requireAction(Long preparationId, Integer expectedVersion, Integer expectedContentVersion,
                               String key, Actor actor) {
        requireActor(actor);
        if (preparationId == null || preparationId <= 0 || expectedVersion == null || expectedVersion < 0
                || expectedContentVersion == null || expectedContentVersion < 0
                || key == null || key.isBlank()) throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
    }

    private void requireComplete(CompleteCommand command, Actor actor) {
        if (command == null) throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        requireAction(command.preparationId(), command.expectedVersion(), command.expectedContentVersion(),
                command.idempotencyKey(), actor);
    }

    private void requireRevision(CreateRevisionCommand command, Actor actor) {
        if (command == null) throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        requireAction(command.preparationId(), command.expectedVersion(), command.expectedContentVersion(),
                command.idempotencyKey(), actor);
    }

    private void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0
                || actor.correlationId() == null || actor.correlationId().isBlank()) throw exception(FORBIDDEN);
    }

    private void requireUuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException failure) {
            throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
        }
    }

    private record Authorization(Integer projectVersion, Long scopeVersion, ProjectWorkBindingFact binding) {
    }

    private record CommandOutcome(CommandResult result, AuditTransition auditTransition) {
    }

    private record AuditTransition(Long draftBefore, Long draftAfter,
                                   Long effectiveBefore, Long effectiveAfter,
                                   AuditState before, AuditState after) {
    }

    private record AuditState(String status, Integer businessVersion,
                              Integer contentVersion, Integer aggregateVersion) {
        private static AuditState empty() {
            return new AuditState(null, null, null, null);
        }

        private static AuditState of(PreparationDO root) {
            return new AuditState(root.getStatusCode(), root.getBusinessVersion(),
                    root.getContentVersion(), root.getVersion());
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private record CommandAuditDetail(String operationId, Long projectId, Long preparationId,
                                      Long draftPreparationIdBefore, Long draftPreparationIdAfter,
                                      Long effectivePreparationIdBefore, Long effectivePreparationIdAfter,
                                      String statusBefore, String statusAfter,
                                      Integer businessVersionBefore, Integer businessVersionAfter,
                                      Integer contentVersionBefore, Integer contentVersionAfter,
                                      Integer aggregateVersionBefore, Integer aggregateVersionAfter,
                                      List<Map<String, Object>> sections) {
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }

    public record CreateCommand(Long projectId, Integer expectedProjectVersion, String idempotencyKey) {
    }

    public record PatchCommand(Long preparationId, Long sectionId,
                               Integer expectedPreparationVersion, Integer expectedContentVersion,
                               Integer expectedProjectVersion, Set<String> submittedFields,
                               JsonNode value, List<RequirementAnalysisAttachmentReqVO> attachments) {
        public PatchCommand {
            submittedFields = submittedFields == null ? Set.of() : Set.copyOf(submittedFields);
            attachments = attachments == null ? null : List.copyOf(attachments);
        }
    }

    public record CompleteCommand(Long preparationId, Integer expectedVersion, Integer expectedContentVersion,
                                  Integer expectedProjectVersion, String idempotencyKey) {
    }

    public record CreateRevisionCommand(Long preparationId, Integer expectedVersion,
                                        Integer expectedContentVersion, Integer expectedProjectVersion,
                                        String idempotencyKey) {
    }

    public record CommandResult(Long preparationId, Long projectId, Integer businessVersion,
                                String status, Integer contentVersion, Integer version) {
    }
}
