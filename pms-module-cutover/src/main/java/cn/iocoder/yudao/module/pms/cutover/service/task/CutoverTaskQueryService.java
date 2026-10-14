package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverEffectiveConfigurationListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.DATA_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverContextSupport.*;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.INVALID_REQUEST;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.NOT_FOUND;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.AST_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.CUS_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.CONFIGURATION_CONFLICT;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.IMP_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.PROJ_PROVIDER_UNAVAILABLE;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP2P3ActionPolicy;

/** 专项运行查询；独立任务按修订014办理，历史阶段按实际业务进度展示。 */
public class CutoverTaskQueryService {

    private static final CutoverP2P3ActionPolicy ACTION_POLICY = new CutoverP2P3ActionPolicy();

    protected static final String ACTION_VIEW = "ACTION_VIEW";
    protected static final String ACTION_EDIT = "ACTION_EDIT";
    protected static final List<String> STAGES = List.of("P2", "P3", "P4", "P5", "P6");
    protected static final Map<String, String> STAGE_LABELS = Map.of(
            "P2", "人工分级", "P3", "现场调研", "P4", "方案编制", "P5", "复核审批", "P6", "执行归档");

    private final CutoverTaskMapper taskMapper;
    private final CutoverTaskDeviceScopeMapper deviceMapper;
    private final CutoverAssessmentMapper assessmentMapper;
    private final CutoverChecklistMapper checklistMapper;
    protected final CutoverConfigurationRevisionMapper configurationMapper;
    protected final CutoverProjectScopePort projectScopePort;
    protected final CutoverProjectContextPort projectContextPort;
    protected final CutoverDeviceScopePort deviceScopePort;
    protected final CutoverCustomerLevelPort customerLevelPort;
    protected final CutoverReadinessPort readinessPort;
    protected final Clock clock;
    @jakarta.annotation.Resource
    protected CutoverTaskSnapshotStore taskSnapshots;

    private final cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverFrozenDeviceMapper frozenDevices;

    public CutoverTaskQueryService(CutoverTaskMapper taskMapper,
                                   cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper deviceMapper,
                                   CutoverAssessmentMapper assessmentMapper,
                                   CutoverChecklistMapper checklistMapper,
                                   CutoverConfigurationRevisionMapper configurationMapper,
                                   CutoverProjectScopePort projectScopePort,
                                   CutoverProjectContextPort projectContextPort,
                                   CutoverDeviceScopePort deviceScopePort,
                                   CutoverCustomerLevelPort customerLevelPort,
                                   CutoverReadinessPort readinessPort,
                                   Clock clock, cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverFrozenDeviceMapper frozenDevices) {
        this.taskMapper = taskMapper;
        this.deviceMapper = deviceMapper;
        this.assessmentMapper = assessmentMapper;
        this.checklistMapper = checklistMapper;
        this.configurationMapper = configurationMapper;
        this.projectScopePort = projectScopePort;
        this.projectContextPort = projectContextPort;
        this.deviceScopePort = deviceScopePort;
        this.customerLevelPort = customerLevelPort;
        this.readinessPort = readinessPort;
        this.clock = clock;
        this.frozenDevices = frozenDevices;
    }

    public CutoverTaskViews.CreateContextData resolveCreateContext(CutoverTaskContext context, List<String> serialNumbers) {
        Long tenantId = context.tenantId();
        Long actorId = context.actorId();
        List<String> normalized = normalizeSerials(serialNumbers);
        List<CutoverDeviceScopePort.DeviceFact> devices = deviceScopePort.resolveBySerials(normalized);
        require(devices != null && !devices.isEmpty()
                        && devices.stream().map(device -> serialKey(device.serialNumber())).collect(Collectors.toSet())
                            .equals(normalized.stream().map(CutoverTaskQueryService::serialKey).collect(Collectors.toSet())), AST_PROVIDER_UNAVAILABLE,
                "设备事实不完整");
        Map<Long, List<CutoverDeviceScopePort.DeviceFact>> byProject = devices.stream()
                .sorted(Comparator.comparing(CutoverDeviceScopePort.DeviceFact::deviceId))
                .collect(Collectors.groupingBy(CutoverDeviceScopePort.DeviceFact::projectId,
                        LinkedHashMap::new, Collectors.toList()));
        List<CutoverTaskViews.CreateContextCandidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, List<CutoverDeviceScopePort.DeviceFact>> entry : byProject.entrySet()) {
            CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, entry.getKey(), ACTION_EDIT);
            if (scope == null || !scope.allowed()) {
                continue;
            }
            CutoverProjectContextPort.ProjectContextFact project = projectContextPort.inspect(CutoverProjectContextPort.Inspection.builder()
                    .tenantId(tenantId)
                    .projectId(entry.getKey())
                    .expectedProjectScopeVersion(scope.projectScopeVersion())
                    .build());
            require(project != null && tenantId.equals(project.tenantId()) && entry.getKey().equals(project.projectId()),
                    PROJ_PROVIDER_UNAVAILABLE, "项目上下文不可用");
            CutoverCustomerLevelPort.CustomerLevelFact customer = customerReference(customerLevelPort, project);
            CutoverReadinessPort.ReadinessFact readiness = independentReadiness(entry.getKey(),
                    entry.getValue().stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).toList());
            boolean createAllowed = true;
            candidates.add(new CutoverTaskViews.CreateContextCandidate(project, List.copyOf(entry.getValue()),
                    customer, readiness, createAllowed));
        }
        require(!candidates.isEmpty(), DATA_SCOPE_FORBIDDEN, "设备不在可创建项目范围内");
        candidates.sort(Comparator.comparing(candidate -> candidate.project().projectId()));
        List<CutoverTaskViews.ConfigurationChoice> choices = configurationMapper
                .selectEffectivePublishedList(new CutoverEffectiveConfigurationListQuery(
                        tenantId, LocalDateTime.now(clock))).stream()
                .map(CutoverTaskQueryService::configurationChoice)
                .toList();
        require(!choices.isEmpty(), CONFIGURATION_CONFLICT, "当前没有可用的割接配置");
        return new CutoverTaskViews.CreateContextData(List.copyOf(candidates), candidates.size() > 1,
                choices, true);
    }

    protected P2ActionEligibility p2ActionEligibility(
            CutoverTaskContext context, CutoverTaskDO task,
            CutoverTaskViews.StoredAssessmentContext stored, CutoverAssessmentDO assessment) {
        Long tenantId = context.tenantId();
        Long actorId = context.actorId();
        var storedProject = stored.project();
        var storedDevices = stored.devices();
        var storedReadiness = stored.implementationReadiness();
        if (!CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                || !actorId.equals(task.getOwnerUserId())
                || !CutoverTaskRules.STATUS_GRADE_CONFIRMING.equals(task.getTaskStatus())
                || (!context.canSaveAssessment() && !context.canSubmitAssessment())
                || (assessment != null
                && CutoverTaskRules.ASSESSMENT_SUBMITTED.equals(assessment.getAssessmentStatus()))) {
            return P2ActionEligibility.NOT_APPLICABLE;
        }
        CutoverProjectScopePort.ProjectScopeFact editScope = projectScopePort.inspect(
                actorId, task.getProjectId(), ACTION_EDIT);
        require(editScope != null, PROJ_PROVIDER_UNAVAILABLE, "项目编辑范围事实不可用");
        boolean editAllowed = editScope.allowed() && task.getProjectId().equals(editScope.projectId());
        if (!editAllowed) {
            return new P2ActionEligibility(false, false);
        }
        CutoverAssessmentAnswers answers = assessment == null ? null
                : CutoverTaskSnapshotStore.answers(assessment);
        if (!context.canSubmitAssessment() || assessment == null
                || !CutoverTaskRules.ASSESSMENT_DRAFT.equals(assessment.getAssessmentStatus())
                || assessment.getManualGrade() == null || answers == null || !answers.complete()) {
            return new P2ActionEligibility(true, false);
        }
        try {
            CutoverProjectContextPort.ProjectContextFact currentProject = projectContextPort.inspect(CutoverProjectContextPort.Inspection.builder()
                    .tenantId(tenantId)
                    .projectId(task.getProjectId())
                    .expectedProjectScopeVersion(editScope.projectScopeVersion())
                    .build());
            require(currentProject != null, PROJ_PROVIDER_UNAVAILABLE, "项目上下文事实不可用");
            List<CutoverDeviceScopePort.DeviceFact> currentDevices = deviceScopePort.resolveBySerials(
                    storedDevices.stream().map(CutoverDeviceScopePort.DeviceFact::serialNumber).toList());
            require(currentDevices != null, AST_PROVIDER_UNAVAILABLE, "设备事实不可用");
            currentDevices = currentDevices.stream().filter(device -> task.getProjectId().equals(device.projectId())).toList();
            boolean comparable = Objects.equals(storedProject, currentProject)
                    && equivalentDevices(storedDevices, currentDevices);
            if (!requiresImplementationReadiness(task.getIntakeSourceType())) {
                return new P2ActionEligibility(true, comparable);
            }
            CutoverReadinessPort.ReadinessFact currentReadiness = readinessPort.inspect(task.getProjectId(),
                    storedDevices.stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).sorted().toList());
            require(currentReadiness != null, IMP_PROVIDER_UNAVAILABLE, "实施就绪事实不可用");
            boolean submitAllowed = comparable && storedReadiness != null
                    && equivalentReadiness(storedReadiness, currentReadiness)
                    && "READY".equals(currentReadiness.decision())
                    && currentReadiness.unmetCodes() != null && currentReadiness.unmetCodes().isEmpty();
            return new P2ActionEligibility(true, submitAllowed);
        } catch (cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException failure) {
            if (failure.code() == cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE
                    || failure.code() == cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException.Code.STALE)
                return new P2ActionEligibility(true, false);
            throw failure;
        } catch (CutoverTaskApplicationException failure) {
            if (Set.of(PROJ_PROVIDER_UNAVAILABLE, AST_PROVIDER_UNAVAILABLE, IMP_PROVIDER_UNAVAILABLE).contains(failure.code()))
                return new P2ActionEligibility(true, false);
            throw failure;
        } catch (cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException failure) {
            if (failure.getCode() == cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException.Code.PROVIDER_UNAVAILABLE
                    || failure.getCode() == cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException.Code.SNAPSHOT_NOT_FOUND)
                return new P2ActionEligibility(true, false);
            throw failure;
        }
    }

    protected List<CutoverTaskViews.WorkbenchStep> workbench(CutoverTaskDO task) {
        int currentIndex = STAGES.indexOf(task.getCurrentStage());
        boolean archived = "ARCHIVED".equals(task.getTaskStatus());
        return STAGES.stream().map(stage -> {
            boolean skipped = "P3".equals(stage) && "D".equals(task.getManualGrade());
            boolean current = !archived && stage.equals(task.getCurrentStage());
            boolean completed = !skipped && (archived || STAGES.indexOf(stage) < currentIndex);
            String state = skipped ? "SKIPPED" : current ? "CURRENT" : completed ? "COMPLETED" : "FUTURE";
            return new CutoverTaskViews.WorkbenchStep(stage, STAGE_LABELS.get(stage), state,
                    current, !skipped && (current || completed));
        }).toList();
    }

    protected List<CutoverDeviceScopePort.DeviceFact> taskDevices(CutoverTaskDO task) {
        return frozenDevices.byTask(new CutoverTaskDeviceListQuery(task.getTenantId(), task.getId()))
                .stream().map(this::device).toList();
    }

    private static String serialKey(String value) { return value.trim().toUpperCase(Locale.ROOT); }

    public CutoverTaskViews.Detail detail(CutoverTaskContext context, Long taskId) {
        Long tenantId = context.tenantId();
        Long actorId = context.actorId();
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && tenantId.equals(task.getTenantId()), NOT_FOUND, "割接任务不存在");
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, task.getProjectId(), ACTION_VIEW);
        require(scope != null && scope.allowed(), NOT_FOUND, "割接任务不可见");

        CutoverProjectContextPort.ProjectContextFact project = CutoverTaskSnapshotStore.project(task);
        CutoverCustomerLevelPort.CustomerLevelFact customer = CutoverTaskSnapshotStore.customer(task);
        CutoverReadinessPort.ReadinessFact readiness = taskSnapshots.readiness(task);
        List<CutoverDeviceScopePort.DeviceFact> devices = taskDevices(task);
        CutoverAssessmentDO assessmentRow = task.getCurrentAssessmentId() == null
                ? null : assessmentMapper.selectById(task.getCurrentAssessmentId());
        CutoverTaskViews.Assessment assessment = assessment(assessmentRow);
        CutoverChecklistDO checklist = CutoverTaskRules.STAGE_P3.equals(task.getCurrentStage())
                ? checklistMapper.selectCurrent(new CutoverChecklistRowQuery(tenantId, taskId, null)) : null;
        P2ActionEligibility p2Eligibility = CutoverTaskRules.STAGE_P2.equals(task.getCurrentStage())
                ? p2ActionEligibility(context, task,
                new CutoverTaskViews.StoredAssessmentContext(project, devices, readiness, customer), assessmentRow)
                : P2ActionEligibility.NOT_APPLICABLE;
        List<String> actions = allowedActions(task, assessmentRow, checklist, context, p2Eligibility);
        return new CutoverTaskViews.Detail(taskCore(task, project),
                new CutoverTaskViews.Source(task.getIntakeSourceType(), task.getSourceSystem(),
                        task.getSourceBusinessNo(), task.getBusinessEventId()),
                new CutoverTaskViews.Project(task.getProjectId(), project == null ? null : project.projectCode(),
                        project == null ? null : project.projectName(), task.getProjectScopeVersion()),
                devices, customer, readiness, assessment, workbench(task), actions);
    }

    protected CutoverTaskViews.Summary summary(CutoverTaskDO task) {
        CutoverProjectContextPort.ProjectContextFact project = CutoverTaskSnapshotStore.project(task);
        return new CutoverTaskViews.Summary(task.getId(), task.getTaskNo(), task.getTaskName(), task.getTaskOrigin(),
                task.getIntakeSourceType(), task.getConfigurationRevisionId(), task.getConfigurationCode(),
                task.getConfigurationRevisionNo(), task.getProjectId(), project == null ? null : project.projectName(),
                project == null ? null : project.departmentCode(), project == null ? null : project.departmentName(),
                task.getOwnerUserId(), task.getCurrentStage(), task.getTaskStatus(), task.getManualGrade(),
                task.getScheduledTime(), task.getCreateTime(), task.getVersion(), false);
    }

    private CutoverTaskViews.TaskCore taskCore(CutoverTaskDO task,
                                               CutoverProjectContextPort.ProjectContextFact project) {
        return new CutoverTaskViews.TaskCore(task.getId(), task.getTaskNo(), task.getTaskName(), task.getBackground(),
                task.getTaskOrigin(), task.getCutoverType(), task.getNetworkMode(), task.getConfigurationRevisionId(),
                task.getConfigurationCode(), task.getConfigurationRevisionNo(), task.getProjectId(),
                project == null ? null : project.projectName(), task.getOwnerUserId(), task.getCurrentStage(),
                task.getTaskStatus(), task.getManualGrade(), task.getScheduledTime(), task.getCreateTime(),
                task.getVersion());
    }

    protected static CutoverTaskViews.ConfigurationChoice configurationChoice(CutoverConfigurationRevisionDO row) {
        return new CutoverTaskViews.ConfigurationChoice(row.getConfigurationCode(), row.getConfigurationName(),
                row.getId(), row.getRevisionNo(), row.getEffectiveFrom(), row.getEffectiveTo());
    }

    private CutoverTaskViews.Assessment assessment(CutoverAssessmentDO row) {
        if (row == null) {
            return null;
        }
        return new CutoverTaskViews.Assessment(row.getId(), row.getAssessmentVersion(), row.getVersion(),
                row.getAssessmentStatus(), row.getQuestionnaireTemplateCode(), row.getQuestionnaireTemplateVersion(),
                CutoverTaskSnapshotStore.answers(row),
                CutoverTaskSnapshotStore.customer(row), row.getManualGrade(),
                Boolean.TRUE.equals(row.getSimpleFlow()), row.getSubmittedBy(), row.getSubmittedAt(),
                row.getInvalidatedAt(), row.getInvalidationReason());
    }

    private List<String> allowedActions(CutoverTaskDO task, CutoverAssessmentDO assessment,
                                        CutoverChecklistDO checklist, CutoverTaskContext context,
                                        P2ActionEligibility p2Eligibility) {
        CutoverDashboardCandidate candidate = new CutoverDashboardCandidate(task.getId(), task.getTaskOrigin(),
                task.getCurrentStage(), task.getTaskStatus(), task.getOwnerUserId(), context.actorId(), task.getManualGrade());
        var facts = cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts
                .p2p3(assessment == null ? null : assessment.getAssessmentStatus(),
                        assessment != null && assessment.getManualGrade() != null,
                        checklist == null ? null : checklist.getStatusCode(),
                        p2Eligibility.editAllowed(), p2Eligibility.submitAllowed());
        var permissions = cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts
                .p2p3(context.canSaveAssessment(), context.canSubmitAssessment(), context.canSaveChecklist(),
                        context.canRequestCollection(), context.canSubmitChecklist());
        return List.copyOf(ACTION_POLICY.allowedActions(candidate, facts, permissions));
    }

    protected static boolean equivalentDevices(List<CutoverDeviceScopePort.DeviceFact> stored,
                                             List<CutoverDeviceScopePort.DeviceFact> current) {
        if (stored.size() != current.size()) {
            return false;
        }
        Map<Long, String> storedIdentity = stored.stream().collect(Collectors.toMap(
                CutoverDeviceScopePort.DeviceFact::deviceId, CutoverTaskQueryService::deviceIdentity));
        Map<Long, String> currentIdentity = current.stream().collect(Collectors.toMap(
                CutoverDeviceScopePort.DeviceFact::deviceId, CutoverTaskQueryService::deviceIdentity));
        return storedIdentity.equals(currentIdentity);
    }

    private static String deviceIdentity(CutoverDeviceScopePort.DeviceFact fact) {
        return fact.serialNumber().trim().toUpperCase(Locale.ROOT) + ":" + fact.projectId()
                + ":" + fact.projectAssignmentVersion();
    }

    protected static boolean equivalentReadiness(CutoverReadinessPort.ReadinessFact stored,
                                               CutoverReadinessPort.ReadinessFact current) {
        return Objects.equals(stored.snapshotId(), current.snapshotId())
                && stored.snapshotVersion() == current.snapshotVersion()
                && Objects.equals(stored.decision(), current.decision())
                && Objects.equals(stored.projectId(), current.projectId())
                && Objects.equals(stored.deviceIds(), current.deviceIds())
                && Objects.equals(stored.unmetCodes(), current.unmetCodes())
                && Objects.equals(JsonUtils.getObjectMapper().valueToTree(stored.sourceWatermark()),
                JsonUtils.getObjectMapper().valueToTree(current.sourceWatermark()));
    }

    protected record P2ActionEligibility(boolean editAllowed, boolean submitAllowed) {
        protected static final P2ActionEligibility NOT_APPLICABLE = new P2ActionEligibility(false, false);
    }

    protected CutoverDeviceScopePort.DeviceFact device(CutoverTaskDeviceScopeDO row) {
        return new CutoverDeviceScopePort.DeviceFact(row.getDeviceId(), row.getSerialNumberSnapshot(),
                row.getProjectId(), row.getProjectAssignmentVersion());
    }

    protected static List<String> normalizeSerials(List<String> values) {
        require(values != null && !values.isEmpty() && values.size() <= 500,
                INVALID_REQUEST, "SN集合非法");
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String value : values) {
            require(value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= 128,
                    INVALID_REQUEST, "SN格式非法");
            require(normalized.putIfAbsent(value.toUpperCase(Locale.ROOT), value) == null,
                    INVALID_REQUEST, "SN重复");
        }
        return List.copyOf(normalized.values());
    }

    protected static void require(boolean condition, CutoverTaskApplicationException.Code code, String message) {
        if (!condition) {
            throw new CutoverTaskApplicationException(code, message);
        }
    }
}
