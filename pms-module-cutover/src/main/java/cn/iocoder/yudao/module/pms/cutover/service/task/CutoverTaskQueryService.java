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
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP2P3ActionPolicy;

/** 专项运行查询；独立任务按修订014办理，历史阶段按实际业务进度展示。 */
public class CutoverTaskQueryService {
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRoleApprovalAudience approvalAudience;


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
        return resolveCreateContext(new CreateContextQuery(context, null, serialNumbers));
    }

    public record CreateContextQuery(CutoverTaskContext context, Long projectId, List<String> serialNumbers) { }

    public CutoverTaskViews.CreateContextData resolveCreateContext(CreateContextQuery query) {
        var context = query.context();
        Long tenantId = context.tenantId();
        Long actorId = context.actorId();
        List<String> normalized = normalizeSerials(query.serialNumbers());
        CutoverProjectScopePort.ProjectScopeFact selectedScope = null;
        List<CutoverDeviceScopePort.DeviceFact> devices;
        if (query.projectId() == null) {
            devices = deviceScopePort.resolveBySerials(normalized);
        } else {
            require(query.projectId() > 0, INVALID_REQUEST, "请选择项目");
            selectedScope = projectScopePort.inspect(actorId, query.projectId(), ACTION_EDIT);
            require(selectedScope != null, PROJ_PROVIDER_UNAVAILABLE, "项目范围事实不可用");
            require(selectedScope.allowed() && Objects.equals(selectedScope.projectId(), query.projectId()),
                    DATA_SCOPE_FORBIDDEN, "项目不在可创建割接任务的范围内");
            devices = deviceScopePort.resolveBySerials(
                    new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery(
                            tenantId, query.projectId(), normalized));
        }
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
            if (query.projectId() != null && !query.projectId().equals(entry.getKey())) continue;
            CutoverProjectScopePort.ProjectScopeFact scope = selectedScope == null
                    ? projectScopePort.inspect(actorId, entry.getKey(), ACTION_EDIT) : selectedScope;
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
        var editEligibility = p2EditEligibility(context, task, assessment);
        if (!editEligibility.editAllowed()) return editEligibility;
        return p2SubmissionEligibility(context, task, stored, assessment);
    }

    private P2ActionEligibility p2EditEligibility(
            CutoverTaskContext context, CutoverTaskDO task, CutoverAssessmentDO assessment) {
        Long actorId = context.actorId();
        if (!CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                || !actorId.equals(task.getOwnerUserId())
                || !CutoverTaskRules.STATUS_GRADE_CONFIRMING.equals(task.getTaskStatus())
                || (assessment != null && !CutoverTaskRules.ASSESSMENT_DRAFT.equals(assessment.getAssessmentStatus()))) {
            return new P2ActionEligibility(false, false, null, "当前任务或评估状态不允许提交");
        }
        var editScope = projectScopePort.inspect(actorId, task.getProjectId(), ACTION_EDIT);
        require(editScope != null, PROJ_PROVIDER_UNAVAILABLE, "项目编辑范围事实不可用");
        if (!editScope.allowed() || !task.getProjectId().equals(editScope.projectId())) {
            return new P2ActionEligibility(false, false, null, "当前账号无本项目编辑范围，请核查项目授权");
        }
        if (!Objects.equals(task.getProjectScopeVersion(), editScope.projectScopeVersion())) {
            return new P2ActionEligibility(false, false, null, "项目范围版本已变化，请核查原任务项目范围后再办理");
        }
        return new P2ActionEligibility(true, false, null, null);
    }

    private P2ActionEligibility p2SubmissionEligibility(
            CutoverTaskContext context, CutoverTaskDO task,
            CutoverTaskViews.StoredAssessmentContext stored, CutoverAssessmentDO assessment) {
        Long tenantId = context.tenantId();
        var storedDevices = stored.devices();
        try {
            var currentProject = projectContextPort.inspect(CutoverProjectContextPort.Inspection.builder()
                    .tenantId(tenantId).projectId(task.getProjectId())
                    .expectedProjectScopeVersion(task.getProjectScopeVersion()).build());
            require(currentProject != null && tenantId.equals(currentProject.tenantId())
                    && task.getProjectId().equals(currentProject.projectId())
                    && currentProject.projectScopeVersion() == task.getProjectScopeVersion(),
                    PROJ_PROVIDER_UNAVAILABLE, "项目来源暂不可用，请刷新重试");
            var currentDevices = deviceScopePort.resolveBySerials(
                    new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery(tenantId, task.getProjectId(),
                            storedDevices.stream().map(CutoverDeviceScopePort.DeviceFact::serialNumber).toList()));
            if (!sameSelectedDevices(storedDevices, currentDevices)) {
                return new P2ActionEligibility(true, false, null,
                        "原选设备已缺失、替换或不属于当前项目，请核查设备归属后重试");
            }
            var currentReadiness = requiresImplementationReadiness(task.getIntakeSourceType())
                    ? readinessPort.inspect(task.getProjectId(), currentDevices.stream()
                        .map(CutoverDeviceScopePort.DeviceFact::deviceId).sorted().toList())
                    : independentReadiness(task.getProjectId(), currentDevices.stream()
                        .map(CutoverDeviceScopePort.DeviceFact::deviceId).toList());
            require(currentReadiness != null, IMP_PROVIDER_UNAVAILABLE, "实施条件暂不可用，请刷新重试");
            var sources = new CutoverTaskViews.StoredAssessmentContext(currentProject, currentDevices,
                    currentReadiness, stored.customerServiceLevel());
            if (!context.canSubmitAssessment()) return new P2ActionEligibility(true, false, sources, "当前账号无提交割接评估权限");
            var answers = assessment == null ? null : CutoverTaskSnapshotStore.answers(assessment);
            if (assessment == null || assessment.getManualGrade() == null || answers == null || !answers.complete()) {
                return new P2ActionEligibility(true, false, sources, "请完整填写评估问卷并选择割接等级");
            }
            if (!sameCustomer(CutoverTaskSnapshotStore.project(assessment), currentProject)) {
                return new P2ActionEligibility(true, false, sources,
                        "项目客户已变化，请先保存草稿更新来源，再重新确认客户服务等级");
            }
            if (!CutoverTaskSnapshotStore.hasRequiredCustomerServiceLevelSelection(assessment)) {
                return new P2ActionEligibility(true, false, sources, "客户未维护服务等级，请手动选择并保存");
            }
            if (requiresImplementationReadiness(task.getIntakeSourceType())
                    && (!"READY".equals(currentReadiness.decision()) || currentReadiness.unmetCodes() == null
                        || !currentReadiness.unmetCodes().isEmpty())) {
                return new P2ActionEligibility(true, false, sources, "当前项目实施条件未就绪，请核查未满足项");
            }
            return new P2ActionEligibility(true, true, sources, null);
        } catch (cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException failure) {
            return new P2ActionEligibility(true, false, null, "项目或设备来源已变化或暂不可用，请刷新并核查归属");
        } catch (CutoverTaskApplicationException failure) {
            if (Set.of(PROJ_PROVIDER_UNAVAILABLE, AST_PROVIDER_UNAVAILABLE, IMP_PROVIDER_UNAVAILABLE).contains(failure.code())) {
                return new P2ActionEligibility(true, false, null, failure.getMessage());
            }
            throw failure;
        } catch (cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException failure) {
            if (failure.getCode() == cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException.Code.PROVIDER_UNAVAILABLE
                    || failure.getCode() == cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException.Code.SNAPSHOT_NOT_FOUND) {
                return new P2ActionEligibility(true, false, null, "项目实施条件暂不可用，请刷新重试");
            }
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

    public record ActionQuery(CutoverTaskContext context, Long taskId) { }
    private record TaskAccess(CutoverTaskDO task, boolean projectRead) { }

    private TaskAccess readAccess(ActionQuery query) {
        CutoverTaskContext context = query.context();
        Long taskId = query.taskId();
        Long tenantId = context.tenantId();
        Long actorId = context.actorId();
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && tenantId.equals(task.getTenantId()), NOT_FOUND, "割接任务不存在");
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, task.getProjectId(), ACTION_VIEW);
        boolean projectRead = scope != null && scope.allowed() && Objects.equals(scope.projectId(), task.getProjectId());
        require(projectRead || approvalAudience.inspectTask(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRoleApprovalAudience.Query(tenantId, actorId, taskId, null)) != null,
                NOT_FOUND, "割接任务不可见");

        return new TaskAccess(task, projectRead);
    }

    /** Test existing save actions before loading sources needed only for submission. */
    public boolean hasWorkbenchAction(ActionQuery query) {
        TaskAccess access = readAccess(query);
        if (!access.projectRead()) return false;
        if ("P2".equals(access.task().getCurrentStage())) {
            var task = access.task();
            var assessment = task.getCurrentAssessmentId() == null ? null : assessmentMapper.selectById(task.getCurrentAssessmentId());
            var editEligibility = p2EditEligibility(query.context(), task, assessment);
            if (!allowedActions(task, assessment, null, query.context(), editEligibility).isEmpty()) return true;
            if (!editEligibility.editAllowed() || !query.context().canSubmitAssessment()) return false;
            var stored = new CutoverTaskViews.StoredAssessmentContext(CutoverTaskSnapshotStore.project(task),
                    taskDevices(task), null, CutoverTaskSnapshotStore.customer(task));
            var eligibility = p2SubmissionEligibility(query.context(), task, stored, assessment);
            return !allowedActions(task, assessment, null, query.context(), eligibility).isEmpty();
        }
        if (!"P3".equals(access.task().getCurrentStage())) return !detail(query, access).allowedActions().isEmpty();
        var task = access.task();
        var assessment = task.getCurrentAssessmentId() == null ? null : assessmentMapper.selectById(task.getCurrentAssessmentId());
        var checklist = checklistMapper.selectCurrent(new CutoverChecklistRowQuery(query.context().tenantId(), query.taskId(), null));
        return !allowedActions(task, assessment, checklist, query.context(), P2ActionEligibility.NOT_APPLICABLE).isEmpty();
    }

    public CutoverTaskViews.Detail detail(CutoverTaskContext context, Long taskId) {
        var query = new ActionQuery(context, taskId);
        return detail(query, readAccess(query));
    }

    private CutoverTaskViews.Detail detail(ActionQuery query, TaskAccess access) {
        CutoverTaskContext context = query.context();
        Long taskId = query.taskId();
        Long tenantId = context.tenantId();
        CutoverTaskDO task = access.task();
        boolean projectRead = access.projectRead();
        CutoverProjectContextPort.ProjectContextFact project = CutoverTaskSnapshotStore.project(task);
        CutoverCustomerLevelPort.CustomerLevelFact customer = CutoverTaskSnapshotStore.customer(task);
        CutoverReadinessPort.ReadinessFact readiness = projectRead ? taskSnapshots.readiness(task) : taskSnapshots.readinessReference(task);
        List<CutoverDeviceScopePort.DeviceFact> devices = taskDevices(task);
        CutoverAssessmentDO assessmentRow = task.getCurrentAssessmentId() == null
                ? null : assessmentMapper.selectById(task.getCurrentAssessmentId());
        CutoverTaskViews.Assessment assessment = assessment(assessmentRow);
        if (assessmentRow != null) customer = CutoverTaskSnapshotStore.customer(assessmentRow);
        if (assessmentRow != null && CutoverTaskRules.ASSESSMENT_SUBMITTED.equals(assessmentRow.getAssessmentStatus())) {
            var submittedProject = CutoverTaskSnapshotStore.project(assessmentRow);
            if (submittedProject != null) project = submittedProject;
        }
        if (projectRead && CutoverTaskRules.STAGE_P2.equals(task.getCurrentStage())
                && (assessmentRow == null || CutoverTaskRules.ASSESSMENT_DRAFT.equals(assessmentRow.getAssessmentStatus()))
                && (customer == null || "UNAVAILABLE".equals(customer.status()))) {
            customer = customerReference(customerLevelPort, project);
        }
        CutoverChecklistDO checklist = Set.of("P3", "P4", "P5").contains(task.getCurrentStage())
                ? checklistMapper.selectCurrent(new CutoverChecklistRowQuery(tenantId, taskId, null)) : null;
        P2ActionEligibility p2Eligibility = projectRead && CutoverTaskRules.STAGE_P2.equals(task.getCurrentStage())
                ? p2ActionEligibility(context, task,
                new CutoverTaskViews.StoredAssessmentContext(project, devices, readiness, customer), assessmentRow)
                : P2ActionEligibility.NOT_APPLICABLE;
        if (p2Eligibility.sources() != null) {
            project = p2Eligibility.sources().project();
            devices = p2Eligibility.sources().devices();
            readiness = p2Eligibility.sources().implementationReadiness();
        }
        List<String> actions = projectRead ? allowedActions(task, assessmentRow, checklist, context, p2Eligibility) : List.of();
        return new CutoverTaskViews.Detail(taskCore(task, project),
                new CutoverTaskViews.Source(task.getIntakeSourceType(), task.getSourceSystem(),
                        task.getSourceBusinessNo(), task.getBusinessEventId()),
                new CutoverTaskViews.Project(task.getProjectId(), project == null ? null : project.projectCode(),
                        project == null ? null : project.projectName(), project == null ? task.getProjectScopeVersion() : project.projectScopeVersion(), project == null ? null : project.departmentName()),
                devices, customer, readiness, assessment, workbench(task), actions, p2Eligibility.blockReason(), !projectRead);
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
        return CutoverTaskViews.Assessment.builder()
                .id(row.getId())
                .assessmentVersion(row.getAssessmentVersion())
                .rowVersion(row.getVersion())
                .status(row.getAssessmentStatus())
                .questionnaireTemplateCode(row.getQuestionnaireTemplateCode())
                .questionnaireTemplateVersion(row.getQuestionnaireTemplateVersion())
                .answers(CutoverTaskSnapshotStore.answers(row))
                .customerServiceLevel(CutoverTaskSnapshotStore.customer(row))
                .manualGrade(row.getManualGrade())
                .simpleFlow(Boolean.TRUE.equals(row.getSimpleFlow()))
                .submittedBy(row.getSubmittedBy())
                .submittedAt(row.getSubmittedAt())
                .invalidatedAt(row.getInvalidatedAt())
                .invalidationReason(row.getInvalidationReason())
                .manualCustomerServiceLevelCode(row.getManualCustomerServiceLevelCode())
                .manualCustomerServiceLevelSelectedBy(row.getManualCustomerServiceLevelSelectedBy())
                .manualCustomerServiceLevelSelectedAt(row.getManualCustomerServiceLevelSelectedAt())
                .build();
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
        var actions = new java.util.ArrayList<>(ACTION_POLICY.allowedActions(candidate, facts, permissions));
        if ("NEW_PLATFORM".equals(task.getTaskOrigin()) && Objects.equals(task.getOwnerUserId(), context.actorId())
                && (context.canSaveAssessment() || context.canSaveChecklist())
                && java.util.Set.of("P3", "P4", "P5").contains(task.getCurrentStage())) {
            var editScope = projectScopePort.inspect(context.actorId(), task.getProjectId(), "ACTION_EDIT");
            if (editScope != null && editScope.allowed() && Objects.equals(editScope.projectId(), task.getProjectId())) {
                if (context.canSaveAssessment() && assessment != null && "SUBMITTED".equals(assessment.getAssessmentStatus())) {
                    try { CutoverTaskRules.revisionTarget(task.getCurrentStage(), task.getTaskStatus(), task.getManualGrade(), "P2"); actions.add("REVISE_ASSESSMENT"); }
                    catch (IllegalArgumentException unavailable) { /* 状态机决定可用动作。 */ }
                }
                if (context.canSaveChecklist() && checklist != null && "SUBMITTED".equals(checklist.getStatusCode())) {
                    try { CutoverTaskRules.revisionTarget(task.getCurrentStage(), task.getTaskStatus(), task.getManualGrade(), "P3"); actions.add("REVISE_CHECKLIST"); }
                    catch (IllegalArgumentException unavailable) { /* 状态机决定可用动作。 */ }
                }
            }
        }
        return List.copyOf(actions);
    }

    protected record P2ActionEligibility(boolean editAllowed, boolean submitAllowed,
                                         CutoverTaskViews.StoredAssessmentContext sources, String blockReason) {
        protected static final P2ActionEligibility NOT_APPLICABLE = new P2ActionEligibility(false, false, null, null);
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
