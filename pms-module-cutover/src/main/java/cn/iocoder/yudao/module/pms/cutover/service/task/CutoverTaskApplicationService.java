package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverEffectivePublishedConfigurationQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverActiveDeviceQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskAssessmentLinkUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskTransitionUpdate;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceProductTypePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.*;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverContextSupport.*;

/** 从原P1/P2内核复制，按正式基线修订014区分独立割接与项目事件入口。 */
public class CutoverTaskApplicationService {

    @jakarta.annotation.Resource
    private CutoverTaskSnapshotStore taskSnapshots;

    private static final String ACTION_EDIT = "ACTION_EDIT";
    private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake();

    private final CutoverTaskMapper taskMapper;
    private final CutoverTaskDeviceScopeMapper deviceMapper;
    private final CutoverTaskStageHistoryMapper historyMapper;
    private final CutoverAssessmentMapper assessmentMapper;
    private final CutoverConfigurationRevisionMapper configurationMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverProjectContextPort projectContextPort;
    private final CutoverDeviceScopePort deviceScopePort;
    private final CutoverCustomerLevelPort customerLevelPort;
    private final CutoverReadinessPort readinessPort;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final Clock clock;
    private final cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy intakeConfiguration;

    public CutoverTaskApplicationService(CutoverTaskMapper taskMapper,
                                         CutoverTaskDeviceScopeMapper deviceMapper,
                                         CutoverTaskStageHistoryMapper historyMapper,
                                         CutoverAssessmentMapper assessmentMapper,
                                         CutoverConfigurationRevisionMapper configurationMapper,
                                         CutoverProjectScopePort projectScopePort,
                                         CutoverProjectContextPort projectContextPort,
                                         CutoverDeviceScopePort deviceScopePort,
                                         CutoverDeviceProductTypePort deviceProductTypePort,
                                         CutoverCustomerLevelPort customerLevelPort,
                                         CutoverReadinessPort readinessPort,
                                         PlatformCommandExecutionApi commandExecutionApi,
                                         Clock clock, cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy intakeConfiguration) {
        this.taskMapper = taskMapper;
        this.deviceMapper = deviceMapper;
        this.historyMapper = historyMapper;
        this.assessmentMapper = assessmentMapper;
        this.configurationMapper = configurationMapper;
        this.projectScopePort = projectScopePort;
        this.projectContextPort = projectContextPort;
        this.deviceScopePort = deviceScopePort;
        this.customerLevelPort = customerLevelPort;
        this.readinessPort = readinessPort;
        this.commandExecutionApi = commandExecutionApi;
        this.clock = clock;
        this.intakeConfiguration = intakeConfiguration;
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverTaskCommandResult create(CreateCutoverTaskCommand command) {
        requireCreate(command);
        PlatformCommandExecutionApi.ExecutionResult<CutoverTaskCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:TASK_CREATE:" + command.projectId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(createDigestValue(command))), CutoverTaskCommandResult.class,
                () -> createOnce(command),
                result -> new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_TASK_CREATE", "CutoverTask",
                        String.valueOf(result.taskId()), command.correlationId(), JsonUtils.toJsonString(result), List.of()));
        requireCompleted(execution.decision());
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverAssessmentCommandResult saveAssessment(SaveCutoverAssessmentCommand command) {
        requireSave(command);
        CutoverTaskDO current = requireTask(taskMapper.selectById(command.taskId()), command.tenantId());
        require(Objects.equals(current.getOwnerUserId(), command.actorId()), NOT_FOUND, "割接任务不可见");
        var scope = projectScopePort.inspect(command.actorId(), current.getProjectId(), ACTION_EDIT);
        require(scope != null && scope.allowed() && Objects.equals(scope.projectId(), current.getProjectId()),
                DATA_SCOPE_FORBIDDEN, "无项目编辑范围");
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        command.tenantId(), "CUT:ASSESSMENT_SAVE:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(new AssessmentSaveInput(command.taskId(), command.expectedTaskVersion(),
                        command.expectedAssessmentVersion(), command.answers(), command.manualGrade()))),
                CutoverAssessmentCommandResult.class, () -> saveAssessmentOnce(command),
                result -> new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_ASSESSMENT_SAVE", "CutoverTask",
                        String.valueOf(result.taskId()), command.correlationId(), JsonUtils.toJsonString(result), List.of()));
        requireCompleted(execution.decision());
        return execution.response();
    }

    private record AssessmentSaveInput(Long taskId, Long taskVersion, Long assessmentVersion,
                                       CutoverAssessmentAnswers answers, String manualGrade) { }

    private CutoverAssessmentCommandResult saveAssessmentOnce(SaveCutoverAssessmentCommand command) {
        CutoverTaskDO snapshot = requireTask(taskMapper.selectById(command.taskId()), command.tenantId());
        requireOwnedP2(snapshot, command.actorId(), command.expectedTaskVersion());
        ResolvedContext context = draftContext(snapshot);
        CutoverTaskDO lockedTask = requireTask(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId());
        requireOwnedP2(lockedTask, command.actorId(), command.expectedTaskVersion());
        var contextFacts = snapshotContext(context, lockedTask.getIntakeSourceType());
        if (lockedTask.getCurrentAssessmentId() == null) {
            requireAssessmentVersion(command.expectedAssessmentVersion() == 0, 0L);
            CutoverAssessmentDO row = new CutoverAssessmentDO();
            row.setId(nextId());
            row.setTenantId(command.tenantId());
            row.setCutoverTaskId(command.taskId());
            row.setAssessmentVersion(1L);
            row.setAssessmentStatus(CutoverTaskRules.ASSESSMENT_DRAFT);
            row.setQuestionnaireTemplateCode(CutoverTaskRules.TEMPLATE_CODE);
            row.setQuestionnaireTemplateVersion(CutoverTaskRules.TEMPLATE_VERSION);
            CutoverTaskSnapshotStore.applyAssessment(new CutoverTaskSnapshotStore.AssessmentWrite(row, contextFacts, command.answers()));
            row.setManualGrade(normalizeOptionalGrade(command.manualGrade()));
            row.setSimpleFlow(false);
            row.setCurrentMarker(1);
            row.setVersion(0L);
            row.setCreator(String.valueOf(command.actorId()));
            row.setUpdater(String.valueOf(command.actorId()));
            require(assessmentMapper.insert(row) == 1, STATE_CONFLICT, "评估草稿创建失败");
            taskSnapshots.replaceAssessmentDevices(CutoverTaskSnapshotStore.DeviceWrite.builder().assessment(row)
                    .devices(storedDevices(command.tenantId(), command.taskId())).productTypes(context.productTypesByDevice())
                    .actorId(command.actorId()).time(LocalDateTime.now(clock)).build());
            requireTaskVersion(taskMapper.linkAssessmentIfMatch(new CutoverTaskAssessmentLinkUpdate(
                    command.tenantId(), command.taskId(), command.expectedTaskVersion(), row.getId())) == 1,
                    lockedTask.getVersion());
            return new CutoverAssessmentCommandResult(command.taskId(), row.getId(), 1L, 0L,
                    command.expectedTaskVersion() + 1, CutoverTaskRules.ASSESSMENT_DRAFT);
        }
        CutoverAssessmentDO row = assessmentMapper.selectForUpdate(new CutoverAssessmentRowQuery(
                command.tenantId(), command.taskId(), lockedTask.getCurrentAssessmentId()));
        require(row != null && CutoverTaskRules.ASSESSMENT_DRAFT.equals(row.getAssessmentStatus()),
                STATE_CONFLICT, "当前评估不可编辑");
        CutoverTaskSnapshotStore.applyAssessment(new CutoverTaskSnapshotStore.AssessmentWrite(row, contextFacts, command.answers()));
        row.setManualGrade(normalizeOptionalGrade(command.manualGrade()));
        row.setUpdater(String.valueOf(command.actorId()));
        requireAssessmentVersion(assessmentMapper.updateDraftIfMatch(new CutoverAssessmentDraftUpdate(
                row, command.expectedAssessmentVersion())) == 1, row.getVersion());
        taskSnapshots.replaceAssessmentDevices(CutoverTaskSnapshotStore.DeviceWrite.builder().assessment(row)
                .devices(storedDevices(command.tenantId(), command.taskId())).productTypes(context.productTypesByDevice())
                .actorId(command.actorId()).time(LocalDateTime.now(clock)).build());
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

    private CutoverTaskCommandResult createOnce(CreateCutoverTaskCommand command) {
        ResolvedContext expected = inspectContext(command);
        requireExpectedCreateContext(command, expected);
        if (requiresImplementationReadiness(command.intakeSourceType())) requireReady(expected.readiness());
        ResolvedContext locked = lockContext(command.actorId(), expected);
        if (requiresImplementationReadiness(command.intakeSourceType())) requireReady(locked.readiness());
        List<Long> deviceIds = locked.devices().stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).toList();
        require(deviceMapper.selectActiveForUpdate(new CutoverActiveDeviceQuery(command.tenantId(),
                command.projectId(), deviceIds)).isEmpty(), ACTIVE_DEVICE_CONFLICT, "设备已有活动割接任务");

        LocalDateTime taskCreatedAt = LocalDateTime.now(clock);
        CutoverConfigurationRevisionDO configuration = configurationMapper.selectEffectivePublished(
                new CutoverEffectivePublishedConfigurationQuery(command.tenantId(),
                        command.configurationCode(), taskCreatedAt));
        require(configuration != null, CONFIGURATION_CONFLICT, "所选割接配置在任务创建时点无有效发布修订");
        intakeConfiguration.validate(configuration, command.cutoverType(), command.networkMode());

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
        row.setConfigurationRevisionId(configuration.getId());
        row.setConfigurationCode(configuration.getConfigurationCode());
        row.setConfigurationRevisionNo(configuration.getRevisionNo());
        row.setCurrentStage(CutoverTaskRules.STAGE_P2);
        row.setTaskStatus(CutoverTaskRules.STATUS_GRADE_CONFIRMING);
        row.setOwnerUserId(command.actorId());
        row.setCustomerId(locked.project().customerId());
        row.setImplementationReadinessSnapshotId(locked.readiness().snapshotId());
        row.setImplementationReadinessSnapshotVersion(locked.readiness().snapshotId() == null
                ? null : locked.readiness().snapshotVersion());
        row.setProjectScopeVersion(locked.project().projectScopeVersion());
        CutoverTaskSnapshotStore.applyTask(new CutoverTaskSnapshotStore.TaskWrite(row, snapshotContext(locked, command.intakeSourceType())));
        row.setVersion(0L);
        row.setCreator(String.valueOf(command.actorId()));
        row.setUpdater(String.valueOf(command.actorId()));
        row.setCreateTime(taskCreatedAt);
        row.setUpdateTime(taskCreatedAt);
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
        insertHistory(CutoverTaskStageHistoryDO.builder()
                .tenantId(command.tenantId())
                .cutoverTaskId(taskId)
                .sequenceNo(1)
                .fromStage(CutoverTaskRules.STAGE_P1)
                .toStage(CutoverTaskRules.STAGE_P2)
                .fromStatus(null)
                .toStatus(CutoverTaskRules.STATUS_GRADE_CONFIRMING)
                .triggerType("P1_ACCEPTED")
                .triggerReferenceId(null)
                .actorId(command.actorId())
                .correlationId(command.correlationId())
                .build());
        return new CutoverTaskCommandResult(taskId, row.getTaskNo(), row.getCurrentStage(), row.getTaskStatus(), 0L, false);
    }

    private CutoverTaskCommandResult submitOnce(SubmitCutoverAssessmentCommand command) {
        CutoverTaskDO snapshot = requireTask(taskMapper.selectById(command.taskId()), command.tenantId());
        requireOwnedP2(snapshot, command.actorId(), command.expectedTaskVersion());
        List<CutoverTaskDeviceScopeDO> storedDevices = storedDevices(command.tenantId(), command.taskId());
        ResolvedContext locked = lockStoredContext(command.actorId(), snapshot, storedDevices);
        if (requiresImplementationReadiness(snapshot.getIntakeSourceType())) requireReady(locked.readiness());
        List<Long> deviceIds = storedDevices.stream().map(CutoverTaskDeviceScopeDO::getDeviceId).toList();
        deviceMapper.selectActiveForUpdate(new CutoverActiveDeviceQuery(command.tenantId(), snapshot.getProjectId(), deviceIds));
        CutoverTaskDO task = requireTask(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId());
        requireOwnedP2(task, command.actorId(), command.expectedTaskVersion());
        CutoverAssessmentDO assessment = assessmentMapper.selectForUpdate(new CutoverAssessmentRowQuery(
                command.tenantId(), command.taskId(), task.getCurrentAssessmentId()));
        require(assessment != null && CutoverTaskRules.ASSESSMENT_DRAFT.equals(assessment.getAssessmentStatus()),
                STATE_CONFLICT, "当前评估不可提交");
        CutoverAssessmentAnswers answers = CutoverTaskSnapshotStore.answers(assessment);
        require(answers != null && answers.complete(), INVALID_REQUEST, "人工评估答案不完整");
        String grade = CutoverTaskRules.normalizeGrade(assessment.getManualGrade());
        CutoverTaskRules.SubmissionTarget target = CutoverTaskRules.submissionTarget(grade);
        LocalDateTime now = LocalDateTime.now(clock);
        CutoverTaskSnapshotStore.applyAssessment(new CutoverTaskSnapshotStore.AssessmentWrite(assessment,
                snapshotContext(locked, task.getIntakeSourceType()), answers));
        assessment.setManualGrade(grade); assessment.setSimpleFlow(target.simpleFlow());
        assessment.setSubmittedBy(command.actorId()); assessment.setSubmittedAt(now); assessment.setUpdater(String.valueOf(command.actorId()));
        taskSnapshots.replaceAssessmentDevices(CutoverTaskSnapshotStore.DeviceWrite.builder().assessment(assessment)
                .devices(storedDevices).productTypes(locked.productTypesByDevice()).actorId(command.actorId()).time(now).build());
        requireAssessmentVersion(assessmentMapper.submitIfMatch(new CutoverAssessmentSubmitUpdate(
                assessment, command.expectedAssessmentVersion())) == 1,
                assessment.getVersion());
        requireTaskVersion(taskMapper.transitionIfMatch(new CutoverTaskTransitionUpdate(
                command.tenantId(), task.getId(), command.expectedTaskVersion(), assessment.getId(), grade,
                target.stage(), target.status())) == 1, task.getVersion());
        insertHistory(CutoverTaskStageHistoryDO.builder()
                .tenantId(command.tenantId())
                .cutoverTaskId(task.getId())
                .sequenceNo(2)
                .fromStage(CutoverTaskRules.STAGE_P2)
                .toStage(target.stage())
                .fromStatus(CutoverTaskRules.STATUS_GRADE_CONFIRMING)
                .toStatus(target.status())
                .triggerType("P2_ASSESSMENT_SUBMITTED")
                .triggerReferenceId(assessment.getId())
                .actorId(command.actorId())
                .correlationId(command.correlationId())
                .build());
        return new CutoverTaskCommandResult(task.getId(), task.getTaskNo(), target.stage(), target.status(),
                task.getVersion() + 1, false);
    }

    private ResolvedContext inspectContext(CreateCutoverTaskCommand command) {
        Long tenantId = command.tenantId(); Long actorId = command.actorId(); Long projectId = command.projectId();
        List<String> serialNumbers = command.serialNumbers();
        boolean requiresReadiness = requiresImplementationReadiness(command.intakeSourceType());
        List<String> normalizedSerials = normalizeSerials(serialNumbers);
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, projectId, ACTION_EDIT);
        require(scope != null, PROJ_PROVIDER_UNAVAILABLE, "项目范围事实不可用");
        require(scope.allowed() && projectId.equals(scope.projectId()), DATA_SCOPE_FORBIDDEN, "无项目编辑范围");
        var projectQuery = CutoverProjectContextPort.Inspection.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .expectedProjectScopeVersion(scope.projectScopeVersion())
                .build();
        CutoverProjectContextPort.ProjectContextFact project = projectContextPort.inspect(projectQuery);
        requireProjectContext(projectQuery, project);
        List<CutoverDeviceScopePort.DeviceFact> devices = orderedDevices(deviceScopePort.resolveBySerials(normalizedSerials))
                .stream().filter(device -> projectId.equals(device.projectId())).toList();
        requireDevices(projectId, normalizedSerials, devices);
        CutoverCustomerLevelPort.CustomerLevelFact customer = customerReference(customerLevelPort, project);
        List<Long> deviceIds = devices.stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).toList();
        CutoverReadinessPort.ReadinessFact readiness = requiresReadiness
                ? readinessPort.inspect(projectId, deviceIds) : independentReadiness(projectId, deviceIds);
        require(customer != null, CUS_PROVIDER_UNAVAILABLE, "客户服务等级事实不可用");
        require(readiness != null, IMP_PROVIDER_UNAVAILABLE, "实施就绪事实不可用");
        // CUT device types are selected from the frozen CUT configuration in P3.
        return new ResolvedContext(project, devices, Map.of(), customer, readiness);
    }

    private ResolvedContext draftContext(CutoverTaskDO task) {
        var scope = projectScopePort.inspect(task.getOwnerUserId(), task.getProjectId(), ACTION_EDIT);
        require(scope != null, PROJ_PROVIDER_UNAVAILABLE, "项目范围事实不可用");
        require(scope.allowed() && task.getProjectId().equals(scope.projectId()), DATA_SCOPE_FORBIDDEN, "无项目编辑范围");
        var rows = storedDevices(task.getTenantId(), task.getId());
        var devices = rows.stream().map(row -> new CutoverDeviceScopePort.DeviceFact(row.getDeviceId(),
                row.getSerialNumberSnapshot(), row.getProjectId(), row.getProjectAssignmentVersion())).toList();
        // 暂存保留受信冻结上下文；当前 Owner 事实在提交事务中重新锁定核对。
        return new ResolvedContext(CutoverTaskSnapshotStore.project(task),
                devices, frozenProductTypes(rows),
                CutoverTaskSnapshotStore.customer(task), taskSnapshots.readiness(task));
    }

    private ResolvedContext lockContext(Long actorId, ResolvedContext expected) {
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(actorId)
                .projectId(expected.project().projectId())
                .action(ACTION_EDIT)
                .expectedProjectScopeVersion(expected.project().projectScopeVersion())
                .build());
        require(scope != null, PROJ_PROVIDER_UNAVAILABLE, "项目范围事实不可用");
        require(scope.allowed(), PROJECT_SCOPE_STALE, "项目范围已失效");
        CutoverProjectContextPort.ProjectContextFact project = projectContextPort.lockAndRevalidate(expected.project());
        requireProjectContext(CutoverProjectContextPort.Inspection.builder()
                .tenantId(expected.project().tenantId())
                .projectId(expected.project().projectId())
                .expectedProjectScopeVersion(expected.project().projectScopeVersion()).build(), project);
        require(expected.project().equals(project), PROJECT_CONTEXT_STALE, "项目上下文事实已变化");
        List<CutoverDeviceScopePort.DeviceFact> devices = orderedDevices(deviceScopePort.lockAndRevalidate(
                expected.project().projectId(), expected.devices()));
        requireEquivalentDevices(expected.devices(), devices);
        CutoverCustomerLevelPort.CustomerLevelFact customer = expected.customer();
        CutoverReadinessPort.ReadinessFact readiness = "NOT_APPLICABLE".equals(expected.readiness().decision())
                ? expected.readiness() : readinessPort.lockAndRevalidate(expected.readiness());
        require(customer != null, CUS_PROVIDER_UNAVAILABLE, "客户服务等级事实不可用");
        require(readiness != null, IMP_PROVIDER_UNAVAILABLE, "实施就绪事实不可用");
        requireEquivalentReadiness(expected.readiness(), readiness);
        return new ResolvedContext(project, devices, expected.productTypesByDevice(), customer, readiness);
    }

    private ResolvedContext lockStoredContext(Long actorId, CutoverTaskDO task,
                                              List<CutoverTaskDeviceScopeDO> storedDevices) {
        CutoverProjectContextPort.ProjectContextFact project = CutoverTaskSnapshotStore.project(task);
        List<CutoverDeviceScopePort.DeviceFact> devices = storedDevices.stream()
                .map(row -> new CutoverDeviceScopePort.DeviceFact(row.getDeviceId(), row.getSerialNumberSnapshot(),
                        row.getProjectId(), row.getProjectAssignmentVersion())).toList();
        Map<Long, CutoverDeviceProductTypePort.ProductTypeFact> productTypes = frozenProductTypes(storedDevices);
        CutoverCustomerLevelPort.CustomerLevelFact customer = CutoverTaskSnapshotStore.customer(task);
        CutoverReadinessPort.ReadinessFact readiness = taskSnapshots.readiness(task);
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(actorId)
                .projectId(project.projectId())
                .action(ACTION_EDIT)
                .expectedProjectScopeVersion(project.projectScopeVersion())
                .build());
        require(scope != null, PROJ_PROVIDER_UNAVAILABLE, "项目范围事实不可用");
        require(scope.allowed(), PROJECT_SCOPE_STALE, "项目范围已失效");
        CutoverProjectContextPort.ProjectContextFact lockedProject = projectContextPort.lockAndRevalidate(project);
        requireProjectContext(CutoverProjectContextPort.Inspection.builder()
                .tenantId(project.tenantId())
                .projectId(project.projectId())
                .expectedProjectScopeVersion(project.projectScopeVersion())
                .build(), lockedProject);
        require(project.equals(lockedProject), PROJECT_CONTEXT_STALE, "项目上下文事实已变化");
        List<CutoverDeviceScopePort.DeviceFact> lockedDevices = orderedDevices(deviceScopePort.lockAndRevalidate(
                project.projectId(), devices));
        requireEquivalentDevices(devices, lockedDevices);
        CutoverCustomerLevelPort.CustomerLevelFact lockedCustomer = customer;
        CutoverReadinessPort.ReadinessFact lockedReadiness = requiresImplementationReadiness(task.getIntakeSourceType())
                ? readinessPort.lockAndRevalidate(readiness)
                : independentReadiness(task.getProjectId(), devices.stream()
                        .map(CutoverDeviceScopePort.DeviceFact::deviceId).toList());
        require(lockedCustomer != null, CUS_PROVIDER_UNAVAILABLE, "客户服务等级事实不可用");
        require(lockedReadiness != null, IMP_PROVIDER_UNAVAILABLE, "实施就绪事实不可用");
        if (requiresImplementationReadiness(task.getIntakeSourceType())) requireEquivalentReadiness(readiness, lockedReadiness);
        return new ResolvedContext(lockedProject, lockedDevices, productTypes, lockedCustomer, lockedReadiness);
    }

    private List<CutoverTaskDeviceScopeDO> storedDevices(Long tenantId, Long taskId) {
        return deviceMapper.selectActiveByTask(new CutoverTaskDeviceListQuery(tenantId, taskId));
    }

    private void insertHistory(CutoverTaskStageHistoryDO history) {
        history.setId(nextId());
        history.setOccurredAt(LocalDateTime.now(clock));
        history.setCreator(String.valueOf(history.getActorId()));
        history.setCreateTime(LocalDateTime.now(clock));
        require(historyMapper.insert(history) == 1, STATE_CONFLICT, "阶段历史创建失败");
    }

    private static void requireCreate(CreateCutoverTaskCommand command) {
        require(command != null && positive(command.tenantId()) && positive(command.actorId())
                        && positive(command.projectId()), INVALID_REQUEST, "创建命令身份非法");
        requireText(command.idempotencyKey(), "Idempotency-Key", 128);
        requireText(command.correlationId(), "correlationId", 128);
        requireText(command.taskName(), "taskName", 128);
        requireText(command.configurationCode(), "configurationCode", 64);
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
        requireText(command.idempotencyKey(), "Idempotency-Key", 128);
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

    private static void requireOwnedP2(CutoverTaskDO task, Long actorId, Long expectedVersion) {
        require(CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                        && actorId.equals(task.getOwnerUserId()), NOT_FOUND, "割接任务不可见");
        require(CutoverTaskRules.STAGE_P2.equals(task.getCurrentStage())
                        && CutoverTaskRules.STATUS_GRADE_CONFIRMING.equals(task.getTaskStatus()),
                STATE_CONFLICT, "割接任务不在P2人工分级阶段");
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw new CutoverTaskApplicationException(TASK_VERSION_CONFLICT, "任务版本已变化",
                    task.getVersion(), null);
        }
    }

    private static void requireAssessmentVersion(boolean condition, Long currentAssessmentVersion) {
        if (!condition) {
            throw new CutoverTaskApplicationException(ASSESSMENT_VERSION_CONFLICT, "评估版本已变化",
                    null, currentAssessmentVersion);
        }
    }

    private static void requireTaskVersion(boolean condition, Long currentTaskVersion) {
        if (!condition) {
            throw new CutoverTaskApplicationException(TASK_VERSION_CONFLICT, "任务版本已变化",
                    currentTaskVersion, null);
        }
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

    private static void requireProjectContext(CutoverProjectContextPort.Inspection expected,
                                              CutoverProjectContextPort.ProjectContextFact fact) {
        Long tenantId = expected.tenantId();
        Long projectId = expected.projectId();
        long projectScopeVersion = expected.expectedProjectScopeVersion();
        require(fact != null && tenantId.equals(fact.tenantId()) && projectId.equals(fact.projectId())
                        && fact.projectVersion() >= 0 && projectScopeVersion == fact.projectScopeVersion()
                        && positive(fact.departmentId())
                        && normalizedText(fact.projectCode(), 64)
                        && normalizedText(fact.departmentCode(), 64) && normalizedText(fact.projectName(), 255)
                        && normalizedText(fact.departmentName(), 255),
                PROJ_PROVIDER_UNAVAILABLE, "项目上下文事实不完整");
    }

    private static void requireEquivalentDevices(List<CutoverDeviceScopePort.DeviceFact> expected,
                                                 List<CutoverDeviceScopePort.DeviceFact> actual) {
        Map<Long, String> expectedIdentity = expected.stream().collect(Collectors.toMap(
                CutoverDeviceScopePort.DeviceFact::deviceId,
                d -> serialKey(d.serialNumber()) + ":" + d.projectId() + ":" + d.projectAssignmentVersion()));
        Map<Long, String> actualIdentity = actual.stream().collect(Collectors.toMap(
                CutoverDeviceScopePort.DeviceFact::deviceId,
                d -> serialKey(d.serialNumber()) + ":" + d.projectId() + ":" + d.projectAssignmentVersion()));
        require(expectedIdentity.equals(actualIdentity), DEVICE_SCOPE_STALE, "设备归属事实已变化");
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
        require(devices != null, AST_PROVIDER_UNAVAILABLE, "设备事实不可用");
        return devices.stream().sorted(Comparator.comparing(CutoverDeviceScopePort.DeviceFact::deviceId)).toList();
    }

    private static Map<Long, CutoverDeviceProductTypePort.ProductTypeFact> productTypesByDevice(
            List<CutoverDeviceProductTypePort.ProductTypeFact> productTypes) {
        Map<Long, CutoverDeviceProductTypePort.ProductTypeFact> byDevice = productTypes.stream().collect(Collectors.toMap(
                CutoverDeviceProductTypePort.ProductTypeFact::deviceId, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
        return Collections.unmodifiableMap(byDevice);
    }

    private static Map<Long, CutoverDeviceProductTypePort.ProductTypeFact> frozenProductTypes(
            List<CutoverTaskDeviceScopeDO> storedDevices) {
        return productTypesByDevice(storedDevices.stream()
                .filter(row -> present(row.getDeviceTypeCodeSnapshot())
                        && present(row.getDeviceTypeSourceVersionSnapshot()))
                .map(row -> new CutoverDeviceProductTypePort.ProductTypeFact(row.getDeviceId(),
                        row.getDeviceTypeCodeSnapshot(), true, row.getDeviceTypeSourceVersionSnapshot(),
                        "RESOLVED", "FROZEN", null, false))
                .sorted(Comparator.comparing(CutoverDeviceProductTypePort.ProductTypeFact::deviceId))
                .toList());
    }

    private static Map<String, Object> createDigestValue(CreateCutoverTaskCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("intakeSourceType", command.intakeSourceType());
        value.put("projectId", command.projectId());
        value.put("serialNumbers", normalizeSerials(command.serialNumbers()));
        value.put("configurationCode", command.configurationCode());
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

    private static void requireExpectedCreateContext(CreateCutoverTaskCommand command, ResolvedContext current) {
        if (!"SELF_CREATED".equals(command.intakeSourceType())) {
            return;
        }
        CreateCutoverTaskCommand.ExpectedCreateContext expected = command.expectedContext();
        require(expected != null && expected.project() != null && expected.devices() != null,
                INVALID_REQUEST, "创建期望上下文不完整");
        require(expected.project().equals(current.project()), PROJECT_CONTEXT_STALE, "项目上下文事实已变化");
        requireEquivalentDevices(expected.devices(), current.devices());
    }

    private static void requireEquivalentReadiness(CutoverReadinessPort.ReadinessFact expected,
                                                    CutoverReadinessPort.ReadinessFact actual) {
        require(actual != null && Objects.equals(expected.snapshotId(), actual.snapshotId())
                        && expected.snapshotVersion() == actual.snapshotVersion()
                        && Objects.equals(expected.projectId(), actual.projectId())
                        && Objects.equals(expected.deviceIds(), actual.deviceIds()),
                IMPLEMENTATION_READINESS_STALE, "实施就绪事实已变化");
    }

    private static CutoverTaskSnapshotStore.Context snapshotContext(ResolvedContext context, String intakeSourceType) {
        return CutoverTaskSnapshotStore.Context.builder().project(context.project()).customer(context.customer())
                .readiness(context.readiness()).intakeSourceType(intakeSourceType).build();
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
                                   Map<Long, CutoverDeviceProductTypePort.ProductTypeFact> productTypesByDevice,
                                   CutoverCustomerLevelPort.CustomerLevelFact customer,
                                   CutoverReadinessPort.ReadinessFact readiness) {
    }

}
