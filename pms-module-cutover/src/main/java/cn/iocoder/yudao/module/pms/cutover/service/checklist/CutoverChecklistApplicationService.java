package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistDraftMutation;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.CutoverChecklistMatchMutation;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistMatchingOptions;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy.Option;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.CutoverTaskMutation;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskStageHistoryDO;
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
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskChecklistSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskRowQuery;
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
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
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
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.FROZEN_CONFIGURATION_INVALID;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.INVALID_REQUEST;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.NOT_FOUND;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.STATE_CONFLICT;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.VERSION_CONFLICT;

/** P3 清单运行服务；由割接运行配置装配正式 Owner 适配器。 */
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
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor commands;
    private final CutoverNavigationDecisionQueryService navigationDecisionQueryService;
    private final Clock clock;
    @jakarta.annotation.Resource
    private CutoverChecklistResultStore resultStorage;
    @jakarta.annotation.Resource
    private CutoverChecklistMetadataStore checklistMetadata;
    @jakarta.annotation.Resource
    private CutoverCustomResultStore customResultStorage;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore fieldDefinitions;
    @jakarta.annotation.Resource
    private CutoverExternalBulletinStore externalBulletins;


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
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(command.taskId()), command);
        requireScope(projectScopePort.inspect(command.actorId(), snapshot.getProjectId(), ACTION_EDIT),
                snapshot, command.expectedProjectScopeVersion());
        CutoverFrozenConfiguration configuration = resolveConfiguration(snapshot);
        MatchContext context = matchContext(snapshot, configuration, command);
        PlatformCommandExecutionApi.ExecutionResult<ChecklistCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:CHECKLIST_GENERATE:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                        "taskVersion", command.expectedTaskVersion(),
                        "assessmentVersion", command.expectedAssessmentVersion(),
                        "projectScopeVersion", command.expectedProjectScopeVersion(),
                        "deviceTypeCodes", command.deviceTypeCodes().stream().sorted().toList(),
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
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(command.taskId()), command);
        requireScope(projectScopePort.inspect(command.actorId(), snapshot.getProjectId(), ACTION_EDIT),
                snapshot, command.expectedProjectScopeVersion());
        CutoverFrozenConfiguration configuration = resolveConfiguration(snapshot);
        MatchContext context = matchContext(snapshot, configuration, command);
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
                        "deviceTypeCodes", command.deviceTypeCodes().stream().sorted().toList(),
                        "selectedConflictDefinitions", command.selectedConflictDefinitions()))),
                ChecklistCommandResult.class, () -> rematchOnce(command, configuration, context),
                result -> successFacts("CUTOVER_CHECKLIST_REMATCH", result, command.correlationId()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    public CutoverChecklistMatchingOptions getMatchingOptions(CutoverChecklistContext context, Long taskId) {
        require(context != null && positive(taskId), INVALID_REQUEST, "清单匹配选项查询参数非法");
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && Objects.equals(task.getTenantId(), context.tenantId()), NOT_FOUND, "割接任务不存在");
        var scope = projectScopePort.inspect(context.actorId(), task.getProjectId(), ACTION_VIEW);
        require(scope != null && scope.allowed() && Objects.equals(scope.projectId(), task.getProjectId()),
                NOT_FOUND, "割接任务不存在");
        CutoverFrozenConfiguration configuration = resolveConfiguration(task);
        CutoverChecklistDO checklist = checklistMapper.selectCurrent(new CutoverChecklistRowQuery(
                context.tenantId(), taskId, null));
        List<String> selected = new ArrayList<>();
        if (checklist != null) {
            selected.addAll(checklistMetadata.dimensions(checklist).getOrDefault("DEVICE_TYPE", List.of()));
        }
        return new CutoverChecklistMatchingOptions(configuration.revisionId(), configuration.revisionNo(),
                dimensionOptions(configuration, "CUTOVER_TYPE"), dimensionOptions(configuration, "NETWORK_MODE"),
                dimensionOptions(configuration, "DEVICE_TYPE"), List.copyOf(selected));
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
        List<CutoverChecklistItemDO> items = withSchemas(itemMapper.selectListByChecklist(new CutoverChecklistItemsQuery(
                tenantId, checklist.getId())));
        Map<Long, CutoverChecklistItemResultDO> currentResults = resultMapper.selectCurrentByChecklist(
                        new CutoverChecklistResultsQuery(tenantId, checklist.getId())).stream()
                .collect(Collectors.toMap(CutoverChecklistItemResultDO::getChecklistItemId, Function.identity()));
        List<CutoverChecklistView.Item> viewItems = items.stream().map(item -> {
            CutoverChecklistItemResultDO current = currentResults.get(item.getId());
            CutoverChecklistView.CurrentResult resultView = current == null ? null
                    : new CutoverChecklistView.CurrentResult(current.getResultVersion(),
                    current.getResultSourceCode(), resultStorage.answer(new CutoverChecklistResultStore.Read(item, current)), current.getFactDescription(),
                    manualReference(current), current.getCollectionTaskId(),
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
                checklist.getInputSnapshotHash(), JsonUtils.toJsonString(configurationService.resolveFrozen(new CutoverFrozenConfigurationQuery(
                        tenantId, checklist.getConfigurationRevisionId(), checklist.getConfigurationCode(), checklist.getConfigurationRevisionNo()))),
                checklistMetadata.matchTrace(checklist), checklistMetadata.gap(checklist), viewItems);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistCommandResult save(SaveChecklistCommand command) {
        requireSave(command);
        authorizeDraftAccess(command);
        return commands.execute(cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor.Write.<ChecklistCommandResult>builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).operationCode("CUTOVER_CHECKLIST_SAVE")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverChecklist").intent(command.intent())
                .businessInput(command).responseType(ChecklistCommandResult.class).operation(() -> saveNew(command))
                .resourceKey(value -> String.valueOf(command.checklistId())).build());
    }

    private ChecklistCommandResult saveNew(SaveChecklistCommand command) {
        LockedDraft locked = lockDraft(command);
        Map<String, CutoverChecklistItemDO> items = byStableKey(locked.items());
        Set<String> seen = new LinkedHashSet<>();
        for (SaveChecklistCommand.DirectAnswer answer : command.answers()) {
            require(answer != null && present(answer.stableItemKey()) && present(answer.answerSnapshot()),
                    INVALID_REQUEST, "直接填写答案不完整");
            require(seen.add(answer.stableItemKey()), INVALID_REQUEST, "直接填写项重复");
            CutoverChecklistItemDO item = items.get(answer.stableItemKey());
            require(item != null && Boolean.TRUE.equals(item.getApplicableFlag()), NOT_FOUND, "清单项不存在");
            CutoverChecklistAnswerValidator.validate(item, answer.answerSnapshot(), false);
            if ("COLLECTION".equals(item.getWorkModeCode())) {
                CutoverChecklistItemResultDO prior = resultMapper.selectCurrentForUpdate(
                        new CutoverChecklistCurrentResultQuery(command.tenantId(), item.getId()));
                require(prior != null && "COLLECTION".equals(prior.getResultSourceCode())
                                && positive(prior.getCollectionResultReferenceId()) && !present(prior.getLoadFailureCode()),
                        STATE_CONFLICT, "须取得本项采集结果后填写核查结论，失败时可使用人工证据");
                appendCollectionAnalysis(command, item, prior, answer);
            } else {
                require("DIRECT".equals(item.getWorkModeCode()), STATE_CONFLICT, "当前清单项不支持直接填写");
                appendResult(new ResultWrite(item, CutoverChecklistItemResultDO.builder()
                .tenantId(command.tenantId())
                .selectedBy(command.actorId())
                .createdBy(command.actorId())
                .checklistItemId(item.getId())
                .resultSourceCode("DIRECT")
                .factDescription(null)
                .manualEvidenceFileReference(null)
                .selectionReasonCode("DIRECT_SAVE")
                .build(), answer.answerSnapshot(), null));
            }
        }
        require(checklistMapper.touchDraftIfMatch(new CutoverChecklistDraftTouchUpdate(command.tenantId(),
                command.checklistId(), command.expectedChecklistVersion())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        return result(locked.task(), locked.checklist(), command.expectedChecklistVersion() + 1, "DRAFT", false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistItemCommandResult addCustomItem(AddCustomItemCommand command) {
        requireCustom(command);
        authorizeDraftAccess(command);
        return commands.execute(cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor.Write.<ChecklistItemCommandResult>builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).operationCode("CUTOVER_CHECKLIST_CUSTOM_ADD")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverChecklist").intent(command.intent())
                .businessInput(command).responseType(ChecklistItemCommandResult.class).operation(() -> addCustomItemNew(command))
                .resourceKey(value -> String.valueOf(command.checklistId())).build());
    }

    private ChecklistItemCommandResult addCustomItemNew(AddCustomItemCommand command) {
        LockedDraft locked = lockDraft(command);
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
        fieldDefinitions.insert(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec.Write.builder()
                .tenantId(command.tenantId()).customChecklistItemId(item.getId()).itemTypeCode(command.itemTypeCode())
                .schema(JsonUtils.parseObject(command.interfaceSchema(), Map.class)).build());
        Integer resultVersion = null;
        if (present(command.answerSnapshot())) {
            appendResult(new ResultWrite(item, CutoverChecklistItemResultDO.builder()
                .tenantId(command.tenantId())
                .selectedBy(command.actorId())
                .createdBy(command.actorId())
                .checklistItemId(item.getId())
                .resultSourceCode("DIRECT")
                .factDescription(null)
                .manualEvidenceFileReference(null)
                .selectionReasonCode("CUSTOM_DIRECT_SAVE")
                .build(), command.answerSnapshot(), null));
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
        authorizeDraftAccess(command);
        return commands.execute(cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor.Write.<ChecklistItemCommandResult>builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).operationCode("CUTOVER_CHECKLIST_CUSTOM_REMOVE")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverChecklist").intent(command.intent())
                .businessInput(command).responseType(ChecklistItemCommandResult.class).operation(() -> removeCustomItemNew(command))
                .resourceKey(value -> String.valueOf(command.checklistId())).build());
    }

    private ChecklistItemCommandResult removeCustomItemNew(RemoveCustomItemCommand command) {
        LockedDraft locked = lockDraft(command);
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
        return requestCollection(command, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public CollectionRequestCommandResult requestCollection(RequestCollectionCommand command, Long existingExecutionId) {
        requireCollection(command);
        require(existingExecutionId == null || positive(existingExecutionId), INVALID_REQUEST, "采集记录标识无效");
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
                                "commandTemplateId", command.commandTemplateId(),
                                "existingExecutionId", existingExecutionId == null ? 0L : existingExecutionId))),
                        CollectionRequestCommandResult.class, () -> requestCollectionOnce(command, existingExecutionId),
                        result -> successFacts("CUTOVER_CHECKLIST_COLLECTION_REQUEST", result, command));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public ChecklistItemCommandResult selectManual(SelectManualResultCommand command) {
        requireManual(command);
        authorizeDraftAccess(command);
        return commands.execute(cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor.Write.<ChecklistItemCommandResult>builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).operationCode("CUTOVER_CHECKLIST_MANUAL_RESULT")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverChecklist").intent(command.intent())
                .businessInput(command).responseType(ChecklistItemCommandResult.class).operation(() -> selectManualNew(command))
                .resourceKey(value -> String.valueOf(command.checklistId())).build());
    }

    private ChecklistItemCommandResult selectManualNew(SelectManualResultCommand command) {
        LockedDraft locked = lockDraft(command);
        CutoverChecklistItemDO item = byStableKey(locked.items()).get(command.stableItemKey());
        require(item != null && Boolean.TRUE.equals(item.getApplicableFlag()), NOT_FOUND, "清单项不存在");
        CutoverChecklistFilePort.FileFact fact = filePort.lockAndRevalidate(CutoverChecklistFilePort.FileQuery.builder()
                .tenantId(command.tenantId())
                .actorId(command.actorId())
                .projectId(locked.task().getProjectId())
                .checklistItemId(item.getId())
                .expectedScopeVersion(command.expectedProjectScopeVersion())
                .handle(command.fileHandle())
                .build());
        requireFileFact(command.fileHandle(), fact);
        CutoverChecklistItemResultDO prior = resultMapper.selectCurrentForUpdate(new CutoverChecklistCurrentResultQuery(command.tenantId(), item.getId()));
        if ("EXTERNAL".equals(item.getWorkModeCode())) {
            externalBulletins.requireManual(new CutoverExternalBulletinStore.Read(item, prior));
        }
        int resultVersion = appendResult(new ResultWrite(item, CutoverChecklistItemResultDO.builder()
                .tenantId(command.tenantId())
                .selectedBy(command.actorId())
                .createdBy(command.actorId())
                .checklistItemId(item.getId())
                .resultSourceCode("MANUAL")
                .originalFailedCollectionTaskId(prior != null && "COLLECTION".equals(prior.getResultSourceCode())
                        && present(prior.getLoadFailureCode()) ? prior.getCollectionTaskId() : null)
                .originalFailedResultVersion(prior != null && "COLLECTION".equals(prior.getResultSourceCode())
                        && present(prior.getLoadFailureCode()) ? prior.getResultVersion().longValue() : null)
                .originalFailureCode(prior != null && "COLLECTION".equals(prior.getResultSourceCode())
                        && present(prior.getLoadFailureCode()) ? prior.getLoadFailureCode() : null)
                .factDescription(command.factDescription())
                .selectionReasonCode("MANUAL_EVIDENCE_SELECTED")
                .build(), null, fact));
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
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command);
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
        checklist.setChecklistVersion(1L);
        checklist.setStatusCode("DRAFT");
        checklist.setInputSnapshotHash(sha256(context.inputSnapshot()));
        CutoverChecklistMetadataStore.applyRoot(new CutoverChecklistMetadataStore.RootWrite(checklist, task, context.gap()));
        checklist.setVersion(0L);
        checklist.setCreator(String.valueOf(command.actorId()));
        checklist.setUpdater(String.valueOf(command.actorId()));
        require(checklistMapper.insert(checklist) == 1, STATE_CONFLICT, "清单创建失败");
        checklistMetadata.replace(CutoverChecklistMetadataStore.Write.builder().checklist(checklist).configuration(configuration)
                .dimensions(context.dimensions()).matchedRuleIds(context.matchedRuleIds())
                .selectedRuleIds(context.items().stream().flatMap(value -> value.ruleIds().stream()).collect(Collectors.toSet()))
                .actorId(command.actorId()).time(LocalDateTime.now(clock)).build());
        for (ResolvedItem resolved : context.items()) {
            insertSystemItem(command, checklist, resolved, configuration);
        }
        return result(task, checklist, 0L, "DRAFT", false);
    }

    private ChecklistCommandResult rematchOnce(RematchChecklistCommand command,
                                               CutoverFrozenConfiguration configuration,
                                               MatchContext context) {
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(command.taskId()), command);
        lockScope(command.actorId(), snapshot, command.expectedProjectScopeVersion());
        CutoverTaskDO task = requireP3Task(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command);
        CutoverAssessmentDO assessment = requireAssessment(command.tenantId(), task,
                command.expectedAssessmentVersion());
        CutoverChecklistDO checklist = requireDraft(checklistMapper.selectCurrentForUpdate(
                new CutoverChecklistRowQuery(command.tenantId(), task.getId(), command.checklistId())),
                command.expectedChecklistVersion());
        require(Objects.equals(checklist.getInputSnapshotHash(), command.expectedInputSnapshotHash()),
                VERSION_CONFLICT, "清单匹配输入已变化");
        List<CutoverChecklistItemDO> items = withSchemas(itemMapper.selectListForUpdate(
                new CutoverChecklistItemsQuery(command.tenantId(), checklist.getId())));
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
            item.setApplicableFlag(resolved != null); item.setRequiredFlag(resolved != null && resolved.required());
            item.setItemDefinitionId(definition.id()); item.setItemDefinitionVersion(definition.itemDefinitionVersion());
            item.setItemTypeCode(definition.itemTypeCode()); item.setItemName(definition.itemName());
            item.setItemDescription(definition.itemDescription()); item.setInterfaceFormatCode(definition.interfaceFormatCode());
            item.setWorkModeCode(definition.workModeCode()); item.setMatchedRuleId(rule == null ? null : rule.id());
            item.setMatchedRuleVersion(rule == null ? null : rule.version()); item.setSortOrder(definition.sortOrder());
            item.setUpdater(String.valueOf(command.actorId()));
            require(itemMapper.updateApplicability(new CutoverChecklistItemApplicabilityUpdate(item)) == 1,
                    VERSION_CONFLICT, "清单项已变化");
        }
        for (ResolvedItem resolved : context.items()) {
            if (!existing.containsKey(resolved.item().stableItemKey())) {
                insertSystemItem(command, checklist, resolved, configuration);
            }
        }
        Long nextChecklistVersion = checklist.getChecklistVersion() + 1;
        checklist.setChecklistVersion(nextChecklistVersion); checklist.setAssessmentId(assessment.getId());
        checklist.setAssessmentVersion(assessment.getAssessmentVersion()); checklist.setInputSnapshotHash(sha256(context.inputSnapshot()));
        checklist.setUpdater(String.valueOf(command.actorId()));
        CutoverChecklistMetadataStore.applyRoot(new CutoverChecklistMetadataStore.RootWrite(checklist, task, context.gap()));
        require(checklistMapper.rematchIfMatch(new CutoverChecklistRematchUpdate(checklist, command.expectedChecklistVersion())) == 1,
                VERSION_CONFLICT, "清单版本已变化");
        checklistMetadata.replace(CutoverChecklistMetadataStore.Write.builder().checklist(checklist).configuration(configuration)
                .dimensions(context.dimensions()).matchedRuleIds(context.matchedRuleIds())
                .selectedRuleIds(context.items().stream().flatMap(value -> value.ruleIds().stream()).collect(Collectors.toSet()))
                .actorId(command.actorId()).time(LocalDateTime.now(clock)).build());
        return new ChecklistCommandResult(task.getId(), checklist.getId(), nextChecklistVersion,
                command.expectedChecklistVersion() + 1, "DRAFT", task.getCurrentStage(), task.getVersion(), false);
    }

    private ChecklistSubmissionFacts submitOnce(SubmitChecklistCommand command) {
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(command.taskId()), command);
        lockScope(command.actorId(), snapshot, command.expectedProjectScopeVersion());
        CutoverTaskDO task = requireP3Task(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command);
        CutoverAssessmentDO assessment = requireAssessment(command.tenantId(), task,
                command.expectedAssessmentVersion());
        CutoverChecklistDO checklist = requireDraft(checklistMapper.selectCurrentForUpdate(
                new CutoverChecklistRowQuery(command.tenantId(), task.getId(), command.checklistId())),
                command.expectedChecklistVersion());
        List<CutoverChecklistItemDO> items = withSchemas(itemMapper.selectListForUpdate(
                new CutoverChecklistItemsQuery(command.tenantId(), checklist.getId())));
        List<CutoverChecklistItemResultDO> results = resultMapper.selectCurrentByChecklistForUpdate(
                new CutoverChecklistResultsQuery(command.tenantId(), checklist.getId()));
        if (checklist.getConfigGapReasonCode() != null) {
            require(items.stream().anyMatch(item -> "CUSTOM".equals(item.getSourceCode())
                            && Boolean.TRUE.equals(item.getApplicableFlag())),
                    STATE_CONFLICT, "配置缺口尚未通过自定义清单项补足");
        }
        Map<Long, CutoverChecklistItemDO> definitions = items.stream().collect(Collectors.toMap(CutoverChecklistItemDO::getId, Function.identity()));
        for (CutoverChecklistItemResultDO answer : results) {
            CutoverChecklistItemDO definition = definitions.get(answer.getChecklistItemId());
            if (definition != null && Boolean.TRUE.equals(definition.getApplicableFlag())
                    && "EXTERNAL".equals(definition.getWorkModeCode())) {
                externalBulletins.validateSubmission(new CutoverExternalBulletinStore.Read(definition, answer));
                if ("EXTERNAL".equals(answer.getResultSourceCode())) {
                    CutoverChecklistAnswerValidator.validate(definition,
                            resultStorage.answer(new CutoverChecklistResultStore.Read(definition, answer)), true);
                }
            }
            if (definition != null && Boolean.TRUE.equals(definition.getApplicableFlag()) && "DIRECT".equals(answer.getResultSourceCode())) {
                CutoverChecklistAnswerValidator.validate(definition, resultStorage.answer(new CutoverChecklistResultStore.Read(definition, answer)), true);
            } else if (definition != null && Boolean.TRUE.equals(definition.getApplicableFlag())
                    && "COLLECTION".equals(answer.getResultSourceCode()) && isCompletedResult(answer)) {
                CutoverChecklistAnswerValidator.validate(definition,
                        JsonUtils.toJsonString(Map.of("value", resultStorage.value(new CutoverChecklistResultStore.Read(definition, answer)))), true);
            }
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

    /** 平台异步回传只刷新已经选择的执行，人工选择和更新执行优先，提交后保持冻结。 */
    @Transactional(rollbackFor = Exception.class)
    public void receivePlatformResult(cn.iocoder.yudao.module.pms.platform.api.collection.CollectionBusinessResultReceiver.Result result) {
        CutoverChecklistItemDO item = itemMapper.selectById(result.objectId());
        require(item != null && Objects.equals(item.getTenantId(), result.tenantId()), NOT_FOUND, "清单项不存在");
        CutoverChecklistDO snapshot = checklistMapper.selectById(item.getChecklistId());
        CutoverTaskDO task = taskMapper.selectForUpdate(new CutoverTaskRowQuery(result.tenantId(), snapshot.getCutoverTaskId()));
        CutoverChecklistDO checklist = checklistMapper.selectCurrentForUpdate(new CutoverChecklistRowQuery(
                result.tenantId(), task.getId(), snapshot.getId()));
        if (checklist == null || !"DRAFT".equals(checklist.getStatusCode()) || !"P3".equals(task.getCurrentStage())) return;
        withSchemas(itemMapper.selectListForUpdate(new CutoverChecklistItemsQuery(result.tenantId(), checklist.getId())));
        CutoverChecklistItemResultDO selected = resultMapper.selectCurrentForUpdate(
                new CutoverChecklistCurrentResultQuery(result.tenantId(), item.getId()));
        if (selected == null || !"COLLECTION".equals(selected.getResultSourceCode())
                || !Objects.equals(selected.getCollectionTaskId(), result.executionId())) return;
        CollectionSelectionSnapshot prior = collectionSnapshot(selected);
        if (prior == null || !Objects.equals(prior.deviceId(), result.deviceId())) throw new IllegalStateException("CUT_COLLECTION_RESULT_MISMATCH");
        boolean success = Set.of("SUCCESS", "SUCCEEDED").contains(result.externalStatus());
        CutoverCollectionPort.CollectionFact fact = new CutoverCollectionPort.CollectionFact(result.executionId(),
                success ? CutoverCollectionPort.TechnicalStatus.COMPLETED : CutoverCollectionPort.TechnicalStatus.FAILED,
                success ? result.fileVersionId() : null, success ? result.resultVersion() : null,
                success ? JsonUtils.toJsonString(result) : null,
                success ? null : present(result.failureCategory()) ? result.failureCategory() : result.externalStatus());
        appendCollectionResult(CutoverCollectionPort.Inspection.builder()
                .tenantId(result.tenantId()).actorId(result.actorId()).projectId(task.getProjectId())
                .collectionTaskId(result.executionId()).taskId(task.getId()).checklistId(checklist.getId())
                .checklistItemId(item.getId()).deviceId(result.deviceId()).commandTemplateId(prior.commandTemplateId()).build(), fact);
        require(checklistMapper.touchDraftIfMatch(new CutoverChecklistDraftTouchUpdate(result.tenantId(), checklist.getId(),
                checklist.getVersion())) == 1, VERSION_CONFLICT, "清单版本已变化");
    }

    private CollectionRequestCommandResult requestCollectionOnce(RequestCollectionCommand command, Long existingExecutionId) {
        LockedDraft locked = lockDraft(command);
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
        if (existingExecutionId != null) {
            collectionTaskId = existingExecutionId;
        } else if (currentSnapshot != null && Set.of("ACCEPTED", "RUNNING").contains(currentSnapshot.technicalStatus())) {
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
        var inspection = CutoverCollectionPort.Inspection.builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).projectId(locked.task().getProjectId())
                .collectionTaskId(collectionTaskId).taskId(command.taskId()).checklistId(command.checklistId())
                .checklistItemId(item.getId()).deviceId(command.deviceId()).commandTemplateId(command.commandTemplateId()).build();
        CutoverCollectionPort.CollectionFact fact = collectionPort.inspect(inspection);
        requireCollectionFact(collectionTaskId, fact);
        int resultVersion = appendCollectionResult(inspection, fact);
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

    /** 复用同一任务负责人、项目范围、状态、版本和行锁。调用方必须先建立短事务。 */
    ExternalDraft lockExternal(CutoverChecklistDraftMutation command) {
        require(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),
                STATE_CONFLICT, "外部加载必须在受控事务中锁定清单");
        require(command != null && positive(command.tenantId()) && positive(command.actorId()) && positive(command.taskId())
                        && positive(command.checklistId()) && nonNegative(command.expectedTaskVersion())
                        && nonNegative(command.expectedChecklistVersion()) && positive(command.expectedProjectScopeVersion()),
                INVALID_REQUEST, "外部清单操作参数非法");
        var locked = lockDraft(command);
        return new ExternalDraft(locked.task(), locked.checklist(), locked.items());
    }

    record ExternalDraft(CutoverTaskDO task, CutoverChecklistDO checklist, List<CutoverChecklistItemDO> items) { }

    /** 重放也重新检查当前身份与编辑范围，状态及版本检查只在首次写入的事务中执行。 */
    public void authorizeDraftAccess(CutoverChecklistDraftMutation command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId()) && positive(command.taskId()),
                INVALID_REQUEST, "清单命令身份非法");
        var task = taskMapper.selectById(command.taskId());
        require(task != null && Objects.equals(task.getTenantId(), command.tenantId()), NOT_FOUND, "割接任务不存在");
        require(Objects.equals(task.getOwnerUserId(), command.actorId()), DATA_SCOPE_FORBIDDEN, "仅任务负责人可维护P3清单");
        var scope = projectScopePort.inspect(command.actorId(), task.getProjectId(), ACTION_EDIT);
        require(scope != null && scope.allowed() && Objects.equals(scope.projectId(), task.getProjectId()),
                DATA_SCOPE_FORBIDDEN, "无项目编辑权限");
    }

    private LockedDraft lockDraft(CutoverChecklistDraftMutation command) {
        Long tenantId = command.tenantId(); Long actorId = command.actorId(); Long taskId = command.taskId();
        Long checklistId = command.checklistId();
        Long expectedChecklistVersion = command.expectedChecklistVersion();
        Long expectedProjectScopeVersion = command.expectedProjectScopeVersion();
        CutoverTaskDO snapshot = requireP3Task(taskMapper.selectById(taskId), command);
        lockScope(actorId, snapshot, expectedProjectScopeVersion);
        CutoverTaskDO task = requireP3Task(taskMapper.selectForUpdate(new CutoverTaskRowQuery(tenantId, taskId)), command);
        requireAssessment(tenantId, task, null);
        CutoverChecklistDO checklist = requireDraft(checklistMapper.selectCurrentForUpdate(
                new CutoverChecklistRowQuery(tenantId, taskId, checklistId)), expectedChecklistVersion);
        List<CutoverChecklistItemDO> items = withSchemas(itemMapper.selectListForUpdate(
                new CutoverChecklistItemsQuery(tenantId, checklistId)));
        return new LockedDraft(task, checklist, items);
    }

    private CutoverAssessmentDO requireAssessment(Long tenantId, CutoverTaskDO task, Long expectedVersion) {
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

    private CutoverTaskDO requireP3Task(CutoverTaskDO task, CutoverTaskMutation command) {
        Long tenantId = command.tenantId(); Long actorId = command.actorId();
        Long expectedVersion = command.expectedTaskVersion();
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

    private CutoverChecklistDO requireDraft(CutoverChecklistDO checklist, Long expectedVersion) {
        require(checklist != null, NOT_FOUND, "当前清单不存在");
        require("DRAFT".equals(checklist.getStatusCode()), STATE_CONFLICT, "当前清单不可编辑");
        require(Objects.equals(checklist.getVersion(), expectedVersion), VERSION_CONFLICT, "清单版本已变化");
        return checklist;
    }

    private void lockScope(Long actorId, CutoverTaskDO task, Long expectedProjectScopeVersion) {
        requireScope(projectScopePort.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(actorId)
                .projectId(task.getProjectId())
                .action(ACTION_EDIT)
                .expectedProjectScopeVersion(expectedProjectScopeVersion)
                .build()), task, expectedProjectScopeVersion);
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

    private MatchContext matchContext(CutoverTaskDO task, CutoverFrozenConfiguration configuration,
                                      CutoverChecklistMatchMutation command) {
        List<Option> options = dimensionOptions(configuration, "DEVICE_TYPE");
        Set<String> allowed = options.stream().map(Option::value).collect(Collectors.toSet());
        List<String> deviceTypeCodes = command.deviceTypeCodes().stream().sorted().toList();
        require(!deviceTypeCodes.isEmpty() && new LinkedHashSet<>(deviceTypeCodes).size() == deviceTypeCodes.size()
                        && deviceTypeCodes.stream().allMatch(allowed::contains),
                INVALID_REQUEST, "请选择任务冻结配置中的有效设备类型");
        Map<String, List<String>> dimensions = new LinkedHashMap<>();
        dimensions.put("CUTOVER_TYPE", List.of(task.getCutoverType()));
        if (present(task.getNetworkMode())) {
            dimensions.put("NETWORK_MODE", List.of(task.getNetworkMode()));
        }
        dimensions.put("DEVICE_TYPE", deviceTypeCodes);
        dimensions.put("CUTOVER_LEVEL", List.of(task.getManualGrade()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", task.getId());
        snapshot.put("taskVersion", task.getVersion());
        snapshot.put("assessmentId", task.getCurrentAssessmentId());
        snapshot.put("assessmentVersion", command.expectedAssessmentVersion());
        snapshot.put("projectScopeVersion", task.getProjectScopeVersion());
        snapshot.put("configurationRevisionId", task.getConfigurationRevisionId());
        snapshot.put("configurationCode", task.getConfigurationCode());
        snapshot.put("configurationRevisionNo", task.getConfigurationRevisionNo());
        snapshot.put("dimensions", dimensions);
        snapshot.put("deviceTypes", options.stream().filter(option -> deviceTypeCodes.contains(option.value())).toList());
        CutoverChecklistMatcher.MatchResult matched = matcher.match(configuration,
                new CutoverChecklistMatcher.MatchInput(dimensions));
        List<ResolvedItem> resolved = matched.readyItems().stream()
                .map(item -> new ResolvedItem(item.item(), item.required(), item.matchedRuleIds()))
                .collect(Collectors.toCollection(ArrayList::new));
        Map<Long, CutoverFrozenConfiguration.ItemDefinition> itemsById = configuration.items().stream()
                .collect(Collectors.toMap(CutoverFrozenConfiguration.ItemDefinition::id, Function.identity()));
        for (CutoverChecklistMatcher.Conflict conflict : matched.conflicts()) {
            GenerateChecklistCommand.SelectedDefinition selected = command.selectedConflictDefinitions().get(conflict.stableItemKey());
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
        return new MatchContext(JsonUtils.toJsonString(snapshot), matched.gap(), List.copyOf(resolved),
                Map.copyOf(dimensions), matched.matchedRuleIds());
    }

    private static List<Option> dimensionOptions(CutoverFrozenConfiguration configuration, String dimensionCode) {
        var dimensions = JsonUtils.parseTree(configuration.dimensionDefinitionSnapshot());
        require(dimensions != null && dimensions.isArray(), FROZEN_CONFIGURATION_INVALID, "冻结配置缺少维度定义");
        String dictionary = null;
        for (var dimension : dimensions) {
            if (dimensionCode.equals(dimension.path("code").asText()) && dimension.path("enabled").asBoolean()) {
                String source = dimension.path("valueSource").asText();
                if (source.startsWith("DICT:") && source.length() > 5) dictionary = source.substring(5);
            }
        }
        if (dictionary == null && !"DEVICE_TYPE".equals(dimensionCode)) return List.of();
        require(dictionary != null, FROZEN_CONFIGURATION_INVALID, "冻结配置缺少匹配维度字典定义");
        var dictionaries = JsonUtils.parseTree(configuration.dictionarySnapshot());
        var values = dictionaries == null ? null : dictionaries.get(dictionary);
        require(values != null && values.isArray() && !values.isEmpty(),
                FROZEN_CONFIGURATION_INVALID, "冻结配置缺少匹配维度选项");
        List<Option> result = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        for (var value : values) {
            String code = value.path("value").asText();
            String label = value.path("label").asText();
            require(present(code) && code.equals(code.trim()) && code.length() <= 64
                            && present(label) && codes.add(code),
                    FROZEN_CONFIGURATION_INVALID, "冻结配置匹配维度选项不完整");
            result.add(new Option(code, label));
        }
        return List.copyOf(result);
    }

    private void insertSystemItem(CutoverTaskMutation command, CutoverChecklistDO checklist,
                                  ResolvedItem resolved, CutoverFrozenConfiguration configuration) {
        Long tenantId = command.tenantId();
        Long actorId = command.actorId();
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

    private int appendResult(ResultWrite write) {
        CutoverChecklistItemResultDO row = write.result();
        row.setFieldDefinitionId(CutoverChecklistResultStore.root(fieldDefinitions.forItem(write.item())).getId());
        Long tenantId = row.getTenantId();
        LocalDateTime now = LocalDateTime.now(clock);
        CutoverChecklistCurrentResultQuery query = new CutoverChecklistCurrentResultQuery(tenantId, row.getChecklistItemId());
        CutoverChecklistItemResultDO current = resultMapper.selectCurrentForUpdate(query);
        if (current != null) {
            require(resultMapper.closeCurrentIfMatch(new CutoverChecklistResultCloseUpdate(tenantId,
                    current.getId(), now)) == 1, VERSION_CONFLICT, "当前清单结果已变化");
        }
        int nextVersion = resultMapper.selectMaxVersion(query) + 1;
        row.setId(nextId());
        row.setResultVersion(nextVersion);
        row.setSelectionStartedAt(now);
        row.setCreatedAt(now);
        row.setDeleted(false);
        require(resultMapper.insert(row) == 1, STATE_CONFLICT, "清单结果保存失败");
        if (write.answer() != null) resultStorage.insertAnswer(new CutoverChecklistResultStore.Write(write.item(), row, write.answer()));
        if (write.file() != null) customResultStorage.insertManual(new CutoverCustomResultStore.Manual(row,
                CutoverChecklistResultStore.root(fieldDefinitions.forItem(write.item())).getId(), write.file()));
        if ("EXTERNAL".equals(write.item().getWorkModeCode()) && "MANUAL".equals(row.getResultSourceCode())) {
            externalBulletins.referenceManual(new CutoverExternalBulletinStore.ManualWrite(
                    new CutoverExternalBulletinStore.Read(write.item(), current), row));
        }
        return nextVersion;
    }

    private int appendCollectionResult(CutoverCollectionPort.Inspection selection,
                                       CutoverCollectionPort.CollectionFact fact) {
        Long tenantId = selection.tenantId();
        Long actorId = selection.actorId();
        Long deviceId = selection.deviceId();
        Long commandTemplateId = selection.commandTemplateId();
        LocalDateTime now = LocalDateTime.now(clock);
        CutoverChecklistCurrentResultQuery query = new CutoverChecklistCurrentResultQuery(tenantId, selection.checklistItemId());
        CutoverChecklistItemResultDO current = resultMapper.selectCurrentForUpdate(query);
        if (current != null) {
            require(resultMapper.closeCurrentIfMatch(new CutoverChecklistResultCloseUpdate(tenantId,
                    current.getId(), now)) == 1, VERSION_CONFLICT, "当前清单结果已变化");
        }
        int nextVersion = resultMapper.selectMaxVersion(query) + 1;
        CutoverChecklistItemResultDO row = new CutoverChecklistItemResultDO();
        row.setId(nextId());
        row.setTenantId(tenantId);
        row.setChecklistItemId(selection.checklistItemId());
        row.setFieldDefinitionId(CutoverChecklistResultStore.root(fieldDefinitions.forItem(itemMapper.selectById(selection.checklistItemId()))).getId());
        row.setResultVersion(nextVersion);
        row.setResultSourceCode("COLLECTION");
        row.setDeviceId(deviceId);
        row.setCommandTemplateId(commandTemplateId);
        row.setCollectionStatusCode(fact.technicalStatus().name());
        row.setCollectionFailureCode(fact.failureCode());
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
        if ("EXTERNAL".equals(result.getResultSourceCode())) {
            return "EXTERNAL_FEEDBACK".equals(result.getSelectionReasonCode()) && !present(result.getLoadFailureCode());
        }
        return !"COLLECTION".equals(result.getResultSourceCode())
                || (positive(result.getCollectionResultReferenceId())
                && positive(result.getCollectionResultVersion()) && !present(result.getLoadFailureCode())
                && result.getBaseChecklistResultId() != null);
    }

    private void appendCollectionAnalysis(SaveChecklistCommand command, CutoverChecklistItemDO item,
                                          CutoverChecklistItemResultDO prior, SaveChecklistCommand.DirectAnswer answer) {
        Long tenant = command.tenantId();
        Long actor = command.actorId();
        String analysis = answer.answerSnapshot();
        LocalDateTime now = LocalDateTime.now(clock);
        require(resultMapper.closeCurrentIfMatch(new CutoverChecklistResultCloseUpdate(tenant, prior.getId(), now)) == 1,
                VERSION_CONFLICT, "当前采集结果已变化");
        var row = new CutoverChecklistItemResultDO();
        row.setId(nextId()); row.setTenantId(tenant); row.setChecklistItemId(item.getId());
        row.setResultVersion(resultMapper.selectMaxVersion(new CutoverChecklistCurrentResultQuery(tenant, item.getId())) + 1);
        row.setResultSourceCode("COLLECTION"); row.setBaseChecklistResultId(prior.getId());
        row.setFieldDefinitionId(prior.getFieldDefinitionId());
        row.setDeviceId(prior.getDeviceId()); row.setCommandTemplateId(prior.getCommandTemplateId());
        row.setCollectionLogId(prior.getCollectionLogId()); row.setCollectionStatusCode(prior.getCollectionStatusCode());
        row.setCollectionFailureCode(prior.getCollectionFailureCode());
        row.setCollectionTaskId(prior.getCollectionTaskId()); row.setCollectionResultReferenceId(prior.getCollectionResultReferenceId());
        row.setCollectionResultVersion(prior.getCollectionResultVersion());
        row.setSelectionStartedAt(now); row.setSelectedBy(actor); row.setSelectionReasonCode("COLLECTION_ANALYSIS_SAVED");
        row.setCreatedBy(actor); row.setCreatedAt(now); row.setDeleted(false);
        require(resultMapper.insert(row) == 1, STATE_CONFLICT, "采集核查结论保存失败");
        resultStorage.insertAnswer(new CutoverChecklistResultStore.Write(item, row, analysis));
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
                                                                  RequestCollectionCommand command) {
        Long tenantId = command.tenantId();
        String correlationId = command.correlationId();
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
                ) {
            return null;
        }
        try {
            CollectionSelectionSnapshot snapshot = new CollectionSelectionSnapshot(current.getCollectionTaskId(),
                    current.getDeviceId(), current.getCommandTemplateId(), current.getCollectionStatusCode(),
                    current.getCollectionResultReferenceId(), current.getCollectionResultVersion(), null, current.getCollectionFailureCode());
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
                                          Long version, String status, boolean replayed) {
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

    private record ResultWrite(CutoverChecklistItemDO item, CutoverChecklistItemResultDO result,
                               String answer, CutoverChecklistFilePort.FileFact file) { }

    private List<CutoverChecklistItemDO> withSchemas(List<CutoverChecklistItemDO> rows) {
        return rows.stream().map(fieldDefinitions::withSchema).toList();
    }

    private String manualReference(CutoverChecklistItemResultDO result) {
        if (!"MANUAL".equals(result.getResultSourceCode())) return null;
        var file = customResultStorage.manualFile(result);
        return file == null ? null : file.referenceKey();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static boolean nonNegative(Long value) {
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

    private record MatchContext(String inputSnapshot, boolean gap, List<ResolvedItem> items,
                                Map<String, List<String>> dimensions, List<Long> matchedRuleIds) {
    }

    private record CollectionSelectionSnapshot(Long collectionTaskId, Long deviceId, Long commandTemplateId,
                                               String technicalStatus, Long resultReferenceId,
                                               Long resultVersion, Object resultSnapshot, String failureCode) {
    }
}
