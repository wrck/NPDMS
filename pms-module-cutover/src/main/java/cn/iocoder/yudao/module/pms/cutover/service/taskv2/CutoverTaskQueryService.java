package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskPageQuery;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.DATA_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.INVALID_REQUEST;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.NOT_FOUND;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.OWNER_PROVIDER_UNAVAILABLE;

/** CUT只读查询编排；生产Bean在正式Owner依赖接通后统一注册。 */
public class CutoverTaskQueryService {

    private static final String ACTION_VIEW = "ACTION_VIEW";
    private static final String ACTION_EDIT = "ACTION_EDIT";
    private static final List<String> STAGES = List.of("P2", "P3", "P4", "P5", "P6");
    private static final Map<String, String> STAGE_LABELS = Map.of(
            "P2", "人工分级", "P3", "现场调研", "P4", "方案编制", "P5", "复核审批", "P6", "执行归档");

    private final CutoverTaskMapper taskMapper;
    private final CutoverTaskDeviceScopeMapper deviceMapper;
    private final CutoverAssessmentMapper assessmentMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverProjectContextPort projectContextPort;
    private final CutoverDeviceScopePort deviceScopePort;
    private final CutoverCustomerLevelPort customerLevelPort;
    private final CutoverReadinessPort readinessPort;

    public CutoverTaskQueryService(CutoverTaskMapper taskMapper,
                                   CutoverTaskDeviceScopeMapper deviceMapper,
                                   CutoverAssessmentMapper assessmentMapper,
                                   CutoverProjectScopePort projectScopePort,
                                   CutoverProjectContextPort projectContextPort,
                                   CutoverDeviceScopePort deviceScopePort,
                                   CutoverCustomerLevelPort customerLevelPort,
                                   CutoverReadinessPort readinessPort) {
        this.taskMapper = taskMapper;
        this.deviceMapper = deviceMapper;
        this.assessmentMapper = assessmentMapper;
        this.projectScopePort = projectScopePort;
        this.projectContextPort = projectContextPort;
        this.deviceScopePort = deviceScopePort;
        this.customerLevelPort = customerLevelPort;
        this.readinessPort = readinessPort;
    }

    public CutoverTaskViews.CreateContextData resolveCreateContext(Long tenantId, Long actorId,
                                                                   List<String> serialNumbers) {
        List<String> normalized = normalizeSerials(serialNumbers);
        List<CutoverDeviceScopePort.DeviceFact> devices = deviceScopePort.resolveBySerials(normalized);
        require(devices != null && devices.size() == normalized.size(), OWNER_PROVIDER_UNAVAILABLE,
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
            CutoverProjectContextPort.ProjectContextFact project = projectContextPort.inspect(
                    tenantId, entry.getKey(), scope.projectScopeVersion());
            require(project != null && tenantId.equals(project.tenantId()) && entry.getKey().equals(project.projectId()),
                    OWNER_PROVIDER_UNAVAILABLE, "项目上下文不可用");
            CutoverCustomerLevelPort.CustomerLevelFact customer = customerLevelPort.inspect(project.customerId());
            CutoverReadinessPort.ReadinessFact readiness = readinessPort.inspect(entry.getKey(),
                    entry.getValue().stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).toList());
            require(customer != null && readiness != null, OWNER_PROVIDER_UNAVAILABLE, "创建上下文不可用");
            boolean createAllowed = "READY".equals(readiness.decision())
                    && readiness.unmetCodes() != null && readiness.unmetCodes().isEmpty();
            candidates.add(new CutoverTaskViews.CreateContextCandidate(project, List.copyOf(entry.getValue()),
                    customer, readiness, createAllowed));
        }
        require(!candidates.isEmpty() && candidates.stream().mapToInt(candidate -> candidate.devices().size()).sum()
                == devices.size(), DATA_SCOPE_FORBIDDEN, "设备不在可创建项目范围内");
        candidates.sort(Comparator.comparing(candidate -> candidate.project().projectId()));
        return new CutoverTaskViews.CreateContextData(List.copyOf(candidates), candidates.size() > 1);
    }

    public PageResult<CutoverTaskViews.Summary> page(Long tenantId, Long actorId, Long projectId,
                                                     String taskStatus, String currentStage,
                                                     int pageNo, int pageSize) {
        require(pageNo > 0 && pageSize > 0 && pageSize <= 100, INVALID_REQUEST, "分页参数非法");
        Set<Long> visible = projectScopePort.resolveAllCurrent(actorId, ACTION_VIEW);
        require(visible != null, OWNER_PROVIDER_UNAVAILABLE, "项目范围不可用");
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        PageResult<CutoverTaskDO> result = taskMapper.selectPage(new CutoverTaskPageQuery(
                tenantId, visible, projectId, taskStatus, currentStage, pageParam));
        return new PageResult<>(result.getList().stream().map(this::summary).toList(), result.getTotal());
    }

    public CutoverTaskViews.Detail detail(Long tenantId, Long actorId, Long taskId,
                                          boolean canSaveAssessment, boolean canSubmitAssessment) {
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && tenantId.equals(task.getTenantId()), NOT_FOUND, "割接任务不存在");
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, task.getProjectId(), ACTION_VIEW);
        require(scope != null && scope.allowed(), NOT_FOUND, "割接任务不可见");

        CutoverProjectContextPort.ProjectContextFact project = parse(task.getProjectContextSnapshot(),
                CutoverProjectContextPort.ProjectContextFact.class);
        CutoverCustomerLevelPort.CustomerLevelFact customer = parse(task.getCustomerContextSnapshot(),
                CutoverCustomerLevelPort.CustomerLevelFact.class);
        CutoverReadinessPort.ReadinessFact readiness = parse(task.getReadinessContextSnapshot(),
                CutoverReadinessPort.ReadinessFact.class);
        List<CutoverDeviceScopePort.DeviceFact> devices = deviceMapper.selectActiveByTask(
                        new CutoverTaskDeviceListQuery(tenantId, taskId)).stream()
                .map(this::device).toList();
        CutoverAssessmentDO assessmentRow = task.getCurrentAssessmentId() == null
                ? null : assessmentMapper.selectById(task.getCurrentAssessmentId());
        CutoverTaskViews.Assessment assessment = assessment(assessmentRow);
        List<String> actions = allowedActions(task, assessmentRow, actorId, canSaveAssessment, canSubmitAssessment);
        return new CutoverTaskViews.Detail(taskCore(task, project),
                new CutoverTaskViews.Source(task.getIntakeSourceType(), task.getSourceSystem(),
                        task.getSourceBusinessNo(), task.getBusinessEventId(), task.getLegacyTaskId()),
                new CutoverTaskViews.Project(task.getProjectId(), project == null ? null : project.projectCode(),
                        project == null ? null : project.projectName(), task.getProjectScopeVersion()),
                devices, customer, readiness, assessment, workbench(task.getCurrentStage()), actions);
    }

    private CutoverTaskViews.Summary summary(CutoverTaskDO task) {
        CutoverProjectContextPort.ProjectContextFact project = parse(task.getProjectContextSnapshot(),
                CutoverProjectContextPort.ProjectContextFact.class);
        return new CutoverTaskViews.Summary(task.getId(), task.getTaskNo(), task.getTaskName(), task.getTaskOrigin(),
                task.getIntakeSourceType(), task.getProjectId(), project == null ? null : project.projectName(),
                project == null ? null : project.departmentCode(), project == null ? null : project.departmentName(),
                task.getOwnerUserId(), task.getCurrentStage(), task.getTaskStatus(), task.getManualGrade(),
                task.getScheduledTime(), task.getCreateTime(), task.getVersion());
    }

    private CutoverTaskViews.TaskCore taskCore(CutoverTaskDO task,
                                               CutoverProjectContextPort.ProjectContextFact project) {
        return new CutoverTaskViews.TaskCore(task.getId(), task.getTaskNo(), task.getTaskName(), task.getBackground(),
                task.getTaskOrigin(), task.getCutoverType(), task.getNetworkMode(), task.getProjectId(),
                project == null ? null : project.projectName(), task.getOwnerUserId(), task.getCurrentStage(),
                task.getTaskStatus(), task.getManualGrade(), task.getScheduledTime(), task.getCreateTime(),
                task.getVersion());
    }

    private CutoverTaskViews.Assessment assessment(CutoverAssessmentDO row) {
        if (row == null) {
            return null;
        }
        CutoverTaskViews.StoredAssessmentContext context = parse(row.getContextSnapshot(),
                CutoverTaskViews.StoredAssessmentContext.class);
        return new CutoverTaskViews.Assessment(row.getId(), row.getAssessmentVersion(), row.getVersion(),
                row.getAssessmentStatus(), row.getQuestionnaireTemplateCode(), row.getQuestionnaireTemplateVersion(),
                parse(row.getAnswerSnapshot(), CutoverAssessmentAnswers.class),
                context == null ? null : context.customerServiceLevel(), row.getManualGrade(),
                Boolean.TRUE.equals(row.getSimpleFlow()), row.getSubmittedBy(), row.getSubmittedAt(),
                row.getInvalidatedAt(), row.getInvalidationReason());
    }

    private List<String> allowedActions(CutoverTaskDO task, CutoverAssessmentDO assessment, Long actorId,
                                        boolean canSave, boolean canSubmit) {
        if (!CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                || !CutoverTaskRules.STAGE_P2.equals(task.getCurrentStage())
                || !CutoverTaskRules.STATUS_GRADE_CONFIRMING.equals(task.getTaskStatus())
                || !actorId.equals(task.getOwnerUserId())) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        boolean submitted = assessment != null && CutoverTaskRules.ASSESSMENT_SUBMITTED.equals(assessment.getAssessmentStatus());
        if (canSave && !submitted) {
            actions.add("SAVE_ASSESSMENT");
        }
        if (canSubmit && assessment != null && CutoverTaskRules.ASSESSMENT_DRAFT.equals(assessment.getAssessmentStatus())
                && assessment.getManualGrade() != null) {
            actions.add("SUBMIT_ASSESSMENT");
        }
        return List.copyOf(actions);
    }

    private List<CutoverTaskViews.WorkbenchStep> workbench(String currentStage) {
        return STAGES.stream().map(stage -> {
            boolean current = stage.equals(currentStage);
            boolean completed = "P2".equals(stage) && ("P3".equals(currentStage) || "P4".equals(currentStage));
            String state = current ? "CURRENT" : completed ? "COMPLETED" : "FUTURE";
            return new CutoverTaskViews.WorkbenchStep(stage, STAGE_LABELS.get(stage), state,
                    current, current || completed);
        }).toList();
    }

    private CutoverDeviceScopePort.DeviceFact device(CutoverTaskDeviceScopeDO row) {
        return new CutoverDeviceScopePort.DeviceFact(row.getDeviceId(), row.getSerialNumberSnapshot(),
                row.getProjectId(), row.getProjectAssignmentVersion());
    }

    private static List<String> normalizeSerials(List<String> values) {
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

    private static <T> T parse(String json, Class<T> type) {
        return json == null ? null : JsonUtils.parseObject(json, type);
    }

    private static void require(boolean condition, CutoverTaskApplicationException.Code code, String message) {
        if (!condition) {
            throw new CutoverTaskApplicationException(code, message);
        }
    }
}
