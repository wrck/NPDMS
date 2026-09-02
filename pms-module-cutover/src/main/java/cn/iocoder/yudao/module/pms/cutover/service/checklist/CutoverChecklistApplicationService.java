package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistCurrentResultQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistCustomRemoveUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistDraftTouchUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistItemsQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistItemApplicabilityUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRematchUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistResultCloseUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistResultsQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverFrozenConfigurationQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskChecklistSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.AddCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RematchChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RemoveCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RequestCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SelectManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SubmitChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistSubmissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistItemCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CollectionRequestCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.DATA_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.COLLECTION_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.FILE_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.INVALID_REQUEST;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.NOT_FOUND;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.STATE_CONFLICT;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.VERSION_CONFLICT;

/** F-CUT-003 P3清单命令内核；跨模块生产Adapter接通前不注册为生产Bean。 */
public class CutoverChecklistApplicationService {

    private static final String ACTION_EDIT = "ACTION_EDIT";
    private static final String ACTION_VIEW = "ACTION_VIEW";
    private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake();

    private final CutoverTaskMapper taskMapper;
    private final CutoverTaskDeviceScopeMapper deviceMapper;
    private final CutoverAssessmentMapper assessmentMapper;
    private final CutoverTaskStageHistoryMapper historyMapper;
    private final CutoverChecklistMapper checklistMapper;
    private final CutoverChecklistItemMapper itemMapper;
    private final CutoverChecklistItemResultMapper resultMapper;
    private final CutoverChecklistConfigurationQueryService configurationService;
    private final CutoverChecklistMatcher matcher;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverCollectionPort collectionPort;
    private final CutoverChecklistFilePort filePort;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final CutoverNavigationDecisionQueryService navigationDecisionQueryService;
    private final Clock clock;

    public CutoverChecklistApplicationService(CutoverTaskMapper taskMapper,
                                              CutoverTaskDeviceScopeMapper deviceMapper,
                                              CutoverAssessmentMapper assessmentMapper,
                                              CutoverTaskStageHistoryMapper historyMapper,
                                              CutoverChecklistMapper checklistMapper,
                                              CutoverChecklistItemMapper itemMapper,
                                              CutoverChecklistItemResultMapper resultMapper,
                                              CutoverChecklistConfigurationQueryService configurationService,
                                              CutoverChecklistMatcher matcher,
                                              CutoverProjectScopePort projectScopePort,
                                              CutoverCollectionPort collectionPort,
                                              CutoverChecklistFilePort filePort,
                                              PlatformCommandExecutionApi commandExecutionApi,
                                              CutoverNavigationDecisionQueryService navigationDecisionQueryService,
                                              Clock clock) {
        this.taskMapper = taskMapper;
        this.deviceMapper = deviceMapper;
        this.assessmentMapper = assessmentMapper;
        this.historyMapper = historyMapper;
        this.checklistMapper = checklistMapper;
        this.itemMapper = itemMapper;
        this.resultMapper = resultMapper;
        this.configurationService = configurationService;
        this.matcher = matcher;
        this.projectScopePort = projectScopePort;
        this.collectionPort = collectionPort;
        this.filePort = filePort;
        this.commandExecutionApi = commandExecutionApi;
        this.navigationDecisionQueryService = navigationDecisionQueryService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistCommandResult generate(GenerateChecklistCommand command) {
        requireGenerate(command);
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(command.taskId()), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        requireScope(projectScopePort.inspect(command.actorId(), snapshot.getProjectId(), ACTION_EDIT),
                snapshot, command.expectedProjectScopeVersion());
        CutoverFrozenConfiguration configuration = resolveConfiguration(snapshot);
        MatchContext context = matchContext(snapshot, command.expectedAssessmentVersion(), configuration,
                command.selectedConflictDefinitions());
        PlatformCommandExecutionApi.ExecutionResult<ChecklistCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:CHECKLIST_GENERATE:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                        "taskVersion", command.expectedTaskVersion(),
                        "assessmentVersion", command.expectedAssessmentVersion(),
                        "projectScopeVersion", command.expectedProjectScopeVersion(),
                        "selectedConflictDefinitions", command.selectedConflictDefinitions()))),
                ChecklistCommandResult.class,
                () -> generateOnce(command, snapshot, configuration, context),
                result -> successFacts("CUTOVER_CHECKLIST_GENERATE", result, command.correlationId()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistCommandResult rematch(RematchChecklistCommand command) {
        requireRematch(command);
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(command.taskId()), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        requireScope(projectScopePort.inspect(command.actorId(), snapshot.getProjectId(), ACTION_EDIT),
                snapshot, command.expectedProjectScopeVersion());
        CutoverFrozenConfiguration configuration = resolveConfiguration(snapshot);
        MatchContext context = matchContext(snapshot, command.expectedAssessmentVersion(), configuration,
                command.selectedConflictDefinitions());
        PlatformCommandExecutionApi.ExecutionResult<ChecklistCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:CHECKLIST_REMATCH:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                        "taskVersion", command.expectedTaskVersion(),
                        "assessmentVersion", command.expectedAssessmentVersion(),
                        "checklistId", command.checklistId(),
                        "checklistVersion", command.expectedChecklistVersion(),
                        "inputSnapshotHash", command.expectedInputSnapshotHash(),
                        "projectScopeVersion", command.expectedProjectScopeVersion(),
                        "selectedConflictDefinitions", command.selectedConflictDefinitions()))),
                ChecklistCommandResult.class, () -> rematchOnce(command, configuration, context),
                result -> successFacts("CUTOVER_CHECKLIST_REMATCH", result, command.correlationId()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    public CutoverChecklistView getView(Long tenantId, Long actorId, Long taskId) {
        require(positive(tenantId) && positive(actorId) && positive(taskId), INVALID_REQUEST, "清单查询参数非法");
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && Objects.equals(task.getTenantId(), tenantId), NOT_FOUND, "割接任务不存在");
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, task.getProjectId(),
                ACTION_VIEW);
        require(scope != null && scope.allowed() && Objects.equals(scope.projectId(), task.getProjectId()),
                NOT_FOUND, "当前清单不存在");
        CutoverChecklistDO checklist = checklistMapper.selectCurrent(new CutoverChecklistRowQuery(
                tenantId, taskId, null));
        require(checklist != null, NOT_FOUND, "当前清单不存在");
        List<CutoverChecklistItemDO> items = itemMapper.selectListByChecklist(new CutoverChecklistItemsQuery(
                tenantId, checklist.getId()));
        Map<Long, CutoverChecklistItemResultDO> currentResults = resultMapper.selectCurrentByChecklist(
                        new CutoverChecklistResultsQuery(tenantId, checklist.getId())).stream()
                .collect(Collectors.toMap(CutoverChecklistItemResultDO::getChecklistItemId, Function.identity()));
        List<CutoverChecklistView.Item> viewItems = items.stream().map(item -> {
            CutoverChecklistItemResultDO current = currentResults.get(item.getId());
            CutoverChecklistView.CurrentResult resultView = current == null ? null
                    : new CutoverChecklistView.CurrentResult(current.getResultVersion(),
                    current.getResultSourceCode(), current.getAnswerSnapshot(), current.getFactDescription(),
                    current.getManualEvidenceFileReference(), current.getCollectionTaskId(),
                    current.getCollectionResultReferenceId(), current.getCollectionResultVersion(),
                    current.getLoadFailureCode());
            return new CutoverChecklistView.Item(item.getId(), item.getStableItemKey(), item.getItemTypeCode(),
                    item.getItemName(), item.getItemDescription(), item.getInterfaceFormatCode(),
                    item.getInterfaceSchemaSnapshot(), item.getWorkModeCode(),
                    Boolean.TRUE.equals(item.getRequiredFlag()), item.getSourceCode(),
                    Boolean.TRUE.equals(item.getApplicableFlag()), item.getSortOrder(), resultView);
        }).toList();
        return new CutoverChecklistView(taskId, task.getCurrentStage(), task.getVersion(),
                scope.projectScopeVersion(), checklist.getId(),
                checklist.getChecklistVersion(), checklist.getVersion(), checklist.getStatusCode(),
                checklist.getInputSnapshotHash(), checklist.getConfigRevisionSnapshot(), checklist.getMatchTrace(),
                checklist.getConfigGapSnapshot(), viewItems);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistCommandResult save(SaveChecklistCommand command) {
        requireSave(command);
        LockedDraft locked = lockDraft(command.tenantId(), command.actorId(), command.taskId(),
                command.expectedTaskVersion(), command.checklistId(), command.expectedChecklistVersion(),
                command.expectedProjectScopeVersion());
        Map<String, CutoverChecklistItemDO> items = byStableKey(locked.items());
        Set<String> seen = new LinkedHashSet<>();
        for (SaveChecklistCommand.DirectAnswer answer : command.answers()) {
            require(answer != null && present(answer.stableItemKey()) && present(answer.answerSnapshot()),
                    INVALID_REQUEST, "直接填写答案不完整");
            require(seen.add(answer.stableItemKey()), INVALID_REQUEST, "直接填写项重复");
            CutoverChecklistItemDO item = items.get(answer.stableItemKey());
            require(item != null && Boolean.TRUE.equals(item.getApplicableFlag()), NOT_FOUND, "清单项不存在");
            require("DIRECT".equals(item.getWorkModeCode()), STATE_CONFLICT, "当前清单项不支持直接填写");
            appendResult(command.tenantId(), command.actorId(), item, "DIRECT", answer.answerSnapshot(),
                    null, null, "DIRECT_SAVE");
        }
        require(checklistMapper.touchDraftIfMatch(new CutoverChecklistDraftTouchUpdate(command.tenantId(),
                command.checklistId(), command.expectedChecklistVersion())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        return result(locked.task(), locked.checklist(), command.expectedChecklistVersion() + 1, "DRAFT", false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistItemCommandResult addCustomItem(AddCustomItemCommand command) {
        requireCustom(command);
        LockedDraft locked = lockDraft(command.tenantId(), command.actorId(), command.taskId(),
                command.expectedTaskVersion(), command.checklistId(), command.expectedChecklistVersion(),
                command.expectedProjectScopeVersion());
        String stableItemKey = "CUSTOM-" + nextId();
        CutoverChecklistItemDO item = new CutoverChecklistItemDO();
        item.setId(nextId());
        item.setTenantId(command.tenantId());
        item.setChecklistId(command.checklistId());
        item.setStableItemKey(stableItemKey);
        item.setItemTypeCode(command.itemTypeCode());
        item.setItemName(command.itemName());
        item.setItemDescription(command.itemDescription());
        item.setInterfaceFormatCode(command.interfaceFormatCode());
        item.setInterfaceSchemaSnapshot(command.interfaceSchema());
        item.setWorkModeCode("DIRECT");
        item.setRequiredFlag(command.required());
        item.setSourceCode("CUSTOM");
        item.setApplicableFlag(true);
        item.setCustomCreatorUserId(command.actorId());
        item.setSortOrder(commandSortOrder(command, locked.items()));
        item.setVersion(0);
        item.setCreator(String.valueOf(command.actorId()));
        item.setUpdater(String.valueOf(command.actorId()));
        require(itemMapper.insert(item) == 1, STATE_CONFLICT, "自定义清单项创建失败");
        Integer resultVersion = null;
        if (present(command.answerSnapshot())) {
            appendResult(command.tenantId(), command.actorId(), item, "DIRECT", command.answerSnapshot(),
                    null, null, "CUSTOM_DIRECT_SAVE");
            resultVersion = 1;
        }
        require(checklistMapper.touchDraftIfMatch(new CutoverChecklistDraftTouchUpdate(command.tenantId(),
                command.checklistId(), command.expectedChecklistVersion())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        return new ChecklistItemCommandResult(command.checklistId(), command.expectedChecklistVersion() + 1,
                item.getId(), stableItemKey, resultVersion);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistItemCommandResult removeCustomItem(RemoveCustomItemCommand command) {
        requireRemoveCustom(command);
        LockedDraft locked = lockDraft(command.tenantId(), command.actorId(), command.taskId(),
                command.expectedTaskVersion(), command.checklistId(), command.expectedChecklistVersion(),
                command.expectedProjectScopeVersion());
        CutoverChecklistItemDO item = byStableKey(locked.items()).get(command.stableItemKey());
        require(item != null && Boolean.TRUE.equals(item.getApplicableFlag()), NOT_FOUND, "自定义清单项不存在");
        require("CUSTOM".equals(item.getSourceCode())
                        && Objects.equals(item.getCustomCreatorUserId(), command.actorId()),
                DATA_SCOPE_FORBIDDEN, "仅创建人可移出自定义清单项");
        require(resultMapper.selectMaxVersion(new CutoverChecklistCurrentResultQuery(command.tenantId(),
                item.getId())) == 0, STATE_CONFLICT, "已产生结果的自定义清单项不可移出");
        require(itemMapper.removeCustomIfMatch(new CutoverChecklistCustomRemoveUpdate(command.tenantId(),
                item.getId(), item.getVersion(), command.actorId())) == 1, VERSION_CONFLICT, "自定义清单项已变化");
        require(checklistMapper.touchDraftIfMatch(new CutoverChecklistDraftTouchUpdate(command.tenantId(),
                command.checklistId(), command.expectedChecklistVersion())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        return new ChecklistItemCommandResult(command.checklistId(), command.expectedChecklistVersion() + 1,
                item.getId(), item.getStableItemKey(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public CollectionRequestCommandResult requestCollection(RequestCollectionCommand command) {
        requireCollection(command);
        PlatformCommandExecutionApi.ExecutionResult<CollectionRequestCommandResult> execution =
                commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                                "CUT:CHECKLIST_COLLECTION:" + command.taskId() + ":" + command.stableItemKey(),
                                command.actorId(), command.idempotencyKey()),
                        sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                                "taskVersion", command.expectedTaskVersion(),
                                "checklistId", command.checklistId(),
                                "checklistVersion", command.expectedChecklistVersion(),
                                "projectScopeVersion", command.expectedProjectScopeVersion(),
                                "stableItemKey", command.stableItemKey(),
                                "deviceId", command.deviceId(),
                                "commandTemplateId", command.commandTemplateId()))),
                        CollectionRequestCommandResult.class, () -> requestCollectionOnce(command),
                        result -> successFacts("CUTOVER_CHECKLIST_COLLECTION_REQUEST", result,
                                command.tenantId(), command.correlationId()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistItemCommandResult selectManual(SelectManualResultCommand command) {
        requireManual(command);
        LockedDraft locked = lockDraft(command.tenantId(), command.actorId(), command.taskId(),
                command.expectedTaskVersion(), command.checklistId(), command.expectedChecklistVersion(),
                command.expectedProjectScopeVersion());
        CutoverChecklistItemDO item = byStableKey(locked.items()).get(command.stableItemKey());
        require(item != null && Boolean.TRUE.equals(item.getApplicableFlag()), NOT_FOUND, "清单项不存在");
        CutoverChecklistFilePort.FileFact fact = filePort.lockAndRevalidate(command.tenantId(), command.actorId(),
                locked.task().getProjectId(), item.getId(), command.expectedProjectScopeVersion(), command.fileHandle());
        requireFileFact(command.fileHandle(), fact);
        int resultVersion = appendResult(command.tenantId(), command.actorId(), item, "MANUAL",
                JsonUtils.toJsonString(fact), command.factDescription(), fact.referenceKey(),
                "MANUAL_EVIDENCE_SELECTED");
        require(checklistMapper.touchDraftIfMatch(new CutoverChecklistDraftTouchUpdate(command.tenantId(),
                command.checklistId(), command.expectedChecklistVersion())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        return new ChecklistItemCommandResult(command.checklistId(), command.expectedChecklistVersion() + 1,
                item.getId(), item.getStableItemKey(), resultVersion);
    }

    public ChecklistCommandResult submit(SubmitChecklistCommand command) {
        requireSubmit(command);
        PlatformCommandExecutionApi.ExecutionResult<ChecklistSubmissionFacts> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:CHECKLIST_SUBMIT:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                        "taskVersion", command.expectedTaskVersion(),
                        "assessmentVersion", command.expectedAssessmentVersion(),
                        "checklistId", command.checklistId(),
                        "checklistVersion", command.expectedChecklistVersion(),
                        "projectScopeVersion", command.expectedProjectScopeVersion()))),
                ChecklistSubmissionFacts.class, () -> submitOnce(command),
                result -> successFacts("CUTOVER_CHECKLIST_SUBMIT", result, command.correlationId()));
        requireCompleted(execution.decision());
        ChecklistCommandResult result = execution.response().toCommandResult(
                execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED);
        return result.withNavigationDecision(navigationDecisionQueryService.decide(command.tenantId(), command.taskId()));
    }

    private ChecklistCommandResult generateOnce(GenerateChecklistCommand command, CutoverTaskDO snapshot,
                                                CutoverFrozenConfiguration configuration, MatchContext context) {
        lockScope(command.actorId(), snapshot, command.expectedProjectScopeVersion());
        CutoverTaskDO task = requireP3Task(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        CutoverAssessmentDO assessment = requireAssessment(command.tenantId(), task,
                command.expectedAssessmentVersion());
        require(checklistMapper.selectCurrentForUpdate(new CutoverChecklistRowQuery(command.tenantId(),
                task.getId(), null)) == null, STATE_CONFLICT, "任务已有当前清单");
        CutoverChecklistDO checklist = new CutoverChecklistDO();
        checklist.setId(nextId());
        checklist.setTenantId(command.tenantId());
        checklist.setCutoverTaskId(task.getId());
        checklist.setAssessmentId(assessment.getId());
        checklist.setAssessmentVersion(assessment.getAssessmentVersion());
        checklist.setChecklistVersion(1);
        checklist.setStatusCode("DRAFT");
        checklist.setInputSnapshot(context.inputSnapshot());
        checklist.setInputSnapshotHash(sha256(context.inputSnapshot()));
        checklist.setConfigRevisionSnapshot(JsonUtils.toJsonString(configuration));
        checklist.setMatchTrace(context.matchTrace());
        checklist.setConfigGapSnapshot(context.gapSnapshot());
        checklist.setVersion(0);
        checklist.setCreator(String.valueOf(command.actorId()));
        checklist.setUpdater(String.valueOf(command.actorId()));
        require(checklistMapper.insert(checklist) == 1, STATE_CONFLICT, "清单创建失败");
        for (ResolvedItem resolved : context.items()) {
            insertSystemItem(command.tenantId(), command.actorId(), checklist, resolved, configuration);
        }
        return result(task, checklist, 0, "DRAFT", false);
    }

    private ChecklistCommandResult rematchOnce(RematchChecklistCommand command,
                                               CutoverFrozenConfiguration configuration,
                                               MatchContext context) {
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(command.taskId()), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        lockScope(command.actorId(), snapshot, command.expectedProjectScopeVersion());
        CutoverTaskDO task = requireP3Task(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        CutoverAssessmentDO assessment = requireAssessment(command.tenantId(), task,
                command.expectedAssessmentVersion());
        CutoverChecklistDO checklist = requireDraft(checklistMapper.selectCurrentForUpdate(
                new CutoverChecklistRowQuery(command.tenantId(), task.getId(), command.checklistId())),
                command.expectedChecklistVersion());
        require(Objects.equals(checklist.getInputSnapshotHash(), command.expectedInputSnapshotHash()),
                VERSION_CONFLICT, "清单匹配输入已变化");
        List<CutoverChecklistItemDO> items = itemMapper.selectListForUpdate(
                new CutoverChecklistItemsQuery(command.tenantId(), checklist.getId()));
        Map<String, CutoverChecklistItemDO> existing = byStableKey(items);
        for (CutoverChecklistItemDO item : items) {
            if (!"SYSTEM_MATCHED".equals(item.getSourceCode())) {
                continue;
            }
            ResolvedItem resolved = context.items().stream()
                    .filter(value -> value.item().stableItemKey().equals(item.getStableItemKey()))
                    .findFirst().orElse(null);
            CutoverFrozenConfiguration.BindingRule rule = resolved == null ? null
                    : primaryRule(configuration, resolved);
            CutoverFrozenConfiguration.ItemDefinition definition = resolved == null
                    ? existingDefinition(item) : resolved.item();
            boolean definitionChanged = resolved != null
                    && (!Objects.equals(item.getItemDefinitionId(), definition.id())
                    || !Objects.equals(item.getItemDefinitionVersion(), definition.itemDefinitionVersion()));
            if (resolved == null || definitionChanged) {
                closeCurrentResult(command.tenantId(), item.getId());
            }
            require(itemMapper.updateApplicability(new CutoverChecklistItemApplicabilityUpdate(command.tenantId(),
                    item.getId(), resolved != null, resolved != null && resolved.required(),
                    definition.id(), definition.itemDefinitionVersion(), definition.itemTypeCode(),
                    definition.itemName(), definition.itemDescription(), definition.interfaceFormatCode(),
                    definition.interfaceSchema(), rule == null ? item.getDisplayConditionSnapshot()
                            : rule.dimensionConditionSnapshot(), definition.workModeCode(),
                    rule == null ? null : rule.id(), rule == null ? null : rule.version(),
                    definition.sortOrder(), command.actorId())) == 1,
                    VERSION_CONFLICT, "清单项已变化");
        }
        for (ResolvedItem resolved : context.items()) {
            if (!existing.containsKey(resolved.item().stableItemKey())) {
                insertSystemItem(command.tenantId(), command.actorId(), checklist, resolved, configuration);
            }
        }
        int nextChecklistVersion = checklist.getChecklistVersion() + 1;
        require(checklistMapper.rematchIfMatch(new CutoverChecklistRematchUpdate(command.tenantId(),
                checklist.getId(), command.expectedChecklistVersion(), nextChecklistVersion, assessment.getId(),
                assessment.getAssessmentVersion(), context.inputSnapshot(), sha256(context.inputSnapshot()),
                context.matchTrace(), context.gapSnapshot(), command.actorId())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        return new ChecklistCommandResult(task.getId(), checklist.getId(), nextChecklistVersion,
                command.expectedChecklistVersion() + 1, "DRAFT", task.getCurrentStage(), task.getVersion(), false);
    }

    private ChecklistSubmissionFacts submitOnce(SubmitChecklistCommand command) {
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(command.taskId()), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        lockScope(command.actorId(), snapshot, command.expectedProjectScopeVersion());
        CutoverTaskDO task = requireP3Task(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        CutoverAssessmentDO assessment = requireAssessment(command.tenantId(), task,
                command.expectedAssessmentVersion());
        CutoverChecklistDO checklist = requireDraft(checklistMapper.selectCurrentForUpdate(
                new CutoverChecklistRowQuery(command.tenantId(), task.getId(), command.checklistId())),
                command.expectedChecklistVersion());
        List<CutoverChecklistItemDO> items = itemMapper.selectListForUpdate(
                new CutoverChecklistItemsQuery(command.tenantId(), checklist.getId()));
        List<CutoverChecklistItemResultDO> results = resultMapper.selectCurrentByChecklistForUpdate(
                new CutoverChecklistResultsQuery(command.tenantId(), checklist.getId()));
        if (checklist.getConfigGapSnapshot() != null) {
            require(items.stream().anyMatch(item -> "CUSTOM".equals(item.getSourceCode())
                            && Boolean.TRUE.equals(item.getApplicableFlag())),
                    STATE_CONFLICT, "配置缺口尚未通过自定义清单项补足");
        }
        Set<Long> completed = results.stream().filter(CutoverChecklistApplicationService::isCompletedResult)
                .map(CutoverChecklistItemResultDO::getChecklistItemId)
                .collect(Collectors.toSet());
        boolean missingRequired = items.stream().anyMatch(item -> Boolean.TRUE.equals(item.getApplicableFlag())
                && Boolean.TRUE.equals(item.getRequiredFlag()) && !completed.contains(item.getId()));
        require(!missingRequired, STATE_CONFLICT, "必填清单项尚未完成");
        LocalDateTime now = LocalDateTime.now(clock);
        require(checklistMapper.submitIfMatch(new CutoverChecklistSubmitUpdate(command.tenantId(), checklist.getId(),
                command.expectedChecklistVersion(), command.actorId(), now)) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        require(taskMapper.submitChecklistIfMatch(new CutoverTaskChecklistSubmitUpdate(command.tenantId(),
                task.getId(), command.expectedTaskVersion())) == 1, VERSION_CONFLICT, "任务版本已变化");
        insertHistory(command, checklist, now);
        return new ChecklistSubmissionFacts(task.getId(), checklist.getId(), checklist.getChecklistVersion(),
                command.expectedChecklistVersion() + 1, "SUBMITTED", CutoverTaskRules.STAGE_P4,
                task.getVersion() + 1, false);
    }

    private CollectionRequestCommandResult requestCollectionOnce(RequestCollectionCommand command) {
        LockedDraft locked = lockDraft(command.tenantId(), command.actorId(), command.taskId(),
                command.expectedTaskVersion(), command.checklistId(), command.expectedChecklistVersion(),
                command.expectedProjectScopeVersion());
        CutoverChecklistItemDO item = byStableKey(locked.items()).get(command.stableItemKey());
        require(item != null && Boolean.TRUE.equals(item.getApplicableFlag()), NOT_FOUND, "采集清单项不存在");
        require("COLLECTION".equals(item.getWorkModeCode()), STATE_CONFLICT, "当前清单项不支持设备采集");
        boolean deviceInScope = deviceMapper.selectActiveByTask(new CutoverTaskDeviceListQuery(command.tenantId(),
                        command.taskId())).stream().anyMatch(row -> Objects.equals(row.getDeviceId(), command.deviceId()));
        require(deviceInScope, DATA_SCOPE_FORBIDDEN, "设备不在当前割接任务范围");
        CutoverChecklistItemResultDO current = resultMapper.selectCurrentForUpdate(
                new CutoverChecklistCurrentResultQuery(command.tenantId(), item.getId()));
        Long collectionTaskId;
        CollectionSelectionSnapshot currentSnapshot = collectionSnapshot(current);
        if (currentSnapshot != null && Set.of("ACCEPTED", "RUNNING").contains(currentSnapshot.technicalStatus())) {
            require(Objects.equals(currentSnapshot.deviceId(), command.deviceId())
                            && Objects.equals(currentSnapshot.commandTemplateId(), command.commandTemplateId()),
                    STATE_CONFLICT, "采集中任务身份与当前请求不一致");
            collectionTaskId = currentSnapshot.collectionTaskId();
        } else {
            CutoverCollectionPort.Request request = new CutoverCollectionPort.Request(command.tenantId(),
                    command.actorId(), locked.task().getProjectId(), command.taskId(), command.checklistId(),
                    locked.checklist().getChecklistVersion(), item.getId(), item.getVersion(), item.getStableItemKey(),
                    command.deviceId(), command.commandTemplateId(), command.idempotencyKey(), command.correlationId());
            CutoverCollectionPort.RequestReceipt receipt = collectionPort.request(request);
            require(receipt != null && positive(receipt.collectionTaskId()) && receipt.technicalStatus() != null,
                    COLLECTION_FACT_INVALID, "采集任务回执不完整");
            collectionTaskId = receipt.collectionTaskId();
        }
        CutoverCollectionPort.CollectionFact fact = collectionPort.inspect(new CutoverCollectionPort.Inspection(
                command.tenantId(), command.actorId(), locked.task().getProjectId(), collectionTaskId,
                command.taskId(), command.checklistId(), item.getId(), command.deviceId(), command.commandTemplateId()));
        requireCollectionFact(collectionTaskId, fact);
        int resultVersion = appendCollectionResult(command.tenantId(), command.actorId(), item,
                command.deviceId(), command.commandTemplateId(), fact);
        require(checklistMapper.touchDraftIfMatch(new CutoverChecklistDraftTouchUpdate(command.tenantId(),
                command.checklistId(), command.expectedChecklistVersion())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        return new CollectionRequestCommandResult(command.taskId(), command.expectedTaskVersion(),
                command.checklistId(), locked.checklist().getChecklistVersion(),
                command.expectedChecklistVersion() + 1, item.getId(), item.getVersion(),
                item.getStableItemKey(), resultVersion, fact.collectionTaskId(),
                fact.resultReferenceId(), fact.resultVersion(), fact.technicalStatus().name(),
                fact.failureCode(), true, false);
    }

    private LockedDraft lockDraft(Long tenantId, Long actorId, Long taskId, Integer expectedTaskVersion,
                                  Long checklistId, Integer expectedChecklistVersion,
                                  Long expectedProjectScopeVersion) {
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(taskId), tenantId, actorId,
                expectedTaskVersion);
        lockScope(actorId, snapshot, expectedProjectScopeVersion);
        CutoverTaskDO task = requireP3Task(taskMapper.selectForUpdate(new CutoverTaskRowQuery(tenantId, taskId)),
                tenantId, actorId, expectedTaskVersion);
        requireAssessment(tenantId, task, null);
        CutoverChecklistDO checklist = requireDraft(checklistMapper.selectCurrentForUpdate(
                new CutoverChecklistRowQuery(tenantId, taskId, checklistId)), expectedChecklistVersion);
        List<CutoverChecklistItemDO> items = itemMapper.selectListForUpdate(
                new CutoverChecklistItemsQuery(tenantId, checklistId));
        return new LockedDraft(task, checklist, items);
    }

    private CutoverAssessmentDO requireAssessment(Long tenantId, CutoverTaskDO task, Integer expectedVersion) {
        CutoverAssessmentDO assessment = assessmentMapper.selectForUpdate(new CutoverAssessmentRowQuery(
                tenantId, task.getId(), task.getCurrentAssessmentId()));
        require(assessment != null && CutoverTaskRules.ASSESSMENT_SUBMITTED.equals(assessment.getAssessmentStatus())
                        && !Boolean.TRUE.equals(assessment.getSimpleFlow())
                        && Set.of("A", "B", "C").contains(assessment.getManualGrade()),
                STATE_CONFLICT, "当前评估不允许生成或提交P3清单");
        if (expectedVersion != null) {
            require(Objects.equals(assessment.getVersion(), expectedVersion), VERSION_CONFLICT, "评估版本已变化");
        }
        return assessment;
    }

    private CutoverTaskDO requireP3Task(CutoverTaskDO task, Long tenantId, Long actorId, Integer expectedVersion) {
        require(task != null && Objects.equals(task.getTenantId(), tenantId), NOT_FOUND, "割接任务不存在");
        require(Objects.equals(task.getOwnerUserId(), actorId), DATA_SCOPE_FORBIDDEN, "仅任务负责人可维护P3清单");
        require(CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                        && CutoverTaskRules.STAGE_P3.equals(task.getCurrentStage())
                        && CutoverTaskRules.STATUS_SURVEYING.equals(task.getTaskStatus())
                        && Set.of("A", "B", "C").contains(task.getManualGrade()),
                STATE_CONFLICT, "当前任务不处于P3调研中");
        require(Objects.equals(task.getVersion(), expectedVersion), VERSION_CONFLICT, "任务版本已变化");
        require(positive(task.getConfigurationRevisionId()) && present(task.getConfigurationCode())
                        && task.getConfigurationRevisionNo() != null && task.getConfigurationRevisionNo() > 0,
                STATE_CONFLICT, "任务未冻结配置修订");
        return task;
    }

    private CutoverChecklistDO requireDraft(CutoverChecklistDO checklist, Integer expectedVersion) {
        require(checklist != null, NOT_FOUND, "当前清单不存在");
        require("DRAFT".equals(checklist.getStatusCode()), STATE_CONFLICT, "当前清单不可编辑");
        require(Objects.equals(checklist.getVersion(), expectedVersion), VERSION_CONFLICT, "清单版本已变化");
        return checklist;
    }

    private void lockScope(Long actorId, CutoverTaskDO task, Long expectedProjectScopeVersion) {
        requireScope(projectScopePort.lockAndRevalidate(actorId, task.getProjectId(), ACTION_EDIT,
                expectedProjectScopeVersion), task, expectedProjectScopeVersion);
    }

    private void requireScope(CutoverProjectScopePort.ProjectScopeFact fact, CutoverTaskDO task,
                              Long expectedProjectScopeVersion) {
        require(fact != null && fact.allowed() && Objects.equals(fact.projectId(), task.getProjectId()),
                DATA_SCOPE_FORBIDDEN, "无项目编辑范围");
        require(expectedProjectScopeVersion != null && fact.projectScopeVersion() == expectedProjectScopeVersion
                        && Objects.equals(task.getProjectScopeVersion(), expectedProjectScopeVersion),
                VERSION_CONFLICT, "项目范围版本已变化");
    }

    private CutoverFrozenConfiguration resolveConfiguration(CutoverTaskDO task) {
        return configurationService.resolveFrozen(new CutoverFrozenConfigurationQuery(task.getTenantId(),
                task.getConfigurationRevisionId(), task.getConfigurationCode(), task.getConfigurationRevisionNo()));
    }

    private MatchContext matchContext(CutoverTaskDO task, Integer assessmentVersion,
                                      CutoverFrozenConfiguration configuration,
                                      Map<String, GenerateChecklistCommand.SelectedDefinition> selections) {
        Map<String, List<String>> dimensions = new LinkedHashMap<>();
        dimensions.put("CUTOVER_TYPE", List.of(task.getCutoverType()));
        if (present(task.getNetworkMode())) {
            dimensions.put("NETWORK_MODE", List.of(task.getNetworkMode()));
        }
        List<String> deviceTypeCodes = deviceMapper.selectActiveByTask(new CutoverTaskDeviceListQuery(
                        task.getTenantId(), task.getId())).stream()
                .map(row -> {
                    require(present(row.getDeviceTypeCodeSnapshot())
                                    && present(row.getDeviceTypeSourceVersionSnapshot()),
                            STATE_CONFLICT, "任务设备产品类型快照不完整");
                    return row.getDeviceTypeCodeSnapshot();
                })
                .distinct()
                .sorted()
                .toList();
        require(!deviceTypeCodes.isEmpty(), STATE_CONFLICT, "任务设备产品类型快照不存在");
        dimensions.put("DEVICE_TYPE", deviceTypeCodes);
        dimensions.put("MANUAL_GRADE", List.of(task.getManualGrade()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", task.getId());
        snapshot.put("taskVersion", task.getVersion());
        snapshot.put("assessmentId", task.getCurrentAssessmentId());
        snapshot.put("assessmentVersion", assessmentVersion);
        snapshot.put("projectScopeVersion", task.getProjectScopeVersion());
        snapshot.put("configurationRevisionId", task.getConfigurationRevisionId());
        snapshot.put("configurationCode", task.getConfigurationCode());
        snapshot.put("configurationRevisionNo", task.getConfigurationRevisionNo());
        snapshot.put("dimensions", dimensions);
        CutoverChecklistMatcher.MatchResult matched = matcher.match(configuration,
                new CutoverChecklistMatcher.MatchInput(dimensions));
        List<ResolvedItem> resolved = matched.readyItems().stream()
                .map(item -> new ResolvedItem(item.item(), item.required(), item.matchedRuleIds()))
                .collect(Collectors.toCollection(ArrayList::new));
        Map<Long, CutoverFrozenConfiguration.ItemDefinition> itemsById = configuration.items().stream()
                .collect(Collectors.toMap(CutoverFrozenConfiguration.ItemDefinition::id, Function.identity()));
        for (CutoverChecklistMatcher.Conflict conflict : matched.conflicts()) {
            GenerateChecklistCommand.SelectedDefinition selected = selections.get(conflict.stableItemKey());
            require(selected != null, STATE_CONFLICT, "配置冲突尚未选择定义");
            CutoverChecklistMatcher.Candidate candidate = conflict.candidates().stream()
                    .filter(value -> Objects.equals(value.itemDefinitionId(), selected.itemDefinitionId())
                            && Objects.equals(value.itemDefinitionVersion(), selected.itemDefinitionVersion()))
                    .findFirst().orElseThrow(() -> new CutoverChecklistException(STATE_CONFLICT, "配置冲突选择无效"));
            CutoverFrozenConfiguration.ItemDefinition item = itemsById.get(candidate.itemDefinitionId());
            boolean required = item.required() || configuration.rules().stream()
                    .filter(rule -> candidate.matchedRuleIds().contains(rule.id()))
                    .anyMatch(CutoverFrozenConfiguration.BindingRule::requiredResult);
            resolved.add(new ResolvedItem(item, required, candidate.matchedRuleIds()));
        }
        resolved.sort(Comparator.comparing((ResolvedItem value) -> value.item().sortOrder(),
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(value -> value.item().stableItemKey()));
        return new MatchContext(JsonUtils.toJsonString(snapshot), JsonUtils.toJsonString(matched),
                matched.gap() ? JsonUtils.toJsonString(Map.of("reason", "NO_APPLICABLE_RULE")) : null,
                List.copyOf(resolved));
    }

    private void insertSystemItem(Long tenantId, Long actorId, CutoverChecklistDO checklist,
                                  ResolvedItem resolved, CutoverFrozenConfiguration configuration) {
        CutoverFrozenConfiguration.BindingRule primaryRule = primaryRule(configuration, resolved);
        CutoverFrozenConfiguration.ItemDefinition definition = resolved.item();
        CutoverChecklistItemDO item = new CutoverChecklistItemDO();
        item.setId(nextId());
        item.setTenantId(tenantId);
        item.setChecklistId(checklist.getId());
        item.setStableItemKey(definition.stableItemKey());
        item.setItemDefinitionId(definition.id());
        item.setItemDefinitionVersion(definition.itemDefinitionVersion());
        item.setItemTypeCode(definition.itemTypeCode());
        item.setItemName(definition.itemName());
        item.setItemDescription(definition.itemDescription());
        item.setInterfaceFormatCode(definition.interfaceFormatCode());
        item.setInterfaceSchemaSnapshot(definition.interfaceSchema());
        item.setDisplayConditionSnapshot(primaryRule == null ? null : primaryRule.dimensionConditionSnapshot());
        item.setWorkModeCode(definition.workModeCode());
        item.setRequiredFlag(resolved.required());
        item.setSourceCode("SYSTEM_MATCHED");
        item.setMatchedRuleId(primaryRule == null ? null : primaryRule.id());
        item.setMatchedRuleVersion(primaryRule == null ? null : primaryRule.version());
        item.setApplicableFlag(true);
        item.setSortOrder(definition.sortOrder());
        item.setVersion(0);
        item.setCreator(String.valueOf(actorId));
        item.setUpdater(String.valueOf(actorId));
        require(itemMapper.insert(item) == 1, STATE_CONFLICT, "系统清单项创建失败");
    }

    private static CutoverFrozenConfiguration.BindingRule primaryRule(CutoverFrozenConfiguration configuration,
                                                                       ResolvedItem resolved) {
        return configuration.rules().stream().filter(rule -> resolved.ruleIds().contains(rule.id()))
                .min(Comparator.comparing(CutoverFrozenConfiguration.BindingRule::id)).orElse(null);
    }

    private static CutoverFrozenConfiguration.ItemDefinition existingDefinition(CutoverChecklistItemDO item) {
        return new CutoverFrozenConfiguration.ItemDefinition(item.getItemDefinitionId(), item.getStableItemKey(),
                item.getItemDefinitionVersion(), item.getItemTypeCode(), item.getItemName(),
                item.getItemDescription(), item.getInterfaceFormatCode(), item.getInterfaceSchemaSnapshot(),
                item.getWorkModeCode(), Boolean.TRUE.equals(item.getRequiredFlag()), item.getSortOrder());
    }

    private int appendResult(Long tenantId, Long actorId, CutoverChecklistItemDO item, String source,
                             String answerSnapshot, String factDescription,
                             String manualReference, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        CutoverChecklistCurrentResultQuery query = new CutoverChecklistCurrentResultQuery(tenantId, item.getId());
        CutoverChecklistItemResultDO current = resultMapper.selectCurrentForUpdate(query);
        if (current != null) {
            require(resultMapper.closeCurrentIfMatch(new CutoverChecklistResultCloseUpdate(tenantId,
                    current.getId(), now)) == 1, VERSION_CONFLICT, "当前清单结果已变化");
        }
        int nextVersion = resultMapper.selectMaxVersion(query) + 1;
        CutoverChecklistItemResultDO row = new CutoverChecklistItemResultDO();
        row.setId(nextId());
        row.setTenantId(tenantId);
        row.setChecklistItemId(item.getId());
        row.setResultVersion(nextVersion);
        row.setResultSourceCode(source);
        row.setAnswerSnapshot(answerSnapshot);
        row.setFactDescription(factDescription);
        row.setManualEvidenceFileReference(manualReference);
        row.setSelectionStartedAt(now);
        row.setSelectedBy(actorId);
        row.setSelectionReasonCode(reason);
        row.setCreatedBy(actorId);
        row.setCreatedAt(now);
        row.setDeleted(false);
        require(resultMapper.insert(row) == 1, STATE_CONFLICT, "清单结果保存失败");
        return nextVersion;
    }

    private int appendCollectionResult(Long tenantId, Long actorId, CutoverChecklistItemDO item,
                                       Long deviceId, Long commandTemplateId,
                                       CutoverCollectionPort.CollectionFact fact) {
        LocalDateTime now = LocalDateTime.now(clock);
        CutoverChecklistCurrentResultQuery query = new CutoverChecklistCurrentResultQuery(tenantId, item.getId());
        CutoverChecklistItemResultDO current = resultMapper.selectCurrentForUpdate(query);
        if (current != null) {
            require(resultMapper.closeCurrentIfMatch(new CutoverChecklistResultCloseUpdate(tenantId,
                    current.getId(), now)) == 1, VERSION_CONFLICT, "当前清单结果已变化");
        }
        int nextVersion = resultMapper.selectMaxVersion(query) + 1;
        CollectionSelectionSnapshot snapshot = new CollectionSelectionSnapshot(fact.collectionTaskId(), deviceId,
                commandTemplateId, fact.technicalStatus().name(), fact.resultReferenceId(), fact.resultVersion(),
                present(fact.resultSnapshot()) ? JsonUtils.parseTree(fact.resultSnapshot()) : null,
                fact.failureCode());
        CutoverChecklistItemResultDO row = new CutoverChecklistItemResultDO();
        row.setId(nextId());
        row.setTenantId(tenantId);
        row.setChecklistItemId(item.getId());
        row.setResultVersion(nextVersion);
        row.setResultSourceCode("COLLECTION");
        row.setAnswerSnapshot(JsonUtils.toJsonString(snapshot));
        row.setCollectionTaskId(fact.collectionTaskId());
        row.setCollectionResultReferenceId(fact.resultReferenceId());
        row.setCollectionResultVersion(fact.resultVersion());
        row.setLoadFailureCode(fact.failureCode());
        row.setSelectionStartedAt(now);
        row.setSelectedBy(actorId);
        row.setSelectionReasonCode("COLLECTION_RESULT_SELECTED");
        row.setCreatedBy(actorId);
        row.setCreatedAt(now);
        row.setDeleted(false);
        require(resultMapper.insert(row) == 1, STATE_CONFLICT, "采集结果保存失败");
        return nextVersion;
    }

    private static boolean isCompletedResult(CutoverChecklistItemResultDO result) {
        return !"COLLECTION".equals(result.getResultSourceCode())
                || (positive(result.getCollectionResultReferenceId())
                && positive(result.getCollectionResultVersion()) && !present(result.getLoadFailureCode()));
    }

    private static void requireCollectionFact(Long expectedCollectionTaskId,
                                              CutoverCollectionPort.CollectionFact fact) {
        require(fact != null && Objects.equals(expectedCollectionTaskId, fact.collectionTaskId())
                        && fact.technicalStatus() != null,
                COLLECTION_FACT_INVALID, "采集事实身份不匹配");
        if (fact.technicalStatus() == CutoverCollectionPort.TechnicalStatus.COMPLETED) {
            require(positive(fact.resultReferenceId()) && positive(fact.resultVersion())
                            && present(fact.resultSnapshot()) && !present(fact.failureCode()),
                    COLLECTION_FACT_INVALID, "完成态采集事实不完整");
            try {
                JsonUtils.parseTree(fact.resultSnapshot());
            } catch (RuntimeException exception) {
                throw new CutoverChecklistException(COLLECTION_FACT_INVALID, "采集结果不是有效JSON");
            }
            return;
        }
        require(fact.resultReferenceId() == null && fact.resultVersion() == null
                        && !present(fact.resultSnapshot()), COLLECTION_FACT_INVALID, "非完成态不得携带采集结果");
        require(fact.technicalStatus() != CutoverCollectionPort.TechnicalStatus.FAILED
                        || present(fact.failureCode()), COLLECTION_FACT_INVALID, "失败态缺少失败码");
    }

    private void closeCurrentResult(Long tenantId, Long itemId) {
        CutoverChecklistItemResultDO current = resultMapper.selectCurrentForUpdate(
                new CutoverChecklistCurrentResultQuery(tenantId, itemId));
        if (current != null) {
            require(resultMapper.closeCurrentIfMatch(new CutoverChecklistResultCloseUpdate(tenantId,
                    current.getId(), LocalDateTime.now(clock))) == 1, VERSION_CONFLICT, "当前清单结果已变化");
        }
    }

    private void requireFileFact(CutoverChecklistFilePort.FileHandle expected,
                                 CutoverChecklistFilePort.FileFact actual) {
        require(actual != null && Objects.equals(expected.artifactId(), actual.artifactId())
                        && Objects.equals(expected.versionNo(), actual.versionNo())
                        && Objects.equals(expected.referenceKey(), actual.referenceKey())
                        && Objects.equals(expected.fileFactVersion(), actual.fileFactVersion())
                        && Objects.equals(expected.scopeVersion(), actual.scopeVersion())
                        && present(actual.sha256()), FILE_FACT_INVALID, "人工证据文件事实已变化");
    }

    private void insertHistory(SubmitChecklistCommand command, CutoverChecklistDO checklist, LocalDateTime now) {
        CutoverTaskStageHistoryDO history = new CutoverTaskStageHistoryDO();
        history.setId(nextId());
        history.setTenantId(command.tenantId());
        history.setCutoverTaskId(command.taskId());
        history.setSequenceNo(3);
        history.setFromStage(CutoverTaskRules.STAGE_P3);
        history.setToStage(CutoverTaskRules.STAGE_P4);
        history.setFromStatus(CutoverTaskRules.STATUS_SURVEYING);
        history.setToStatus(CutoverTaskRules.STATUS_PLAN_DRAFTING);
        history.setTriggerType("P3_CHECKLIST_SUBMITTED");
        history.setTriggerReferenceId(checklist.getId());
        history.setActorId(command.actorId());
        history.setCorrelationId(command.correlationId());
        history.setOccurredAt(now);
        history.setCreator(String.valueOf(command.actorId()));
        history.setCreateTime(now);
        require(historyMapper.insert(history) == 1, STATE_CONFLICT, "P3阶段历史创建失败");
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(String action, ChecklistCommandResult result,
                                                                  String correlationId) {
        return new PlatformCommandExecutionApi.SuccessFacts(action, "CutoverChecklist",
                String.valueOf(result.checklistId()), correlationId, JsonUtils.toJsonString(result), List.of());
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(String action, ChecklistSubmissionFacts result,
                                                                  String correlationId) {
        return new PlatformCommandExecutionApi.SuccessFacts(action, "CutoverChecklist",
                String.valueOf(result.checklistId()), correlationId, JsonUtils.toJsonString(result), List.of());
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(String action,
                                                                  CollectionRequestCommandResult result,
                                                                  Long tenantId,
                                                                  String correlationId) {
        List<PlatformCommandExecutionApi.BusinessEvent> events = result.resultLinked()
                ? List.of(resultLinkedEvent(result, tenantId, correlationId)) : List.of();
        return new PlatformCommandExecutionApi.SuccessFacts(action, "CutoverChecklistItem",
                String.valueOf(result.checklistItemId()), correlationId, JsonUtils.toJsonString(result), events);
    }

    private PlatformCommandExecutionApi.BusinessEvent resultLinkedEvent(CollectionRequestCommandResult result,
                                                                         Long tenantId, String correlationId) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("tenantId", tenantId);
        payload.put("taskId", result.taskId());
        payload.put("taskVersion", result.taskVersion());
        payload.put("checklistId", result.checklistId());
        payload.put("checklistVersion", result.checklistBusinessVersion());
        payload.put("checklistFactVersion", result.checklistVersion());
        payload.put("stableItemKey", result.stableItemKey());
        payload.put("itemVersion", result.itemVersion());
        payload.put("resultVersion", result.resultVersion());
        payload.put("collectionTaskId", result.collectionTaskId());
        payload.put("resultReferenceId", result.collectionResultReferenceId());
        payload.put("collectionResultVersion", result.collectionResultVersion());
        payload.put("resultSourceCode", "COLLECTION");
        payload.put("correlationId", correlationId);
        return new PlatformCommandExecutionApi.BusinessEvent(eventId, "CutoverChecklistItemResultLinked",
                JsonUtils.toJsonString(payload));
    }

    private static CollectionSelectionSnapshot collectionSnapshot(CutoverChecklistItemResultDO current) {
        if (current == null || !"COLLECTION".equals(current.getResultSourceCode())
                || !present(current.getAnswerSnapshot())) {
            return null;
        }
        try {
            CollectionSelectionSnapshot snapshot = JsonUtils.parseObject(current.getAnswerSnapshot(),
                    CollectionSelectionSnapshot.class);
            require(snapshot != null && positive(snapshot.collectionTaskId()) && positive(snapshot.deviceId())
                            && positive(snapshot.commandTemplateId()) && present(snapshot.technicalStatus()),
                    COLLECTION_FACT_INVALID, "当前采集选择事实不完整");
            return snapshot;
        } catch (CutoverChecklistException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CutoverChecklistException(COLLECTION_FACT_INVALID, "当前采集选择事实损坏");
        }
    }

    private ChecklistCommandResult result(CutoverTaskDO task, CutoverChecklistDO checklist,
                                          Integer version, String status, boolean replayed) {
        return new ChecklistCommandResult(task.getId(), checklist.getId(), checklist.getChecklistVersion(),
                version, status, task.getCurrentStage(), task.getVersion(), replayed);
    }

    private static Map<String, CutoverChecklistItemDO> byStableKey(List<CutoverChecklistItemDO> items) {
        return items.stream().collect(Collectors.toMap(CutoverChecklistItemDO::getStableItemKey, Function.identity()));
    }

    private static int commandSortOrder(AddCustomItemCommand command, List<CutoverChecklistItemDO> items) {
        if (command.interfaceSchema() == null || command.interfaceSchema().isBlank()) {
            throw new CutoverChecklistException(INVALID_REQUEST, "自定义项Schema不能为空");
        }
        return items.stream().map(CutoverChecklistItemDO::getSortOrder).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 10;
    }

    private static void requireGenerate(GenerateChecklistCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && nonNegative(command.expectedTaskVersion())
                        && nonNegative(command.expectedAssessmentVersion())
                        && positive(command.expectedProjectScopeVersion()), INVALID_REQUEST, "生成清单命令非法");
        requireText(command.idempotencyKey(), "Idempotency-Key", 128);
        requireText(command.correlationId(), "correlationId", 128);
    }

    private static void requireSave(SaveChecklistCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && positive(command.checklistId())
                        && nonNegative(command.expectedTaskVersion()) && nonNegative(command.expectedChecklistVersion())
                        && positive(command.expectedProjectScopeVersion()) && !command.answers().isEmpty(),
                INVALID_REQUEST, "保存清单命令非法");
    }

    private static void requireRematch(RematchChecklistCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && positive(command.checklistId())
                        && nonNegative(command.expectedTaskVersion())
                        && nonNegative(command.expectedAssessmentVersion())
                        && nonNegative(command.expectedChecklistVersion())
                        && present(command.expectedInputSnapshotHash())
                        && positive(command.expectedProjectScopeVersion()), INVALID_REQUEST, "重匹配命令非法");
        requireText(command.idempotencyKey(), "Idempotency-Key", 128);
        requireText(command.correlationId(), "correlationId", 128);
    }

    private static void requireCustom(AddCustomItemCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && positive(command.checklistId())
                        && nonNegative(command.expectedTaskVersion()) && nonNegative(command.expectedChecklistVersion())
                        && positive(command.expectedProjectScopeVersion()), INVALID_REQUEST, "自定义项命令非法");
        requireText(command.itemTypeCode(), "itemTypeCode", 32);
        requireText(command.itemName(), "itemName", 255);
        requireText(command.interfaceFormatCode(), "interfaceFormatCode", 32);
    }

    private static void requireRemoveCustom(RemoveCustomItemCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && positive(command.checklistId())
                        && nonNegative(command.expectedTaskVersion()) && nonNegative(command.expectedChecklistVersion())
                        && positive(command.expectedProjectScopeVersion()), INVALID_REQUEST, "移出自定义项命令非法");
        requireText(command.stableItemKey(), "stableItemKey", 96);
    }

    private static void requireCollection(RequestCollectionCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && positive(command.checklistId())
                        && nonNegative(command.expectedTaskVersion()) && nonNegative(command.expectedChecklistVersion())
                        && positive(command.expectedProjectScopeVersion()) && positive(command.deviceId())
                        && positive(command.commandTemplateId()), INVALID_REQUEST, "采集请求命令非法");
        requireText(command.stableItemKey(), "stableItemKey", 96);
        requireText(command.idempotencyKey(), "Idempotency-Key", 128);
        requireText(command.correlationId(), "correlationId", 128);
    }

    private static void requireManual(SelectManualResultCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && positive(command.checklistId())
                        && nonNegative(command.expectedTaskVersion()) && nonNegative(command.expectedChecklistVersion())
                        && positive(command.expectedProjectScopeVersion()) && command.fileHandle() != null,
                INVALID_REQUEST, "人工证据命令非法");
        requireText(command.stableItemKey(), "stableItemKey", 96);
    }

    private static void requireSubmit(SubmitChecklistCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && positive(command.checklistId())
                        && nonNegative(command.expectedTaskVersion()) && nonNegative(command.expectedAssessmentVersion())
                        && nonNegative(command.expectedChecklistVersion()) && positive(command.expectedProjectScopeVersion()),
                INVALID_REQUEST, "提交清单命令非法");
        requireText(command.idempotencyKey(), "Idempotency-Key", 128);
        requireText(command.correlationId(), "correlationId", 128);
    }

    private static void requireCompleted(PlatformCommandExecutionApi.Decision decision) {
        if (decision == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new CutoverChecklistException(IDEMPOTENCY_CONFLICT, "Idempotency-Key载荷冲突");
        }
        if (decision == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw new CutoverChecklistException(IDEMPOTENCY_IN_PROGRESS, "相同命令正在处理");
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(present(value) && value.equals(value.trim()) && value.length() <= maxLength,
                INVALID_REQUEST, field + "格式非法");
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static boolean nonNegative(Integer value) {
        return value != null && value >= 0;
    }

    private static void require(boolean condition, CutoverChecklistException.Code code, String message) {
        if (!condition) {
            throw new CutoverChecklistException(code, message);
        }
    }

    private static Long nextId() {
        return ID_GENERATOR.nextId();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record LockedDraft(CutoverTaskDO task, CutoverChecklistDO checklist,
                               List<CutoverChecklistItemDO> items) {
    }

    private record ResolvedItem(CutoverFrozenConfiguration.ItemDefinition item, boolean required,
                                List<Long> ruleIds) {
    }

    private record MatchContext(String inputSnapshot, String matchTrace, String gapSnapshot,
                                List<ResolvedItem> items) {
    }

    private record CollectionSelectionSnapshot(Long collectionTaskId, Long deviceId, Long commandTemplateId,
                                               String technicalStatus, Long resultReferenceId,
                                               Long resultVersion, Object resultSnapshot, String failureCode) {
    }
}
