package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverEffectiveConfigurationListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;

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

import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.DATA_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverRuntimeContextSupport.*;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.INVALID_REQUEST;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.NOT_FOUND;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.AST_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.CUS_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.CONFIGURATION_CONFLICT;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.IMP_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.PROJ_PROVIDER_UNAVAILABLE;

/** 专项运行查询；独立任务按修订014办理，历史阶段按实际业务进度展示。 */
public class CutoverRuntimeTaskQueryService extends CutoverTaskQueryService {

    private final cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverFrozenDeviceMapper frozenDevices;
    private final cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverSelectedTaskMapper selectedTasks;
    private final cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience approvalAudience;

    public CutoverRuntimeTaskQueryService(CutoverTaskMapper taskMapper,
                                   cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper deviceMapper,
                                   CutoverAssessmentMapper assessmentMapper,
                                   CutoverChecklistMapper checklistMapper,
                                   CutoverConfigurationRevisionMapper configurationMapper,
                                   CutoverProjectScopePort projectScopePort,
                                   CutoverProjectContextPort projectContextPort,
                                   CutoverDeviceScopePort deviceScopePort,
                                   CutoverCustomerLevelPort customerLevelPort,
                                   CutoverReadinessPort readinessPort,
                                   Clock clock, cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverFrozenDeviceMapper frozenDevices,
                                   cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverSelectedTaskMapper selectedTasks,
                                   cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience approvalAudience) {
        super(taskMapper, deviceMapper, assessmentMapper, checklistMapper, configurationMapper,
                projectScopePort, projectContextPort, deviceScopePort, customerLevelPort, readinessPort, clock);
        this.frozenDevices = frozenDevices;
        this.selectedTasks = selectedTasks;
        this.approvalAudience = approvalAudience;
    }

    @Override
    public CutoverTaskViews.CreateContextData resolveCreateContext(CutoverTaskContext context, List<String> serialNumbers) {
        Long tenantId = context.tenantId();
        Long actorId = context.actorId();
        List<String> normalized = normalizeSerials(serialNumbers);
        List<CutoverDeviceScopePort.DeviceFact> devices = deviceScopePort.resolveBySerials(normalized);
        require(devices != null && !devices.isEmpty()
                        && devices.stream().map(device -> serialKey(device.serialNumber())).collect(Collectors.toSet())
                            .equals(normalized.stream().map(CutoverRuntimeTaskQueryService::serialKey).collect(Collectors.toSet())), AST_PROVIDER_UNAVAILABLE,
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
                .map(CutoverRuntimeTaskQueryService::configurationChoice)
                .toList();
        require(!choices.isEmpty(), CONFIGURATION_CONFLICT, "当前没有可用的割接配置");
        return new CutoverTaskViews.CreateContextData(List.copyOf(candidates), candidates.size() > 1,
                choices, true);
    }

    public PageResult<CutoverTaskViews.Summary> pageForTaskIds(CutoverTaskContext context, Set<Long> taskIds, PageParam page) {
        require(page.getPageNo() > 0 && page.getPageSize() > 0 && page.getPageSize() <= 100, INVALID_REQUEST, "分页参数非法");
        if (taskIds.isEmpty()) return new PageResult<>(List.of(), 0L);
        Set<Long> visible = projectScopePort.resolveAllCurrent(context.actorId(), ACTION_VIEW);
        var approvalTasks = approvalAudience.authorizedTaskIds(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience.Selection(
                context.tenantId(), context.actorId(), taskIds));
        if (visible.isEmpty() && approvalTasks.isEmpty()) return new PageResult<>(List.of(), 0L);
        var result = selectedTasks.selectedPage(new cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverSelectedTaskMapper.SelectedTaskPageQuery(
                context.tenantId(), visible, taskIds, approvalTasks, page.getPageNo(), page.getPageSize()));
        return new PageResult<>(result.getList().stream().map(row -> summary(row)
                .withApprovalOnly(!visible.contains(row.getProjectId()))).toList(), result.getTotal());
    }

    @Override
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
        } catch (cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException failure) {
            if (failure.code() == cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE
                    || failure.code() == cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException.Code.STALE)
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

    @Override
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

    @Override
    protected List<CutoverDeviceScopePort.DeviceFact> taskDevices(CutoverTaskDO task) {
        return frozenDevices.byTask(new CutoverTaskDeviceListQuery(task.getTenantId(), task.getId()))
                .stream().map(this::device).toList();
    }

    private static String serialKey(String value) { return value.trim().toUpperCase(Locale.ROOT); }

}
