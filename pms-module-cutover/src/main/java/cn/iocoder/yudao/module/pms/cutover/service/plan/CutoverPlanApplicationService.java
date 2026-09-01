package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanHistoryQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskSourceInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactException;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.ApprovalStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFact;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalCommandResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalPauseCommand;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartCommand;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.CreateCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.DownloadCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.InvalidateCutoverPlanSourceCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SaveCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SubmitCutoverPlanCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.CutoverPlanCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.DownloadCutoverPlanDraftResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.InvalidateCutoverPlanSourceResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.SubmitCutoverPlanResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationException.Code.*;

/** F-CUT-004 Task 4草稿应用内核；生产装配由依赖接通任务完成。 */
public class CutoverPlanApplicationService {
    private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake();
    private static final String ACTION_EDIT = "ACTION_EDIT";
    private static final String DRAFT = "DRAFT";

    private final CutoverTaskMapper taskMapper;
    private final CutoverPlanRevisionMapper planMapper;
    private final CutoverPlanStepMapper stepMapper;
    private final CutoverSupportArrangementMapper supportMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverPlanSourcePort sourcePort;
    private final CutoverPlanFilePort filePort;
    private final CutoverPlanContentCodec codec;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final CutoverApprovalFactApi approvalFactApi;
    private final CutoverTaskStageHistoryMapper historyMapper;
    private final Clock clock;

    public CutoverPlanApplicationService(CutoverTaskMapper taskMapper, CutoverPlanRevisionMapper planMapper,
                                         CutoverPlanStepMapper stepMapper,
                                         CutoverSupportArrangementMapper supportMapper,
                                         CutoverProjectScopePort projectScopePort,
                                         CutoverPlanSourcePort sourcePort, CutoverPlanFilePort filePort,
                                         CutoverPlanContentCodec codec,
                                         PlatformCommandExecutionApi commandExecutionApi, Clock clock) {
        this(taskMapper, planMapper, stepMapper, supportMapper, projectScopePort, sourcePort, filePort,
                codec, commandExecutionApi, null, null, clock);
    }

    public CutoverPlanApplicationService(CutoverTaskMapper taskMapper, CutoverPlanRevisionMapper planMapper,
                                         CutoverPlanStepMapper stepMapper,
                                         CutoverSupportArrangementMapper supportMapper,
                                         CutoverProjectScopePort projectScopePort,
                                         CutoverPlanSourcePort sourcePort, CutoverPlanFilePort filePort,
                                         CutoverPlanContentCodec codec,
                                         PlatformCommandExecutionApi commandExecutionApi,
                                         CutoverApprovalFactApi approvalFactApi,
                                         CutoverTaskStageHistoryMapper historyMapper, Clock clock) {
        this.taskMapper = taskMapper;
        this.planMapper = planMapper;
        this.stepMapper = stepMapper;
        this.supportMapper = supportMapper;
        this.projectScopePort = projectScopePort;
        this.sourcePort = sourcePort;
        this.filePort = filePort;
        this.codec = codec;
        this.commandExecutionApi = commandExecutionApi;
        this.approvalFactApi = approvalFactApi;
        this.historyMapper = historyMapper;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverPlanCommandResult createDraft(CreateCutoverPlanDraftCommand command) {
        requireCreate(command);
        PlatformCommandExecutionApi.ExecutionResult<CutoverPlanCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:PLAN_DRAFT_CREATE:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(createDigest(command))), CutoverPlanCommandResult.class,
                () -> createNew(command),
                result -> successFacts("CUTOVER_PLAN_DRAFT_CREATE", result, command.correlationId()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverPlanCommandResult saveDraft(SaveCutoverPlanDraftCommand command) {
        requireSave(command);
        PlatformCommandExecutionApi.ExecutionResult<CutoverPlanCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:PLAN_DRAFT_SAVE:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(saveDigest(command))), CutoverPlanCommandResult.class,
                () -> saveNew(command),
                result -> successFacts("CUTOVER_PLAN_DRAFT_SAVE", result, command.correlationId()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadCutoverPlanDraftResult downloadDraft(DownloadCutoverPlanDraftCommand command) {
        requireDownload(command);
        PlatformCommandExecutionApi.ExecutionResult<DownloadCutoverPlanDraftResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:PLAN_DRAFT_DOWNLOAD:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                        "planVersion", command.expectedPlanVersion()))), DownloadCutoverPlanDraftResult.class,
                () -> downloadNew(command),
                result -> downloadSuccessFacts(command, result));
        requireCompleted(execution.decision());
        return execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public SubmitCutoverPlanResult submit(SubmitCutoverPlanCommand command) {
        requireSubmit(command);
        PlatformCommandExecutionApi.ExecutionResult<SubmitCutoverPlanResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:PLAN_SUBMIT:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                        "taskVersion", command.expectedTaskVersion(), "planVersion", command.expectedPlanVersion()))),
                SubmitCutoverPlanResult.class, () -> submitNew(command),
                result -> new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_PLAN_SUBMIT",
                        "CutoverPlanRevision", String.valueOf(result.planRevisionId()), command.correlationId(),
                        JsonUtils.toJsonString(result), List.of()));
        requireCompleted(execution.decision());
        return execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public InvalidateCutoverPlanSourceResult invalidateSource(InvalidateCutoverPlanSourceCommand command) {
        requireInvalidation(command);
        PlatformCommandExecutionApi.ExecutionResult<InvalidateCutoverPlanSourceResult> execution =
                commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                                "CUT:PLAN_SOURCE_INVALIDATE:" + command.taskId(), command.actorId(),
                                command.idempotencyKey()),
                        sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                                "taskVersion", command.expectedTaskVersion(),
                                "planVersion", command.expectedPlanVersion(),
                                "reasonCode", "SOURCE_FACT_INVALIDATED"))),
                        InvalidateCutoverPlanSourceResult.class, () -> invalidateSourceNew(command),
                        result -> new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_PLAN_SOURCE_INVALIDATE",
                                "CutoverPlanRevision", String.valueOf(result.planRevisionId()),
                                command.correlationId(), JsonUtils.toJsonString(result), List.of()));
        requireCompleted(execution.decision());
        return execution.response();
    }

    private DownloadCutoverPlanDraftResult downloadNew(DownloadCutoverPlanDraftCommand command) {
        CutoverTaskDO task = requireVisibleTask(command.tenantId(), command.actorId(), command.taskId());
        CutoverPlanRevisionDO plan = requireDraft(planMapper.selectCurrent(new CutoverPlanRevisionQuery(
                command.tenantId(), command.taskId(), null)), command.expectedPlanVersion());
        CutoverPlanSourcePort.SourceSnapshot snapshot = parseSource(plan.getSourceSnapshot());
        CutoverPlanSourcePort.SourceFacts sourceFacts = new CutoverPlanSourcePort.SourceFacts(
                snapshot, snapshot.failedRiskFacts());
        CutoverPlanContentCodec.DecodedContent content = storedContent(command.tenantId(), plan, sourceFacts);
        try {
            codec.validateComplete(content, sourceFacts);
        } catch (IllegalArgumentException ex) {
            throw failure(INVALID_REQUEST, "方案内容尚未完整");
        }
        CutoverPlanFilePort.FileFact file = filePort.downloadDraft(command.tenantId(), command.actorId(),
                task.getProjectId(), plan.getId());
        if (file == null) throw failure(OWNER_DATA_CORRUPTED, "PLT未返回初稿文件事实");
        return new DownloadCutoverPlanDraftResult(plan.getId(), plan.getVersion(), file,
                clock.instant().toEpochMilli());
    }

    private SubmitCutoverPlanResult submitNew(SubmitCutoverPlanCommand command) {
        CutoverTaskDO inspectedTask = requireOwnedP4(taskMapper.selectById(command.taskId()), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        CutoverPlanRevisionDO inspectedPlan = requireDraft(planMapper.selectCurrent(new CutoverPlanRevisionQuery(
                command.tenantId(), command.taskId(), null)), command.expectedPlanVersion());
        CutoverPlanSourcePort.SourceSnapshot snapshot = parseSource(inspectedPlan.getSourceSnapshot());
        CutoverPlanSourcePort.SourceFacts expectedSource = new CutoverPlanSourcePort.SourceFacts(
                snapshot, snapshot.failedRiskFacts());
        CutoverPlanContentCodec.DecodedContent content = storedContent(command.tenantId(), inspectedPlan, expectedSource);
        requireComplete(content, expectedSource);

        lockScope(command.actorId(), inspectedTask.getProjectId(), snapshot.projectScopeVersion());
        CutoverPlanSourcePort.SourceFacts currentSource = lockSource(command.tenantId(), command.actorId(), expectedSource);
        requireSameSource(expectedSource, currentSource);
        lockStoredFiles(command.tenantId(), command.actorId(), inspectedTask.getProjectId(), content);

        CutoverTaskDO task = requireOwnedP4(taskMapper.selectForUpdate(new CutoverTaskRowQuery(
                command.tenantId(), command.taskId())), command.tenantId(), command.actorId(),
                command.expectedTaskVersion());
        CutoverPlanRevisionDO plan = requireDraft(planMapper.selectCurrentForUpdate(new CutoverPlanRevisionQuery(
                command.tenantId(), command.taskId(), inspectedPlan.getId())), command.expectedPlanVersion());
        CutoverPlanChildrenQuery children = new CutoverPlanChildrenQuery(command.tenantId(), plan.getId());
        stepMapper.selectListByPlanForUpdate(children);
        supportMapper.selectListByPlanForUpdate(children);
        requireComplete(storedContent(command.tenantId(), plan, currentSource), currentSource);

        CutoverApprovalStartResult approval = startApproval(command, task, plan, currentSource.snapshot());
        LocalDateTime now = LocalDateTime.now(clock);
        int newPlanVersion = plan.getVersion() + 1;
        if (planMapper.submitDraftIfMatch(new CutoverPlanSubmitUpdate(command.tenantId(), plan.getId(),
                plan.getVersion(), newPlanVersion, command.actorId(), now, approval.fact().approvalInstanceId(),
                approval.fact().approvalVersion())) != 1) {
            throw failure(VERSION_CONFLICT, "草稿版本已变化");
        }
        if (taskMapper.submitPlanIfMatch(new CutoverTaskPlanSubmitUpdate(command.tenantId(), task.getId(),
                task.getVersion())) != 1) {
            throw failure(VERSION_CONFLICT, "任务版本已变化");
        }
        insertStageHistory(command.tenantId(), task, plan.getId(), command.actorId(), command.correlationId(),
                "P4", "P5", "PLAN_DRAFTING", "APPROVING", "P4_PLAN_SUBMITTED", now);
        return new SubmitCutoverPlanResult(task.getId(), "P5", task.getVersion() + 1, plan.getId(),
                plan.getRevisionNo(), newPlanVersion, approval.fact().approvalInstanceId(),
                approval.fact().approvalVersion(), approval.fact().status().name());
    }

    private InvalidateCutoverPlanSourceResult invalidateSourceNew(InvalidateCutoverPlanSourceCommand command) {
        CutoverTaskDO task = requireP5(taskMapper.selectForUpdate(new CutoverTaskRowQuery(
                command.tenantId(), command.taskId())), command.tenantId(), command.expectedTaskVersion());
        CutoverPlanRevisionDO plan = requireSubmitted(planMapper.selectCurrentForUpdate(new CutoverPlanRevisionQuery(
                command.tenantId(), command.taskId(), null)), command.expectedPlanVersion());
        CutoverPlanSourcePort.SourceSnapshot source = parseSource(plan.getSourceSnapshot());
        CutoverApprovalCommandResult paused = pauseApproval(command, plan, source);
        LocalDateTime now = LocalDateTime.now(clock);
        int newPlanVersion = plan.getVersion() + 1;
        if (planMapper.invalidateSubmittedIfMatch(new CutoverPlanInvalidationUpdate(command.tenantId(), plan.getId(),
                plan.getVersion(), newPlanVersion, plan.getApprovalVersion(), paused.fact().approvalVersion(),
                command.actorId(), now, "SOURCE_FACT_INVALIDATED")) != 1) {
            throw failure(VERSION_CONFLICT, "已提交方案版本已变化");
        }
        if (taskMapper.returnToPlanForSourceInvalidation(new CutoverTaskSourceInvalidationUpdate(
                command.tenantId(), task.getId(), task.getVersion())) != 1) {
            throw failure(VERSION_CONFLICT, "任务版本已变化");
        }
        insertStageHistory(command.tenantId(), task, plan.getId(), command.actorId(), command.correlationId(),
                "P5", "P4", "APPROVING", "PLAN_DRAFTING", "P5_SOURCE_INVALIDATED", now);
        return new InvalidateCutoverPlanSourceResult(task.getId(), "P4", task.getVersion() + 1,
                plan.getId(), newPlanVersion, "INVALIDATED", paused.fact().approvalInstanceId(),
                paused.fact().approvalVersion(), paused.fact().status().name());
    }

    private CutoverPlanCommandResult createNew(CreateCutoverPlanDraftCommand command) {
        CutoverTaskDO task = requireOwnedP4(taskMapper.selectById(command.taskId()), command);
        CutoverProjectScopePort.ProjectScopeFact scope = inspectScope(command.actorId(), task.getProjectId(),
                command.expectedProjectScopeVersion());
        CutoverPlanSourcePort.SourceFacts facts = inspectSource(command.tenantId(), command.actorId(), command.taskId());
        requireSource(facts, task);
        CutoverPlanFilePort.FileFact inspectedFile = inspectCreateFile(command, task);
        return createOnce(command, task, scope, facts, inspectedFile);
    }

    private CutoverPlanCommandResult saveNew(SaveCutoverPlanDraftCommand command) {
        CutoverTaskDO task = requireOwnedP4(taskMapper.selectById(command.taskId()), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        CutoverProjectScopePort.ProjectScopeFact scope = inspectScope(command.actorId(), task.getProjectId(),
                command.expectedProjectScopeVersion());
        CutoverPlanRevisionDO plan = requireDraft(planMapper.selectCurrent(new CutoverPlanRevisionQuery(
                command.tenantId(), command.taskId(), null)), command.expectedPlanVersion());
        CutoverPlanSourcePort.SourceSnapshot snapshot = parseSource(plan.getSourceSnapshot());
        CutoverPlanSourcePort.SourceFacts facts = new CutoverPlanSourcePort.SourceFacts(snapshot, snapshot.failedRiskFacts());
        CutoverPlanContentCodec.DecodedContent decoded = decode(command.content(), facts);
        CutoverPlanFilePort.FileFact inspectedFile = inspectSaveFile(command, task, decoded);
        return saveOnce(command, task, scope, facts, plan.getId(), decoded, inspectedFile);
    }

    private CutoverPlanCommandResult createOnce(CreateCutoverPlanDraftCommand command, CutoverTaskDO inspectedTask,
                                                  CutoverProjectScopePort.ProjectScopeFact inspectedScope,
                                                  CutoverPlanSourcePort.SourceFacts inspectedFacts,
                                                  CutoverPlanFilePort.FileFact inspectedFile) {
        lockScope(command.actorId(), inspectedTask.getProjectId(), inspectedScope.projectScopeVersion());
        CutoverPlanSourcePort.SourceFacts lockedFacts = lockSource(command.tenantId(), command.actorId(), inspectedFacts);
        requireSameSource(inspectedFacts, lockedFacts);
        CutoverPlanFilePort.FileFact file = lockFile(command, inspectedTask, inspectedFile);
        CutoverTaskDO task = requireOwnedP4(taskMapper.selectForUpdate(new CutoverTaskRowQuery(command.tenantId(),
                command.taskId())), command);
        if (planMapper.selectCurrentForUpdate(new CutoverPlanRevisionQuery(command.tenantId(), command.taskId(), null)) != null) {
            throw failure(STATE_CONFLICT, "当前草稿已存在");
        }
        CutoverPlanContentCodec.DecodedContent decoded = file == null
                ? codec.createInitialOnlineDraft(command.editMode(), lockedFacts) : null;
        Integer maxRevision = planMapper.selectMaxRevisionNo(new CutoverPlanHistoryQuery(command.tenantId(), command.taskId()));
        CutoverPlanRevisionDO row = newPlan(command, task, lockedFacts, decoded, file,
                (maxRevision == null ? 0 : maxRevision) + 1);
        if (planMapper.insert(row) != 1) throw failure(STATE_CONFLICT, "草稿创建失败");
        return result(task, row, 0);
    }

    private CutoverPlanCommandResult saveOnce(SaveCutoverPlanDraftCommand command, CutoverTaskDO inspectedTask,
                                                CutoverProjectScopePort.ProjectScopeFact inspectedScope,
                                                CutoverPlanSourcePort.SourceFacts expectedFacts, Long planId,
                                                CutoverPlanContentCodec.DecodedContent decoded,
                                                CutoverPlanFilePort.FileFact inspectedFile) {
        lockScope(command.actorId(), inspectedTask.getProjectId(), inspectedScope.projectScopeVersion());
        CutoverPlanSourcePort.SourceFacts lockedFacts = lockSource(command.tenantId(), command.actorId(), expectedFacts);
        requireSameSource(expectedFacts, lockedFacts);
        CutoverPlanFilePort.FileFact file = lockFile(command, inspectedTask, decoded, inspectedFile);
        CutoverTaskDO task = requireOwnedP4(taskMapper.selectForUpdate(new CutoverTaskRowQuery(command.tenantId(),
                command.taskId())), command.tenantId(), command.actorId(), command.expectedTaskVersion());
        CutoverPlanRevisionDO plan = requireDraft(planMapper.selectCurrentForUpdate(new CutoverPlanRevisionQuery(
                command.tenantId(), command.taskId(), planId)), command.expectedPlanVersion());
        CutoverPlanChildrenQuery children = new CutoverPlanChildrenQuery(command.tenantId(), plan.getId());
        stepMapper.selectListByPlanForUpdate(children);
        List<CutoverSupportArrangementDO> existingSupport = supportMapper.selectListByPlanForUpdate(children);
        LocalDateTime now = LocalDateTime.now(clock);
        int newVersion = command.expectedPlanVersion() + 1;
        if (planMapper.replaceDraftIfMatch(new CutoverPlanDraftUpdate(command.tenantId(), plan.getId(),
                command.expectedPlanVersion(), newVersion, command.content().path("editMode").asText(),
                decoded.rootSnapshot() == null ? null : JsonUtils.toJsonString(decoded.rootSnapshot()),
                file == null ? null : file.artifactId(), file == null ? null : file.versionNo(),
                file == null ? null : file.referenceKey(), file == null ? null : JsonUtils.toJsonString(file.fileFactVersion()),
                file == null ? null : file.scopeVersion(), file == null ? null : file.sha256(),
                file == null ? null : true, String.valueOf(command.actorId()), now)) != 1) {
            throw failure(VERSION_CONFLICT, "草稿版本已变化");
        }
        stepMapper.deleteDraftRows(children);
        supportMapper.deleteDraftRows(children);
        insertChildren(command, plan.getId(), decoded, existingSupport);
        return new CutoverPlanCommandResult(task.getId(), task.getVersion(), plan.getId(), plan.getRevisionNo(),
                newVersion, DRAFT, false);
    }

    private CutoverPlanRevisionDO newPlan(CreateCutoverPlanDraftCommand command, CutoverTaskDO task,
                                           CutoverPlanSourcePort.SourceFacts facts,
                                           CutoverPlanContentCodec.DecodedContent decoded,
                                           CutoverPlanFilePort.FileFact file, int revisionNo) {
        CutoverPlanSourcePort.SourceSnapshot source = facts.snapshot();
        CutoverPlanRevisionDO row = new CutoverPlanRevisionDO();
        row.setId(nextId()); row.setTenantId(command.tenantId()); row.setCutoverTaskId(task.getId());
        row.setRevisionNo(revisionNo); row.setOriginCode("NEW_PLATFORM"); row.setEditModeCode(command.editMode());
        row.setGradeCode(source.grade()); row.setAssessmentId(source.assessmentId());
        row.setAssessmentVersion(source.assessmentVersion()); row.setChecklistId(source.checklistId());
        row.setChecklistVersion(source.checklistVersion()); row.setConfigurationRevisionId(source.configurationRevisionId());
        row.setConfigurationCode(source.configurationCode()); row.setConfigurationRevisionNo(source.configurationRevisionNo());
        row.setTemplateSectionSnapshot(JsonUtils.toJsonString(source.templateSections()));
        row.setSourceSnapshot(JsonUtils.toJsonString(source));
        row.setContentSnapshot(decoded == null ? null : JsonUtils.toJsonString(decoded.rootSnapshot()));
        if (file != null) setFile(row, file);
        row.setOwnershipConfirmed(file == null ? null : true); row.setStatusCode(DRAFT); row.setCurrentMarker(1); row.setVersion(0);
        row.setCreator(String.valueOf(command.actorId())); row.setUpdater(String.valueOf(command.actorId()));
        return row;
    }

    private void insertChildren(SaveCutoverPlanDraftCommand command, Long planId,
                                CutoverPlanContentCodec.DecodedContent decoded,
                                List<CutoverSupportArrangementDO> existingSupport) {
        for (CutoverPlanContentCodec.PlanStep source : decoded.steps()) {
            CutoverPlanStepDO row = new CutoverPlanStepDO();
            row.setId(nextId()); row.setTenantId(command.tenantId()); row.setPlanRevisionId(planId);
            row.setSectionCode(source.sectionCode()); row.setStepNo(source.stepNo()); row.setContent(source.content());
            row.setVersion(0); row.setCreator(String.valueOf(command.actorId())); row.setUpdater(String.valueOf(command.actorId()));
            if (stepMapper.insert(row) != 1) throw failure(STATE_CONFLICT, "步骤保存失败");
        }
        Map<String, CutoverSupportArrangementDO> existingByRole = new LinkedHashMap<>();
        for (CutoverSupportArrangementDO existing : existingSupport) {
            if (existingByRole.put(existing.getRoleCode(), existing) != null) {
                throw failure(OWNER_DATA_CORRUPTED, "保障安排角色身份重复");
            }
        }
        for (CutoverPlanContentCodec.SupportArrangement source : decoded.supportArrangements()) {
            CutoverSupportArrangementDO existing = existingByRole.get(source.roleCode());
            if (existing == null && source.arrangementId() != null) {
                throw failure(INVALID_REQUEST, "新增保障安排不得指定身份");
            }
            if (existing != null && source.arrangementId() != null
                    && !Objects.equals(source.arrangementId(), existing.getId())) {
                throw failure(INVALID_REQUEST, "保障安排身份与角色不匹配");
            }
            CutoverSupportArrangementDO row = new CutoverSupportArrangementDO();
            row.setId(existing == null ? nextId() : existing.getId());
            row.setTenantId(command.tenantId()); row.setPlanRevisionId(planId); row.setRoleCode(source.roleCode());
            row.setPersonName(source.personName()); row.setDutyDescription(source.dutyDescription()); row.setPhone(source.phone());
            row.setArrivalTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(source.arrivalTime()), ZoneId.systemDefault()));
            row.setVersion(0); row.setCreator(String.valueOf(command.actorId())); row.setUpdater(String.valueOf(command.actorId()));
            if (supportMapper.insert(row) != 1) throw failure(STATE_CONFLICT, "保障安排保存失败");
        }
    }

    private CutoverPlanFilePort.FileFact inspectCreateFile(CreateCutoverPlanDraftCommand command, CutoverTaskDO task) {
        if (!"FULL_FILE_UPLOAD".equals(command.editMode())) return null;
        CutoverPlanFilePort.FileFact actual = filePort.inspect(command.tenantId(), command.actorId(), task.getProjectId(),
                command.expectedFileFact().handle());
        if (!actual.equals(command.expectedFileFact())) throw failure(FILE_FACT_STALE, "文件事实已变化");
        return actual;
    }

    private CutoverPlanFilePort.FileFact inspectSaveFile(SaveCutoverPlanDraftCommand command, CutoverTaskDO task,
                                                          CutoverPlanContentCodec.DecodedContent decoded) {
        if (decoded.fileFact() == null) return null;
        CutoverPlanFilePort.FileFact actual = filePort.inspect(command.tenantId(), command.actorId(), task.getProjectId(),
                decoded.fileFact().handle());
        if (!actual.equals(decoded.fileFact())) throw failure(FILE_FACT_STALE, "文件事实已变化");
        return actual;
    }

    private CutoverPlanFilePort.FileFact lockFile(CreateCutoverPlanDraftCommand command, CutoverTaskDO task,
                                                   CutoverPlanFilePort.FileFact inspected) {
        if (inspected == null) return null;
        CutoverPlanFilePort.FileFact locked = filePort.lockAndRevalidate(command.tenantId(), command.actorId(),
                task.getProjectId(), inspected.handle());
        if (!locked.equals(inspected)) throw failure(FILE_FACT_STALE, "文件事实已变化");
        return locked;
    }

    private CutoverPlanFilePort.FileFact lockFile(SaveCutoverPlanDraftCommand command, CutoverTaskDO task,
                                                   CutoverPlanContentCodec.DecodedContent decoded,
                                                   CutoverPlanFilePort.FileFact inspected) {
        if (inspected == null) return null;
        CutoverPlanFilePort.FileFact locked = filePort.lockAndRevalidate(command.tenantId(), command.actorId(),
                task.getProjectId(), decoded.fileFact().handle());
        if (!locked.equals(inspected)) throw failure(FILE_FACT_STALE, "文件事实已变化");
        return locked;
    }

    private CutoverProjectScopePort.ProjectScopeFact inspectScope(Long actorId, Long projectId, Long expected) {
        CutoverProjectScopePort.ProjectScopeFact fact = projectScopePort.inspect(actorId, projectId, ACTION_EDIT);
        if (fact == null || !fact.allowed()) throw failure(NOT_FOUND, "任务不可见");
        if (!Objects.equals(fact.projectScopeVersion(), expected)) throw failure(PROJECT_SCOPE_STALE, "项目范围已变化");
        return fact;
    }

    private void lockScope(Long actorId, Long projectId, long expected) {
        CutoverProjectScopePort.ProjectScopeFact fact = projectScopePort.lockAndRevalidate(actorId, projectId, ACTION_EDIT, expected);
        if (fact == null || !fact.allowed() || fact.projectScopeVersion() != expected) {
            throw failure(PROJECT_SCOPE_STALE, "项目范围已变化");
        }
    }

    private static CutoverTaskDO requireOwnedP4(CutoverTaskDO task, CreateCutoverPlanDraftCommand command) {
        return requireOwnedP4(task, command.tenantId(), command.actorId(), command.expectedTaskVersion());
    }

    private static CutoverTaskDO requireOwnedP4(CutoverTaskDO task, Long tenantId, Long actorId, Integer version) {
        if (task == null || !Objects.equals(task.getTenantId(), tenantId) || !Objects.equals(task.getOwnerUserId(), actorId)) {
            throw failure(NOT_FOUND, "任务不可见");
        }
        if (!CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                || !CutoverTaskRules.STAGE_P4.equals(task.getCurrentStage())
                || !CutoverTaskRules.STATUS_PLAN_DRAFTING.equals(task.getTaskStatus())) {
            throw failure(STATE_CONFLICT, "任务当前不可编辑P4方案");
        }
        if (!Objects.equals(task.getVersion(), version)) throw failure(VERSION_CONFLICT, "任务版本已变化");
        return task;
    }

    private static CutoverPlanRevisionDO requireDraft(CutoverPlanRevisionDO plan, Integer expectedVersion) {
        if (plan == null) throw failure(NOT_FOUND, "当前方案不存在");
        if (!DRAFT.equals(plan.getStatusCode()) || !Objects.equals(plan.getCurrentMarker(), 1)) {
            throw failure(STATE_CONFLICT, "当前方案不可编辑");
        }
        if (!Objects.equals(plan.getVersion(), expectedVersion)) throw failure(VERSION_CONFLICT, "草稿版本已变化");
        return plan;
    }

    private static CutoverTaskDO requireP5(CutoverTaskDO task, Long tenantId, Integer expectedVersion) {
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)
                || !CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())) {
            throw failure(NOT_FOUND, "任务不存在");
        }
        if (!"P5".equals(task.getCurrentStage()) || !"APPROVING".equals(task.getTaskStatus())) {
            throw failure(STATE_CONFLICT, "任务当前不在P5审批中");
        }
        if (!Objects.equals(task.getVersion(), expectedVersion)) throw failure(VERSION_CONFLICT, "任务版本已变化");
        return task;
    }

    private static CutoverPlanRevisionDO requireSubmitted(CutoverPlanRevisionDO plan, Integer expectedVersion) {
        if (plan == null) throw failure(NOT_FOUND, "当前已提交方案不存在");
        if (!"SUBMITTED".equals(plan.getStatusCode()) || !Objects.equals(plan.getCurrentMarker(), 1)
                || !positive(plan.getApprovalInstanceId()) || plan.getApprovalVersion() == null
                || plan.getApprovalVersion() < 0) {
            throw failure(STATE_CONFLICT, "当前方案不可执行来源失效");
        }
        if (!Objects.equals(plan.getVersion(), expectedVersion)) throw failure(VERSION_CONFLICT, "方案版本已变化");
        return plan;
    }

    private static void requireSource(CutoverPlanSourcePort.SourceFacts facts, CutoverTaskDO task) {
        if (facts == null || !Objects.equals(facts.snapshot().taskId(), task.getId())
                || !Objects.equals(facts.snapshot().projectId(), task.getProjectId())) {
            throw failure(OWNER_DATA_CORRUPTED, "P4来源事实身份损坏");
        }
        if (!Objects.equals(facts.snapshot().taskVersion(), task.getVersion())) {
            throw failure(TASK_VERSION_STALE, "任务版本已变化");
        }
    }

    private CutoverPlanContentCodec.DecodedContent decode(JsonNode content,
                                                           CutoverPlanSourcePort.SourceFacts facts) {
        try { return codec.decodeWritable(content, facts); }
        catch (IllegalArgumentException ex) { throw new CutoverPlanApplicationException(INVALID_REQUEST, ex.getMessage()); }
    }

    private static CutoverPlanSourcePort.SourceSnapshot parseSource(String json) {
        try { return JsonUtils.parseObject(json, CutoverPlanSourcePort.SourceSnapshot.class); }
        catch (RuntimeException ex) { throw new CutoverPlanApplicationException(OWNER_DATA_CORRUPTED, "冻结来源事实损坏"); }
    }

    private CutoverPlanContentCodec.DecodedContent storedContent(Long tenantId, CutoverPlanRevisionDO plan,
                                                                  CutoverPlanSourcePort.SourceFacts sourceFacts) {
        if ("FULL_FILE_UPLOAD".equals(plan.getEditModeCode())) {
            CutoverPlanFilePort.FileFact file = new CutoverPlanFilePort.FileFact(plan.getFileArtifactId(),
                    plan.getFileVersionNo(), plan.getFileReferenceKey(), JsonUtils.parseObject(
                    plan.getFileFactVersion(), CutoverPlanFilePort.FileFactVersion.class),
                    plan.getFileScopeVersion(), plan.getFileSha256());
            return new CutoverPlanContentCodec.DecodedContent(null, List.of(), List.of(), file,
                    Boolean.TRUE.equals(plan.getOwnershipConfirmed()));
        }
        try {
            ObjectNode root = (ObjectNode) JsonUtils.parseObject(plan.getContentSnapshot(), JsonNode.class);
            ArrayNode steps = root.putArray("steps");
            stepMapper.selectListByPlan(new CutoverPlanChildrenQuery(tenantId, plan.getId())).forEach(value -> {
                ObjectNode row = steps.addObject();
                row.put("sectionCode", value.getSectionCode());
                row.put("stepNo", value.getStepNo());
                row.put("content", value.getContent());
            });
            if ("ONLINE_TEMPLATE_STANDARD".equals(plan.getEditModeCode())) {
                ArrayNode support = root.putArray("supportArrangements");
                supportMapper.selectListByPlan(new CutoverPlanChildrenQuery(tenantId, plan.getId())).forEach(value -> {
                    ObjectNode row = support.addObject();
                    putWireLong(row, "arrangementId", value.getId());
                    row.put("roleCode", value.getRoleCode());
                    row.put("personName", value.getPersonName());
                    row.put("dutyDescription", value.getDutyDescription());
                    row.put("phone", value.getPhone());
                    row.put("arrivalTime", value.getArrivalTime().atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli());
                });
            }
            return codec.decodeWritable(root, sourceFacts);
        } catch (CutoverPlanApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw failure(OWNER_DATA_CORRUPTED, "持久化方案内容损坏");
        }
    }

    private CutoverTaskDO requireVisibleTask(Long tenantId, Long actorId, Long taskId) {
        CutoverTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)
                || !CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())) {
            throw failure(NOT_FOUND, "任务不可见");
        }
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, task.getProjectId(), "ACTION_VIEW");
        if (scope == null || !scope.allowed()) throw failure(NOT_FOUND, "任务不可见");
        return task;
    }

    private void requireComplete(CutoverPlanContentCodec.DecodedContent content,
                                 CutoverPlanSourcePort.SourceFacts sourceFacts) {
        if (content.fileFact() != null) {
            if (!content.ownershipConfirmed()) throw failure(PLAN_SECTION_INCOMPLETE, "完整文件未确认归属");
            return;
        }
        String mode = content.rootSnapshot().path("editMode").asText();
        List<String> requiredSections = "ONLINE_TEMPLATE_SIMPLE_D".equals(mode)
                ? CutoverPlanRules.SIMPLE_SECTIONS : CutoverPlanRules.STANDARD_SECTIONS;
        if (requiredSections.stream().anyMatch(section -> content.steps().stream()
                .noneMatch(step -> section.equals(step.sectionCode())))) {
            throw failure(PLAN_SECTION_INCOMPLETE, "方案步骤不完整");
        }
        if ("ONLINE_TEMPLATE_SIMPLE_D".equals(mode)) return;
        JsonNode overview = content.rootSnapshot().path("overview");
        if (!"ONLINE_TEMPLATE_STANDARD".equals(mode)
                || overview.path("projectDescription").asText().isBlank()
                || overview.path("scheduleTable").isEmpty()) {
            throw failure(PLAN_SECTION_INCOMPLETE, "标准方案概览不完整");
        }
        if (content.rootSnapshot().path("riskMitigations").size() != sourceFacts.failedRiskFacts().size()) {
            throw failure(RISK_MITIGATION_INCOMPLETE, "风险措施不完整");
        }
        if (content.supportArrangements().size() != CutoverPlanRules.SUPPORT_ROLES.size()) {
            throw failure(SUPPORT_ARRANGEMENT_INCOMPLETE, "保障安排不完整");
        }
    }

    private void lockStoredFiles(Long tenantId, Long actorId, Long projectId,
                                 CutoverPlanContentCodec.DecodedContent content) {
        if (content.fileFact() != null) {
            requireLockedFile(tenantId, actorId, projectId, content.fileFact());
            return;
        }
        JsonNode overview = content.rootSnapshot().path("overview");
        for (String field : List.of("preTopologyFile", "postTopologyFile", "networkConfigurationFile")) {
            JsonNode node = overview.get(field);
            if (node != null && !node.isNull()) {
                requireLockedFile(tenantId, actorId, projectId,
                        JsonUtils.parseObject(JsonUtils.toJsonString(node), CutoverPlanFilePort.FileFact.class));
            }
        }
    }

    private void requireLockedFile(Long tenantId, Long actorId, Long projectId,
                                   CutoverPlanFilePort.FileFact expected) {
        CutoverPlanFilePort.FileFact current = filePort.lockAndRevalidate(tenantId, actorId, projectId,
                expected.handle());
        if (!Objects.equals(expected, current)) throw failure(FILE_FACT_STALE, "文件事实已变化");
    }

    private CutoverApprovalStartResult startApproval(SubmitCutoverPlanCommand command, CutoverTaskDO task,
                                                      CutoverPlanRevisionDO plan,
                                                      CutoverPlanSourcePort.SourceSnapshot source) {
        if (approvalFactApi == null) throw failure(OWNER_PROVIDER_UNAVAILABLE, "CUT-05审批Provider不可用");
        try {
            CutoverApprovalStartResult result = approvalFactApi.start(new CutoverApprovalStartCommand(
                    command.tenantId(), task.getId(), task.getVersion(), plan.getId(), plan.getRevisionNo(),
                    source.grade(), source.assessmentId(), source.assessmentVersion(), source.checklistId(),
                    source.checklistVersion(), source.snapshotVersion(), null, command.idempotencyKey(),
                    command.correlationId()));
            CutoverApprovalFact fact = result == null ? null : result.fact();
            if (fact == null || fact.status() != ApprovalStatus.PENDING
                    || !Objects.equals(fact.taskId(), task.getId())
                    || !Objects.equals(fact.planRevisionId(), plan.getId())
                    || !Objects.equals(fact.planRevisionNo(), plan.getRevisionNo())
                    || !Objects.equals(fact.sourceSnapshotVersion(), source.snapshotVersion())) {
                throw failure(OWNER_DATA_CORRUPTED, "CUT-05审批事实身份损坏");
            }
            return result;
        } catch (CutoverApprovalFactException ex) {
            throw approvalFailure(ex);
        }
    }

    private CutoverApprovalCommandResult pauseApproval(InvalidateCutoverPlanSourceCommand command,
                                                        CutoverPlanRevisionDO plan,
                                                        CutoverPlanSourcePort.SourceSnapshot source) {
        if (approvalFactApi == null) throw failure(OWNER_PROVIDER_UNAVAILABLE, "CUT-05审批Provider不可用");
        try {
            CutoverApprovalCommandResult result = approvalFactApi.pauseForSourceInvalidation(
                    new CutoverApprovalPauseCommand(command.tenantId(), plan.getApprovalInstanceId(),
                            plan.getApprovalVersion(), plan.getId(), source.snapshotVersion(),
                            "SOURCE_FACT_INVALIDATED", command.idempotencyKey(), command.correlationId()));
            CutoverApprovalFact fact = result == null ? null : result.fact();
            if (fact == null || fact.status() != ApprovalStatus.PAUSED_SOURCE_INVALIDATED
                    || !Objects.equals(fact.approvalInstanceId(), plan.getApprovalInstanceId())
                    || !Objects.equals(fact.taskId(), plan.getCutoverTaskId())
                    || !Objects.equals(fact.planRevisionId(), plan.getId())
                    || !Objects.equals(fact.planRevisionNo(), plan.getRevisionNo())
                    || !Objects.equals(fact.sourceSnapshotVersion(), source.snapshotVersion())
                    || fact.approvalVersion() <= plan.getApprovalVersion()) {
                throw failure(OWNER_DATA_CORRUPTED, "CUT-05暂停审批事实身份损坏");
            }
            return result;
        } catch (CutoverApprovalFactException ex) {
            throw approvalFailure(ex);
        }
    }

    private static CutoverPlanApplicationException approvalFailure(CutoverApprovalFactException ex) {
        return switch (ex.code()) {
            case PROVIDER_UNAVAILABLE -> new CutoverPlanApplicationException(OWNER_PROVIDER_UNAVAILABLE, ex.getMessage());
            case IDEMPOTENCY_CONFLICT -> new CutoverPlanApplicationException(IDEMPOTENCY_CONFLICT, ex.getMessage());
            case IDEMPOTENCY_IN_PROGRESS -> new CutoverPlanApplicationException(IDEMPOTENCY_IN_PROGRESS, ex.getMessage());
            case STATE_CONFLICT -> new CutoverPlanApplicationException(STATE_CONFLICT, ex.getMessage());
            case VERSION_CONFLICT -> new CutoverPlanApplicationException(VERSION_CONFLICT, ex.getMessage());
            default -> new CutoverPlanApplicationException(OWNER_DATA_CORRUPTED, ex.getMessage());
        };
    }

    private void insertStageHistory(Long tenantId, CutoverTaskDO task, Long referenceId, Long actorId,
                                    String correlationId, String fromStage, String toStage,
                                    String fromStatus, String toStatus, String triggerType,
                                    LocalDateTime occurredAt) {
        if (historyMapper == null) throw failure(OWNER_DATA_CORRUPTED, "阶段历史Mapper未装配");
        Integer maxSequence = taskMapper.selectMaxStageHistorySequence(new CutoverTaskRowQuery(tenantId, task.getId()));
        CutoverTaskStageHistoryDO history = new CutoverTaskStageHistoryDO();
        history.setId(nextId()); history.setTenantId(tenantId); history.setCutoverTaskId(task.getId());
        history.setSequenceNo((maxSequence == null ? 0 : maxSequence) + 1);
        history.setFromStage(fromStage); history.setToStage(toStage);
        history.setFromStatus(fromStatus); history.setToStatus(toStatus);
        history.setTriggerType(triggerType); history.setTriggerReferenceId(referenceId);
        history.setActorId(actorId); history.setCorrelationId(correlationId); history.setOccurredAt(occurredAt);
        history.setCreator(String.valueOf(actorId)); history.setCreateTime(occurredAt);
        if (historyMapper.insert(history) != 1) throw failure(STATE_CONFLICT, "阶段历史创建失败");
    }

    private static void setFile(CutoverPlanRevisionDO row, CutoverPlanFilePort.FileFact file) {
        row.setFileArtifactId(file.artifactId()); row.setFileVersionNo(file.versionNo());
        row.setFileReferenceKey(file.referenceKey()); row.setFileFactVersion(JsonUtils.toJsonString(file.fileFactVersion()));
        row.setFileScopeVersion(file.scopeVersion()); row.setFileSha256(file.sha256());
    }

    private static CutoverPlanCommandResult result(CutoverTaskDO task, CutoverPlanRevisionDO row, int version) {
        return new CutoverPlanCommandResult(task.getId(), task.getVersion(), row.getId(), row.getRevisionNo(), version, DRAFT, false);
    }

    private static Map<String, Object> createDigest(CreateCutoverPlanDraftCommand command) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("taskId", command.taskId());
        value.put("taskVersion", command.expectedTaskVersion()); value.put("projectScopeVersion", command.expectedProjectScopeVersion());
        value.put("editMode", command.editMode()); value.put("fileFact", command.expectedFileFact());
        value.put("ownershipConfirmed", command.ownershipConfirmed()); return value;
    }

    private static Map<String, Object> saveDigest(SaveCutoverPlanDraftCommand command) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("taskId", command.taskId());
        value.put("taskVersion", command.expectedTaskVersion()); value.put("planVersion", command.expectedPlanVersion());
        value.put("projectScopeVersion", command.expectedProjectScopeVersion());
        value.put("content", canonicalJson(command.content())); return value;
    }

    private CutoverPlanSourcePort.SourceFacts inspectSource(Long tenantId, Long actorId, Long taskId) {
        try {
            return sourcePort.inspect(tenantId, actorId, taskId);
        } catch (CutoverPlanOwnerFactException ex) {
            throw ownerFailure(ex);
        }
    }

    private CutoverPlanSourcePort.SourceFacts lockSource(Long tenantId, Long actorId,
                                                          CutoverPlanSourcePort.SourceFacts expected) {
        try {
            return sourcePort.lockAndRevalidate(tenantId, actorId, expected);
        } catch (CutoverPlanOwnerFactException ex) {
            throw ownerFailure(ex);
        }
    }

    private static CutoverPlanApplicationException ownerFailure(CutoverPlanOwnerFactException ex) {
        if (ex.code() == CutoverPlanOwnerFactException.Code.PROVIDER_UNAVAILABLE) {
            return new CutoverPlanApplicationException(OWNER_PROVIDER_UNAVAILABLE, ex.getMessage());
        }
        if (ex.code() == CutoverPlanOwnerFactException.Code.ASSESSMENT_STALE) {
            return new CutoverPlanApplicationException(ASSESSMENT_STALE, ex.getMessage());
        }
        if (ex.code() == CutoverPlanOwnerFactException.Code.CHECKLIST_STALE) {
            return new CutoverPlanApplicationException(CHECKLIST_STALE, ex.getMessage());
        }
        if (ex.code() == CutoverPlanOwnerFactException.Code.PROJECT_OR_DEVICE_STALE) {
            return new CutoverPlanApplicationException(PROJECT_OR_DEVICE_STALE, ex.getMessage());
        }
        if (ex.code() == CutoverPlanOwnerFactException.Code.CONFIGURATION_OR_TEMPLATE_STALE) {
            return new CutoverPlanApplicationException(CONFIGURATION_OR_TEMPLATE_STALE, ex.getMessage());
        }
        return new CutoverPlanApplicationException(OWNER_DATA_CORRUPTED,
                ex.code() == CutoverPlanOwnerFactException.Code.OWNER_DATA_CORRUPTED
                        ? ex.getMessage() : "来源Owner未返回可分轴比较的当前事实");
    }

    private static void requireSameSource(CutoverPlanSourcePort.SourceFacts expected,
                                          CutoverPlanSourcePort.SourceFacts current) {
        if (current == null) throw failure(OWNER_DATA_CORRUPTED, "来源Owner返回空事实");
        CutoverPlanSourcePort.SourceSnapshot before = expected.snapshot();
        CutoverPlanSourcePort.SourceSnapshot after = current.snapshot();
        if (!Objects.equals(before.taskId(), after.taskId())) {
            throw failure(OWNER_DATA_CORRUPTED, "来源任务身份损坏");
        }
        if (!Objects.equals(before.taskVersion(), after.taskVersion())) {
            throw failure(TASK_VERSION_STALE, "任务版本已变化");
        }
        if (!Objects.equals(before.assessmentId(), after.assessmentId())
                || !Objects.equals(before.assessmentVersion(), after.assessmentVersion())
                || !Objects.equals(before.grade(), after.grade())) {
            throw failure(ASSESSMENT_STALE, "评估事实已变化");
        }
        if (!Objects.equals(before.checklistId(), after.checklistId())
                || !Objects.equals(before.checklistVersion(), after.checklistVersion())
                || !Objects.equals(expected.failedRiskFacts(), current.failedRiskFacts())) {
            throw failure(CHECKLIST_STALE, "清单事实已变化");
        }
        if (!Objects.equals(before.projectId(), after.projectId())
                || !Objects.equals(before.projectVersion(), after.projectVersion())
                || !Objects.equals(before.projectScopeVersion(), after.projectScopeVersion())
                || !Objects.equals(before.devices(), after.devices())) {
            throw failure(PROJECT_OR_DEVICE_STALE, "项目或设备事实已变化");
        }
        if (!Objects.equals(before.configurationRevisionId(), after.configurationRevisionId())
                || !Objects.equals(before.configurationCode(), after.configurationCode())
                || !Objects.equals(before.configurationRevisionNo(), after.configurationRevisionNo())
                || !Objects.equals(before.templateSections(), after.templateSections())) {
            throw failure(CONFIGURATION_OR_TEMPLATE_STALE, "配置或模板事实已变化");
        }
        if (!Objects.equals(expected, current)) {
            throw failure(OWNER_DATA_CORRUPTED, "来源Owner返回无法分轴解释的变化");
        }
    }

    private static JsonNode canonicalJson(JsonNode value) {
        if (value == null || value.isValueNode()) return value;
        if (value.isArray()) {
            ArrayNode result = JsonUtils.getObjectMapper().createArrayNode();
            value.forEach(item -> result.add(canonicalJson(item)));
            return result;
        }
        ObjectNode result = JsonUtils.getObjectMapper().createObjectNode();
        Map<String, JsonNode> fields = new TreeMap<>();
        value.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
        fields.forEach((name, child) -> result.set(name, canonicalJson(child)));
        return result;
    }

    private static PlatformCommandExecutionApi.SuccessFacts successFacts(String action, CutoverPlanCommandResult result,
                                                                          String correlationId) {
        return new PlatformCommandExecutionApi.SuccessFacts(action, "CutoverPlanRevision",
                String.valueOf(result.planRevisionId()), correlationId, JsonUtils.toJsonString(result), List.of());
    }

    private static PlatformCommandExecutionApi.SuccessFacts downloadSuccessFacts(
            DownloadCutoverPlanDraftCommand command, DownloadCutoverPlanDraftResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("actorId", command.actorId());
        detail.put("planRevisionId", result.planRevisionId());
        detail.put("planVersion", result.planVersion());
        detail.put("fileArtifactFact", result.fileArtifactFact());
        detail.put("downloadedAt", result.downloadedAt());
        return new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_PLAN_DRAFT_DOWNLOAD",
                "CutoverPlanRevision", String.valueOf(result.planRevisionId()), command.correlationId(),
                JsonUtils.toJsonString(detail), List.of());
    }

    private static void requireCreate(CreateCutoverPlanDraftCommand command) {
        if (command == null || !positive(command.tenantId()) || !positive(command.actorId()) || !positive(command.taskId())
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.expectedProjectScopeVersion() == null || command.expectedProjectScopeVersion() < 0
                || !List.of("ONLINE_TEMPLATE_STANDARD", "ONLINE_TEMPLATE_SIMPLE_D", "FULL_FILE_UPLOAD").contains(command.editMode())
                || !validText(command.idempotencyKey(), 128) || !validText(command.correlationId(), 128)) {
            throw failure(INVALID_REQUEST, "创建草稿命令非法");
        }
        boolean upload = "FULL_FILE_UPLOAD".equals(command.editMode());
        if ((upload && (command.expectedFileFact() == null || !Boolean.TRUE.equals(command.ownershipConfirmed())))
                || (!upload && (command.expectedFileFact() != null || command.ownershipConfirmed() != null))) {
            throw failure(INVALID_REQUEST, "文件草稿事实非法");
        }
    }

    private static void requireSave(SaveCutoverPlanDraftCommand command) {
        if (command == null || !positive(command.tenantId()) || !positive(command.actorId()) || !positive(command.taskId())
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.expectedPlanVersion() == null || command.expectedPlanVersion() < 0
                || command.expectedProjectScopeVersion() == null || command.expectedProjectScopeVersion() < 0
                || command.content() == null || !validText(command.idempotencyKey(), 128)
                || !validText(command.correlationId(), 128)) throw failure(INVALID_REQUEST, "保存草稿命令非法");
    }

    private static void requireDownload(DownloadCutoverPlanDraftCommand command) {
        if (command == null || !positive(command.tenantId()) || !positive(command.actorId())
                || !positive(command.taskId()) || command.expectedPlanVersion() == null
                || command.expectedPlanVersion() < 0 || !validText(command.idempotencyKey(), 128)
                || !validText(command.correlationId(), 128)) {
            throw failure(INVALID_REQUEST, "下载初稿命令非法");
        }
    }

    private static void requireSubmit(SubmitCutoverPlanCommand command) {
        if (command == null || !positive(command.tenantId()) || !positive(command.actorId())
                || !positive(command.taskId()) || command.expectedTaskVersion() == null
                || command.expectedTaskVersion() < 0 || command.expectedPlanVersion() == null
                || command.expectedPlanVersion() < 0 || !validText(command.idempotencyKey(), 128)
                || !validText(command.correlationId(), 128)) {
            throw failure(INVALID_REQUEST, "提交方案命令非法");
        }
    }

    private static void requireInvalidation(InvalidateCutoverPlanSourceCommand command) {
        if (command == null || !positive(command.tenantId()) || !positive(command.actorId())
                || !positive(command.taskId()) || command.expectedTaskVersion() == null
                || command.expectedTaskVersion() < 0 || command.expectedPlanVersion() == null
                || command.expectedPlanVersion() < 0 || !validText(command.idempotencyKey(), 128)
                || !validText(command.correlationId(), 128)) {
            throw failure(INVALID_REQUEST, "来源失效命令非法");
        }
    }

    private static void putWireLong(ObjectNode node, String field, Long value) {
        if (value > -9_007_199_254_740_991L && value < 9_007_199_254_740_991L) node.put(field, value);
        else node.put(field, Long.toString(value));
    }

    private static void requireCompleted(PlatformCommandExecutionApi.Decision decision) {
        if (decision == PlatformCommandExecutionApi.Decision.CONFLICT) throw failure(IDEMPOTENCY_CONFLICT, "幂等键载荷冲突");
        if (decision == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw failure(IDEMPOTENCY_IN_PROGRESS, "命令处理中");
    }

    private static boolean positive(Long value) { return value != null && value > 0; }
    private static boolean validText(String value, int max) { return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= max; }
    private static long nextId() { return ID_GENERATOR.nextId(); }
    private static CutoverPlanApplicationException failure(CutoverPlanApplicationException.Code code, String message) {
        return new CutoverPlanApplicationException(code, message);
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
