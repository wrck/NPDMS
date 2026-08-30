package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverActiveDeviceQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskAssessmentLinkUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskTransitionUpdate;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.*;

/** F-CUT-002自建任务与P2人工评估应用内核；生产装配由Task 2接通正式Owner后完成。 */
public class CutoverTaskApplicationService {

    private static final String ACTION_EDIT = "ACTION_EDIT";
    private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake();

    private final CutoverTaskMapper taskMapper;
    private final CutoverTaskDeviceScopeMapper deviceMapper;
    private final CutoverTaskStageHistoryMapper historyMapper;
    private final CutoverAssessmentMapper assessmentMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverProjectContextPort projectContextPort;
    private final CutoverDeviceScopePort deviceScopePort;
    private final CutoverCustomerLevelPort customerLevelPort;
    private final CutoverReadinessPort readinessPort;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final Clock clock;

    public CutoverTaskApplicationService(CutoverTaskMapper taskMapper,
                                         CutoverTaskDeviceScopeMapper deviceMapper,
                                         CutoverTaskStageHistoryMapper historyMapper,
                                         CutoverAssessmentMapper assessmentMapper,
                                         CutoverProjectScopePort projectScopePort,
                                         CutoverProjectContextPort projectContextPort,
                                         CutoverDeviceScopePort deviceScopePort,
                                         CutoverCustomerLevelPort customerLevelPort,
                                         CutoverReadinessPort readinessPort,
                                         PlatformCommandExecutionApi commandExecutionApi,
                                         Clock clock) {
        this.taskMapper = taskMapper;
        this.deviceMapper = deviceMapper;
        this.historyMapper = historyMapper;
        this.assessmentMapper = assessmentMapper;
        this.projectScopePort = projectScopePort;
        this.projectContextPort = projectContextPort;
        this.deviceScopePort = deviceScopePort;
        this.customerLevelPort = customerLevelPort;
        this.readinessPort = readinessPort;
        this.commandExecutionApi = commandExecutionApi;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverTaskCommandResult create(CreateCutoverTaskCommand command) {
        requireCreate(command);
        ResolvedContext expected = inspectContext(command.tenantId(), command.actorId(), command.projectId(),
                command.serialNumbers());
        requireReady(expected.readiness());
        PlatformCommandExecutionApi.ExecutionResult<CutoverTaskCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:TASK_CREATE:" + command.projectId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(createDigestValue(command))), CutoverTaskCommandResult.class,
                () -> createOnce(command, expected),
                result -> new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_TASK_CREATE", "CutoverTask",
                        String.valueOf(result.taskId()), command.correlationId(), JsonUtils.toJsonString(result), List.of()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverAssessmentCommandResult saveAssessment(SaveCutoverAssessmentCommand command) {
        requireSave(command);
        CutoverTaskDO snapshot = requireTask(taskMapper.selectById(command.taskId()), command.tenantId());
        requireOwnedP2(snapshot, command.actorId(), command.expectedTaskVersion());
        ResolvedContext context = inspectStoredContext(snapshot);
        CutoverTaskDO lockedTask = requireTask(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId());
        requireOwnedP2(lockedTask, command.actorId(), command.expectedTaskVersion());
        String answers = JsonUtils.toJsonString(command.answers());
        String contextJson = JsonUtils.toJsonString(contextSnapshot(context));
        if (lockedTask.getCurrentAssessmentId() == null) {
            require(command.expectedAssessmentVersion() == 0, VERSION_CONFLICT, "评估版本已变化");
            CutoverAssessmentDO row = new CutoverAssessmentDO();
            row.setId(nextId());
            row.setTenantId(command.tenantId());
            row.setCutoverTaskId(command.taskId());
            row.setAssessmentVersion(1);
            row.setAssessmentStatus(CutoverTaskRules.ASSESSMENT_DRAFT);
            row.setQuestionnaireTemplateCode(CutoverTaskRules.TEMPLATE_CODE);
            row.setQuestionnaireTemplateVersion(CutoverTaskRules.TEMPLATE_VERSION);
            row.setAnswerSnapshot(answers);
            row.setContextSnapshot(contextJson);
            row.setManualGrade(normalizeOptionalGrade(command.manualGrade()));
            row.setSimpleFlow(false);
            row.setCurrentMarker(1);
            row.setVersion(0);
            row.setCreator(String.valueOf(command.actorId()));
            row.setUpdater(String.valueOf(command.actorId()));
            require(assessmentMapper.insert(row) == 1, STATE_CONFLICT, "评估草稿创建失败");
            require(taskMapper.linkAssessmentIfMatch(new CutoverTaskAssessmentLinkUpdate(command.tenantId(),
                    command.taskId(), command.expectedTaskVersion(), row.getId())) == 1,
                    VERSION_CONFLICT, "任务版本已变化");
            return new CutoverAssessmentCommandResult(command.taskId(), row.getId(), 1, 0,
                    command.expectedTaskVersion() + 1, CutoverTaskRules.ASSESSMENT_DRAFT);
        }
        CutoverAssessmentDO row = assessmentMapper.selectForUpdate(new CutoverAssessmentRowQuery(
                command.tenantId(), command.taskId(), lockedTask.getCurrentAssessmentId()));
        require(row != null && CutoverTaskRules.ASSESSMENT_DRAFT.equals(row.getAssessmentStatus()),
                STATE_CONFLICT, "当前评估不可编辑");
        require(assessmentMapper.updateDraftIfMatch(new CutoverAssessmentDraftUpdate(command.tenantId(), row.getId(),
                command.expectedAssessmentVersion(), answers, contextJson,
                normalizeOptionalGrade(command.manualGrade()))) == 1, VERSION_CONFLICT, "评估版本已变化");
        return new CutoverAssessmentCommandResult(command.taskId(), row.getId(), row.getAssessmentVersion(),
                command.expectedAssessmentVersion() + 1, lockedTask.getVersion(), CutoverTaskRules.ASSESSMENT_DRAFT);
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverTaskCommandResult submitAssessment(SubmitCutoverAssessmentCommand command) {
        requireSubmit(command);
        PlatformCommandExecutionApi.ExecutionResult<CutoverTaskCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:ASSESSMENT_SUBMIT:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(Map.of("taskId", command.taskId(),
                        "taskVersion", command.expectedTaskVersion(),
                        "assessmentVersion", command.expectedAssessmentVersion()))),
                CutoverTaskCommandResult.class, () -> submitOnce(command),
                result -> new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_ASSESSMENT_SUBMIT", "CutoverTask",
                        String.valueOf(result.taskId()), command.correlationId(), JsonUtils.toJsonString(result), List.of()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    private CutoverTaskCommandResult createOnce(CreateCutoverTaskCommand command, ResolvedContext expected) {
        ResolvedContext locked = lockContext(command.actorId(), expected);
        requireReady(locked.readiness());
        List<Long> deviceIds = locked.devices().stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).toList();
        require(deviceMapper.selectActiveForUpdate(new CutoverActiveDeviceQuery(command.tenantId(),
                command.projectId(), deviceIds)).isEmpty(), ACTIVE_DEVICE_CONFLICT, "设备已有活动割接任务");

        Long taskId = nextId();
        CutoverTaskDO row = new CutoverTaskDO();
        row.setId(taskId);
        row.setTenantId(command.tenantId());
        row.setProjectId(command.projectId());
        row.setTaskNo("CUT-" + taskId);
        row.setTaskName(command.taskName());
        row.setBackground(command.background());
        row.setCutoverType(command.cutoverType());
        row.setNetworkMode(command.networkMode());
        row.setScheduledTime(command.scheduledTime());
        row.setTaskOrigin(CutoverTaskRules.ORIGIN_NEW_PLATFORM);
        row.setIntakeSourceType(command.intakeSourceType());
        row.setSourceSystem(command.sourceSystem());
        row.setSourceBusinessNo(command.sourceBusinessNo());
        row.setBusinessEventId(command.businessEventId());
        row.setCurrentStage(CutoverTaskRules.STAGE_P2);
        row.setTaskStatus(CutoverTaskRules.STATUS_GRADE_CONFIRMING);
        row.setOwnerUserId(command.actorId());
        row.setCustomerId(locked.project().customerId());
        row.setImplementationReadinessSnapshotId(locked.readiness().snapshotId());
        row.setImplementationReadinessSnapshotVersion(locked.readiness().snapshotVersion());
        row.setProjectScopeVersion(locked.project().projectScopeVersion());
        row.setProjectContextSnapshot(JsonUtils.toJsonString(locked.project()));
        row.setDeviceScopeWatermark(JsonUtils.toJsonString(locked.devices()));
        row.setCustomerContextSnapshot(JsonUtils.toJsonString(locked.customer()));
        row.setReadinessContextSnapshot(JsonUtils.toJsonString(locked.readiness()));
        row.setVersion(0);
        row.setCreator(String.valueOf(command.actorId()));
        row.setUpdater(String.valueOf(command.actorId()));
        require(taskMapper.insert(row) == 1, STATE_CONFLICT, "割接任务创建失败");
        for (CutoverDeviceScopePort.DeviceFact device : locked.devices()) {
            CutoverTaskDeviceScopeDO link = new CutoverTaskDeviceScopeDO();
            link.setId(nextId());
            link.setTenantId(command.tenantId());
            link.setCutoverTaskId(taskId);
            link.setProjectId(command.projectId());
            link.setDeviceId(device.deviceId());
            link.setSerialNumberSnapshot(device.serialNumber());
            link.setProjectAssignmentVersion(device.projectAssignmentVersion());
            link.setActiveMarker(1);
            link.setVersion(0);
            link.setCreator(String.valueOf(command.actorId()));
            link.setUpdater(String.valueOf(command.actorId()));
            require(deviceMapper.insert(link) == 1, STATE_CONFLICT, "任务设备范围创建失败");
        }
        insertHistory(command.tenantId(), taskId, 1, CutoverTaskRules.STAGE_P1, CutoverTaskRules.STAGE_P2,
                null, CutoverTaskRules.STATUS_GRADE_CONFIRMING, "P1_ACCEPTED", null,
                command.actorId(), command.correlationId());
        return new CutoverTaskCommandResult(taskId, row.getTaskNo(), row.getCurrentStage(), row.getTaskStatus(), 0, false);
    }

    private CutoverTaskCommandResult submitOnce(SubmitCutoverAssessmentCommand command) {
        CutoverTaskDO snapshot = requireTask(taskMapper.selectById(command.taskId()), command.tenantId());
        requireOwnedP2(snapshot, command.actorId(), command.expectedTaskVersion());
        List<CutoverTaskDeviceScopeDO> storedDevices = storedDevices(command.tenantId(), command.taskId());
        ResolvedContext locked = lockStoredContext(command.actorId(), snapshot, storedDevices);
        requireReady(locked.readiness());
        require("AVAILABLE".equals(locked.customer().status()), CUSTOMER_CONTEXT_INVALID,
                "客户服务等级尚未配置");
        List<Long> deviceIds = storedDevices.stream().map(CutoverTaskDeviceScopeDO::getDeviceId).toList();
        deviceMapper.selectActiveForUpdate(new CutoverActiveDeviceQuery(command.tenantId(), snapshot.getProjectId(), deviceIds));
        CutoverTaskDO task = requireTask(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId());
        requireOwnedP2(task, command.actorId(), command.expectedTaskVersion());
        CutoverAssessmentDO assessment = assessmentMapper.selectForUpdate(new CutoverAssessmentRowQuery(
                command.tenantId(), command.taskId(), task.getCurrentAssessmentId()));
        require(assessment != null && CutoverTaskRules.ASSESSMENT_DRAFT.equals(assessment.getAssessmentStatus()),
                STATE_CONFLICT, "当前评估不可提交");
        CutoverAssessmentAnswers answers = JsonUtils.parseObject(assessment.getAnswerSnapshot(), CutoverAssessmentAnswers.class);
        require(answers != null && answers.complete(), INVALID_REQUEST, "人工评估答案不完整");
        String grade = CutoverTaskRules.normalizeGrade(assessment.getManualGrade());
        CutoverTaskRules.SubmissionTarget target = CutoverTaskRules.submissionTarget(grade);
        LocalDateTime now = LocalDateTime.now(clock);
        require(assessmentMapper.submitIfMatch(new CutoverAssessmentSubmitUpdate(command.tenantId(), assessment.getId(),
                command.expectedAssessmentVersion(), JsonUtils.toJsonString(contextSnapshot(locked)), grade,
                target.simpleFlow(), command.actorId(), now)) == 1, VERSION_CONFLICT, "评估版本已变化");
        require(taskMapper.transitionIfMatch(new CutoverTaskTransitionUpdate(command.tenantId(), task.getId(),
                command.expectedTaskVersion(), assessment.getId(), grade, target.stage(), target.status())) == 1,
                VERSION_CONFLICT, "任务版本已变化");
        insertHistory(command.tenantId(), task.getId(), 2, CutoverTaskRules.STAGE_P2, target.stage(),
                CutoverTaskRules.STATUS_GRADE_CONFIRMING, target.status(), "P2_ASSESSMENT_SUBMITTED",
                assessment.getId(), command.actorId(), command.correlationId());
        return new CutoverTaskCommandResult(task.getId(), task.getTaskNo(), target.stage(), target.status(),
                task.getVersion() + 1, false);
    }

    private ResolvedContext inspectContext(Long tenantId, Long actorId, Long projectId,
                                           List<String> serialNumbers) {
        List<String> normalizedSerials = normalizeSerials(serialNumbers);
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, projectId, ACTION_EDIT);
        require(scope != null && scope.allowed() && projectId.equals(scope.projectId()), DATA_SCOPE_FORBIDDEN,
                "无项目编辑范围");
        CutoverProjectContextPort.ProjectContextFact project = projectContextPort.inspect(tenantId, projectId,
                scope.projectScopeVersion());
        requireProjectContext(tenantId, projectId, scope.projectScopeVersion(), project);
        List<CutoverDeviceScopePort.DeviceFact> devices = orderedDevices(deviceScopePort.resolveBySerials(normalizedSerials));
        requireDevices(projectId, normalizedSerials, devices);
        CutoverCustomerLevelPort.CustomerLevelFact customer = customerLevelPort.inspect(project.customerId());
        CutoverReadinessPort.ReadinessFact readiness = readinessPort.inspect(projectId,
                devices.stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).toList());
        return new ResolvedContext(project, devices, customer, readiness);
    }

    private ResolvedContext inspectStoredContext(CutoverTaskDO task) {
        List<CutoverTaskDeviceScopeDO> rows = storedDevices(task.getTenantId(), task.getId());
        return inspectContext(task.getTenantId(), task.getOwnerUserId(), task.getProjectId(),
                rows.stream().map(CutoverTaskDeviceScopeDO::getSerialNumberSnapshot).toList());
    }

    private ResolvedContext lockContext(Long actorId, ResolvedContext expected) {
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.lockAndRevalidate(actorId,
                expected.project().projectId(), ACTION_EDIT, expected.project().projectScopeVersion());
        require(scope != null && scope.allowed(), DATA_SCOPE_FORBIDDEN, "项目范围已失效");
        CutoverProjectContextPort.ProjectContextFact project = projectContextPort.lockAndRevalidate(expected.project());
        requireProjectContext(expected.project().tenantId(), expected.project().projectId(),
                expected.project().projectScopeVersion(), project);
        require(expected.project().equals(project), VERSION_CONFLICT, "项目上下文事实已变化");
        List<CutoverDeviceScopePort.DeviceFact> devices = orderedDevices(deviceScopePort.lockAndRevalidate(
                expected.project().projectId(), expected.devices()));
        requireEquivalentDevices(expected.devices(), devices);
        CutoverCustomerLevelPort.CustomerLevelFact customer = customerLevelPort.lockAndRevalidate(expected.customer());
        CutoverReadinessPort.ReadinessFact readiness = readinessPort.lockAndRevalidate(expected.readiness());
        return new ResolvedContext(project, devices, customer, readiness);
    }

    private ResolvedContext lockStoredContext(Long actorId, CutoverTaskDO task,
                                              List<CutoverTaskDeviceScopeDO> storedDevices) {
        CutoverProjectContextPort.ProjectContextFact project = JsonUtils.parseObject(task.getProjectContextSnapshot(),
                CutoverProjectContextPort.ProjectContextFact.class);
        List<CutoverDeviceScopePort.DeviceFact> devices = storedDevices.stream()
                .map(row -> new CutoverDeviceScopePort.DeviceFact(row.getDeviceId(), row.getSerialNumberSnapshot(),
                        row.getProjectId(), row.getProjectAssignmentVersion())).toList();
        CutoverCustomerLevelPort.CustomerLevelFact customer = JsonUtils.parseObject(task.getCustomerContextSnapshot(),
                CutoverCustomerLevelPort.CustomerLevelFact.class);
        CutoverReadinessPort.ReadinessFact readiness = JsonUtils.parseObject(task.getReadinessContextSnapshot(),
                CutoverReadinessPort.ReadinessFact.class);
        return lockContext(actorId, new ResolvedContext(project, devices, customer, readiness));
    }

    private List<CutoverTaskDeviceScopeDO> storedDevices(Long tenantId, Long taskId) {
        return deviceMapper.selectActiveByTask(new CutoverTaskDeviceListQuery(tenantId, taskId));
    }

    private void insertHistory(Long tenantId, Long taskId, int sequence, String fromStage, String toStage,
                               String fromStatus, String toStatus, String triggerType, Long referenceId,
                               Long actorId, String correlationId) {
        CutoverTaskStageHistoryDO history = new CutoverTaskStageHistoryDO();
        history.setId(nextId());
        history.setTenantId(tenantId);
        history.setCutoverTaskId(taskId);
        history.setSequenceNo(sequence);
        history.setFromStage(fromStage);
        history.setToStage(toStage);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setTriggerType(triggerType);
        history.setTriggerReferenceId(referenceId);
        history.setActorId(actorId);
        history.setCorrelationId(correlationId);
        history.setOccurredAt(LocalDateTime.now(clock));
        history.setCreator(String.valueOf(actorId));
        history.setCreateTime(LocalDateTime.now(clock));
        require(historyMapper.insert(history) == 1, STATE_CONFLICT, "阶段历史创建失败");
    }

    private static void requireCreate(CreateCutoverTaskCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.projectId()), INVALID_REQUEST, "创建命令身份非法");
        requireText(command.idempotencyKey(), "Idempotency-Key", 128);
        requireText(command.correlationId(), "correlationId", 128);
        requireText(command.taskName(), "taskName", 128);
        requireText(command.background(), "background", 4000);
        requireText(command.cutoverType(), "cutoverType", 32);
        require(Set.of("SELF_CREATED", "ITR", "PROJECT_EVENT").contains(command.intakeSourceType()),
                INVALID_REQUEST, "intakeSourceType非法");
        boolean validUnion = switch (command.intakeSourceType()) {
            case "SELF_CREATED" -> command.sourceSystem() == null && command.sourceBusinessNo() == null
                    && command.businessEventId() == null;
            case "ITR" -> present(command.sourceSystem()) && present(command.sourceBusinessNo())
                    && command.businessEventId() == null;
            case "PROJECT_EVENT" -> command.sourceSystem() == null && command.sourceBusinessNo() == null
                    && present(command.businessEventId());
            default -> false;
        };
        require(validUnion, INVALID_REQUEST, "来源身份组合非法");
    }

    private static void requireSave(SaveCutoverAssessmentCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && command.expectedTaskVersion() != null
                        && command.expectedTaskVersion() >= 0 && command.expectedAssessmentVersion() != null
                        && command.expectedAssessmentVersion() >= 0 && command.answers() != null,
                INVALID_REQUEST, "评估暂存命令非法");
        requireText(command.correlationId(), "correlationId", 128);
    }

    private static void requireSubmit(SubmitCutoverAssessmentCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.taskId()) && command.expectedTaskVersion() != null
                        && command.expectedTaskVersion() >= 0 && command.expectedAssessmentVersion() != null
                        && command.expectedAssessmentVersion() >= 0,
                INVALID_REQUEST, "评估提交命令非法");
        requireText(command.idempotencyKey(), "Idempotency-Key", 128);
        requireText(command.correlationId(), "correlationId", 128);
    }

    private static void requireOwnedP2(CutoverTaskDO task, Long actorId, Integer expectedVersion) {
        require(CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                        && actorId.equals(task.getOwnerUserId()), NOT_FOUND, "割接任务不可见");
        require(CutoverTaskRules.STAGE_P2.equals(task.getCurrentStage())
                        && CutoverTaskRules.STATUS_GRADE_CONFIRMING.equals(task.getTaskStatus()),
                STATE_CONFLICT, "割接任务不在P2人工分级阶段");
        require(Objects.equals(task.getVersion(), expectedVersion), VERSION_CONFLICT, "任务版本已变化");
    }

    private static CutoverTaskDO requireTask(CutoverTaskDO task, Long tenantId) {
        require(task != null && tenantId.equals(task.getTenantId()) && !Boolean.TRUE.equals(task.getDeleted()),
                NOT_FOUND, "割接任务不存在");
        return task;
    }

    private static void requireReady(CutoverReadinessPort.ReadinessFact readiness) {
        require(readiness != null && "READY".equals(readiness.decision())
                        && readiness.unmetCodes() != null && readiness.unmetCodes().isEmpty(),
                READINESS_NOT_READY, "实施准备未就绪");
    }

    private static void requireDevices(Long projectId, List<String> requested,
                                       List<CutoverDeviceScopePort.DeviceFact> devices) {
        require(!devices.isEmpty() && devices.size() == requested.size(), INVALID_REQUEST, "设备事实不完整");
        Set<String> expectedKeys = requested.stream().map(CutoverTaskApplicationService::serialKey).collect(Collectors.toSet());
        Set<String> actualKeys = devices.stream().map(CutoverDeviceScopePort.DeviceFact::serialNumber)
                .map(CutoverTaskApplicationService::serialKey).collect(Collectors.toSet());
        require(expectedKeys.equals(actualKeys) && devices.stream().allMatch(d -> projectId.equals(d.projectId())),
                DATA_SCOPE_FORBIDDEN, "设备不属于所选项目");
    }

    private static void requireProjectContext(Long tenantId, Long projectId, long projectScopeVersion,
                                              CutoverProjectContextPort.ProjectContextFact fact) {
        require(fact != null && tenantId.equals(fact.tenantId()) && projectId.equals(fact.projectId())
                        && fact.projectVersion() >= 0 && projectScopeVersion == fact.projectScopeVersion()
                        && positive(fact.customerId()) && positive(fact.departmentId())
                        && normalizedText(fact.projectCode(), 64) && normalizedText(fact.customerCode(), 64)
                        && normalizedText(fact.departmentCode(), 64) && normalizedText(fact.projectName(), 255)
                        && normalizedText(fact.customerName(), 255) && normalizedText(fact.departmentName(), 255),
                OWNER_PROVIDER_UNAVAILABLE, "项目上下文事实不完整");
    }

    private static void requireEquivalentDevices(List<CutoverDeviceScopePort.DeviceFact> expected,
                                                 List<CutoverDeviceScopePort.DeviceFact> actual) {
        Map<Long, String> expectedIdentity = expected.stream().collect(Collectors.toMap(
                CutoverDeviceScopePort.DeviceFact::deviceId,
                d -> serialKey(d.serialNumber()) + ":" + d.projectId() + ":" + d.projectAssignmentVersion()));
        Map<Long, String> actualIdentity = actual.stream().collect(Collectors.toMap(
                CutoverDeviceScopePort.DeviceFact::deviceId,
                d -> serialKey(d.serialNumber()) + ":" + d.projectId() + ":" + d.projectAssignmentVersion()));
        require(expectedIdentity.equals(actualIdentity), VERSION_CONFLICT, "设备归属事实已变化");
    }

    private static List<String> normalizeSerials(List<String> values) {
        require(values != null && !values.isEmpty() && values.size() <= 500, INVALID_REQUEST, "SN集合非法");
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String value : values) {
            requireText(value, "serialNumber", 128);
            String normalized = value.trim();
            require(ordered.putIfAbsent(serialKey(normalized), normalized) == null, INVALID_REQUEST, "SN重复");
        }
        return List.copyOf(ordered.values());
    }

    private static List<CutoverDeviceScopePort.DeviceFact> orderedDevices(
            List<CutoverDeviceScopePort.DeviceFact> devices) {
        require(devices != null, OWNER_PROVIDER_UNAVAILABLE, "设备事实不可用");
        return devices.stream().sorted(Comparator.comparing(CutoverDeviceScopePort.DeviceFact::deviceId)).toList();
    }

    private static Map<String, Object> createDigestValue(CreateCutoverTaskCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("intakeSourceType", command.intakeSourceType());
        value.put("projectId", command.projectId());
        value.put("serialNumbers", normalizeSerials(command.serialNumbers()));
        value.put("taskName", command.taskName());
        value.put("background", command.background());
        value.put("cutoverType", command.cutoverType());
        value.put("networkMode", command.networkMode());
        value.put("scheduledTime", command.scheduledTime());
        value.put("sourceSystem", command.sourceSystem());
        value.put("sourceBusinessNo", command.sourceBusinessNo());
        value.put("businessEventId", command.businessEventId());
        return value;
    }

    private static Object contextSnapshot(ResolvedContext context) {
        return Map.of("project", context.project(), "devices", context.devices(),
                "implementationReadiness", context.readiness(), "customerServiceLevel", context.customer());
    }

    private static String normalizeOptionalGrade(String grade) {
        return grade == null ? null : CutoverTaskRules.normalizeGrade(grade);
    }

    private static String serialKey(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean normalizedText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= maxLength;
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= maxLength,
                INVALID_REQUEST, field + "格式非法");
    }

    private static void require(boolean condition, CutoverTaskApplicationException.Code code, String message) {
        if (!condition) {
            throw new CutoverTaskApplicationException(code, message);
        }
    }

    private static void requireCompleted(PlatformCommandExecutionApi.Decision decision) {
        if (decision == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new CutoverTaskApplicationException(IDEMPOTENCY_CONFLICT, "幂等键载荷冲突");
        }
        if (decision == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw new CutoverTaskApplicationException(IDEMPOTENCY_IN_PROGRESS, "相同命令正在处理中");
        }
    }

    private static long nextId() {
        return ID_GENERATOR.nextId();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private record ResolvedContext(CutoverProjectContextPort.ProjectContextFact project,
                                   List<CutoverDeviceScopePort.DeviceFact> devices,
                                   CutoverCustomerLevelPort.CustomerLevelFact customer,
                                   CutoverReadinessPort.ReadinessFact readiness) {
    }
}
