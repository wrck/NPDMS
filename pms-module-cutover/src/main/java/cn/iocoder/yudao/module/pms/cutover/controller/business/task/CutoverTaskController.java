package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

import cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo.CutoverTaskResponses;
import cn.iocoder.yudao.module.pms.cutover.controller.business.support.CutoverRequestHeaders;

import cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskListService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverDashboardQueryService;
import cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo.CutoverTaskListRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo.CutoverTaskStatisticsRespVO;
import org.springframework.http.HttpHeaders;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo.CutoverTaskReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo.CutoverCreateContextRespVO;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import tools.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 独立割接生产入口，复用原阶段契约并遵守修订014的来源准入规则。 */
@RequestMapping("/api/v1/pms/cutover-tasks")
@RestController
public class CutoverTaskController {

    private final CutoverTaskApplicationService applicationService;
    private final CutoverTaskQueryService queryService;
    private final CutoverTaskRequestContext requestContext;
    private final CutoverTaskRequestCodec requestCodec;
    private final CutoverTaskListService listService;
    private final CutoverDashboardQueryService dashboard;
    private final CutoverDashboardRequestContext dashboardContext;

    public CutoverTaskController(CutoverTaskApplicationService applicationService,
                                 CutoverTaskQueryService queryService,
                                 CutoverTaskRequestContext requestContext,
                                 CutoverTaskRequestCodec requestCodec, CutoverTaskListService listService,
                                 CutoverDashboardQueryService dashboard, CutoverDashboardRequestContext dashboardContext) {
        this.applicationService = Objects.requireNonNull(applicationService);
        this.queryService = Objects.requireNonNull(queryService);
        this.requestContext = Objects.requireNonNull(requestContext);
        this.requestCodec = Objects.requireNonNull(requestCodec);
        this.listService = Objects.requireNonNull(listService);
        this.dashboard = Objects.requireNonNull(dashboard);
        this.dashboardContext = Objects.requireNonNull(dashboardContext);
    }

    @PostMapping("/actions/resolve-create-context")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:create')")
    public CommonResult<CutoverCreateContextRespVO> resolveCreateContext(
            @RequestBody JsonNode body) {
        CutoverTaskReqVO.ResolveCreateContext request = requestCodec.resolveCreateContext(body);
        require(request != null && request.serialNumbers() != null, "serialNumbers");
        var trusted = requestContext.current();
        return success(createContext(queryService.resolveCreateContext(
                trusted, request.serialNumbers())));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<PageResult<CutoverTaskListRespVO>> list(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        CutoverTaskListQuery query = requestCodec.listQuery(queryParameters);
        var page = listService.page(dashboardContext.current(), query);
        return success(new PageResult<>(page.getList().stream().map(CutoverTaskListRespVO::from).toList(), page.getTotal()));
    }

    @GetMapping("/statistics")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverTaskStatisticsRespVO> statistics() {
        return success(CutoverTaskStatisticsRespVO.from(dashboard.statistics(dashboardContext.current())));
    }

    @ExceptionHandler(cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardOwnerFactException.class)
    public ResponseEntity<CommonResult<CutoverDashboardErrorData>> handleDashboardOwner(
            cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardOwnerFactException failure) {
        boolean unavailable = "OWNER_PROVIDER_UNAVAILABLE".equals(failure.category());
        var body = CommonResult.<CutoverDashboardErrorData>error(unavailable ? 1_011_007_100 : 1_011_007_101,
                failure.getMessage());
        body.setData(new CutoverDashboardErrorData(failure.category(), failure.reasonCode(),
                unavailable ? "RETRY_AFTER_OWNER_RECOVERY" : "CONTACT_SUPPORT", failure.ownerContext()));
        return ResponseEntity.status(unavailable ? 503 : 500).body(body);
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:create')")
    public CommonResult<CutoverTaskResponses.TaskCommand> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody JsonNode body) {
        CutoverTaskReqVO.Create request = requestCodec.create(body);
        require(request != null && request.expectedProjectContext() != null
                && request.expectedDeviceScopeWatermark() != null, "create request");
        var trusted = requestContext.current();
        var project = request.expectedProjectContext();
        CutoverProjectContextPort.ProjectContextFact projectFact = CutoverProjectContextPort.ProjectContextFact.builder()
                .tenantId(trusted.tenantId())
                .projectId(project.projectId())
                .projectVersion(project.projectVersion())
                .projectCode(project.projectCode())
                .projectName(project.projectName())
                .customerId(project.customerId())
                .customerCode(project.customerCode())
                .customerName(project.customerName())
                .departmentId(project.departmentId())
                .departmentCode(project.departmentCode())
                .departmentName(project.departmentName())
                .projectScopeVersion(requiredVersion(request.expectedProjectScopeVersion(), "expectedProjectScopeVersion"))
                .build();
        List<CutoverDeviceScopePort.DeviceFact> devices = request.expectedDeviceScopeWatermark().stream()
                .map(item -> new CutoverDeviceScopePort.DeviceFact(item.deviceId(), item.serialNumber(),
                        request.projectId(), requiredVersion(item.projectAssignmentVersion(),
                        "projectAssignmentVersion"))).toList();
        CutoverCustomerLevelPort.CustomerLevelFact customer = new CutoverCustomerLevelPort.CustomerLevelFact(
                request.expectedCustomerServiceLevelStatus(), project.customerId(), project.customerCode(),
                project.customerName(), request.expectedCustomerServiceLevelRevisionId(),
                request.expectedCustomerServiceLevelCode(), request.expectedCustomerServiceLevelFactVersion() == null
                ? 0L : requiredVersion(request.expectedCustomerServiceLevelFactVersion(), "expectedCustomerServiceLevelFactVersion"),
                request.expectedCustomerServiceLevelEffectiveFrom(), request.expectedCustomerServiceLevelEffectiveTo());
        CutoverReadinessPort.ReadinessFact readiness = new CutoverReadinessPort.ReadinessFact(
                null, 0L, "NOT_APPLICABLE", request.projectId(),
                devices.stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).toList(), null, List.of());
        return success(CutoverTaskResponses.TaskCommand.from(applicationService.create(new CreateCutoverTaskCommand(trusted.tenantId(), trusted.actorId(),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId(), "SELF_CREATED",
                request.projectId(), request.serialNumbers(), request.configurationCode(), request.taskName(),
                request.background(), request.cutoverType(), request.networkMode(), request.scheduledTime(),
                null, null, null, new CreateCutoverTaskCommand.ExpectedCreateContext(
                projectFact, devices, customer, readiness)))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverTaskResponses.Detail> detail(@PathVariable("id") Long id) {
        requireId(id);
        var trusted = requestContext.current();
        return success(CutoverTaskResponses.Detail.from(queryService.detail(trusted, id)));
    }

    @PutMapping("/{id}/assessment")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-assessment')")
    public CommonResult<CutoverTaskResponses.AssessmentCommand> saveAssessment(
            @PathVariable("id") Long id, @RequestHeader HttpHeaders headers,
            @RequestBody JsonNode body) {
        String ifMatch = CutoverRequestHeaders.value(headers, "If-Match");
        String assessmentIfMatch = CutoverRequestHeaders.value(headers, "Assessment-If-Match");
        CutoverTaskReqVO.SaveAssessment request = requestCodec.saveAssessment(body);
        requireId(id);
        require(request != null && request.answers() != null, "assessment request");
        var trusted = requestContext.current();
        var answers = request.answers();
        return success(CutoverTaskResponses.AssessmentCommand.from(applicationService.saveAssessment(new SaveCutoverAssessmentCommand(
                trusted.tenantId(), trusted.actorId(), id, version(ifMatch), version(assessmentIfMatch),
                new CutoverAssessmentAnswers(answers.businessImportanceLevel(), answers.operationComplexityLevel(),
                        answers.hiddenRiskLevel(), answers.sparePartApplied()), request.manualGrade(),
                header(CutoverRequestHeaders.value(headers, "Idempotency-Key"), "Idempotency-Key"), trusted.correlationId()))));
    }

    @PostMapping("/{id}/assessment/actions/submit")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:submit-assessment')")
    public CommonResult<CutoverTaskResponses.TaskCommand> submitAssessment(
            @PathVariable("id") Long id, @RequestHeader HttpHeaders headers,
            @RequestBody JsonNode body) {
        String idempotencyKey = CutoverRequestHeaders.value(headers, "Idempotency-Key");
        String ifMatch = CutoverRequestHeaders.value(headers, "If-Match");
        String assessmentIfMatch = CutoverRequestHeaders.value(headers, "Assessment-If-Match");
        requireId(id);
        requestCodec.emptyCommand(body);
        var trusted = requestContext.current();
        return success(CutoverTaskResponses.TaskCommand.from(applicationService.submitAssessment(new SubmitCutoverAssessmentCommand(
                trusted.tenantId(), trusted.actorId(), id, version(ifMatch), version(assessmentIfMatch),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()))));
    }

    @ExceptionHandler(CutoverTaskApplicationException.class)
    public ResponseEntity<CommonResult<CutoverTaskErrorData>> handleApplication(
            CutoverTaskApplicationException exception) {
        return applicationError(exception);
    }

    static ResponseEntity<CommonResult<CutoverTaskErrorData>> applicationError(CutoverTaskApplicationException exception) {
        ErrorDescriptor descriptor = errorDescriptor(exception.code());
        return error(descriptor.httpStatus(), errorCode(exception.code()), exception.getMessage(), new CutoverTaskErrorData(descriptor.category(), descriptor.reasonCode(), descriptor.recoveryAction(), descriptor.ownerContext(), exception.currentTaskVersion(), exception.currentAssessmentVersion(), null));
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<CommonResult<CutoverTaskErrorData>> handleValidation(Exception exception) {
        return error(400, 1_011_005_100, exception.getMessage(), new CutoverTaskErrorData("VALIDATION", "INVALID_REQUEST", "CORRECT_REQUEST", null, null, null, null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverTaskErrorData>> handlePermission(AccessDeniedException exception) {
        return error(403, 1_011_005_101, exception.getMessage(), new CutoverTaskErrorData("FUNCTION_PERMISSION", "FUNCTION_PERMISSION_DENIED", "REQUEST_PERMISSION", null, null, null, null));
    }

    private static ErrorDescriptor errorDescriptor(CutoverTaskApplicationException.Code code) {
        return switch (code) {
            case INVALID_REQUEST -> new ErrorDescriptor(400, "VALIDATION", "INVALID_REQUEST", "CORRECT_REQUEST", null);
            case NOT_FOUND -> new ErrorDescriptor(404, "NOT_VISIBLE_OR_NOT_FOUND", "CUTOVER_TASK_NOT_VISIBLE",
                    "RETURN_TO_LIST", null);
            case DATA_SCOPE_FORBIDDEN -> new ErrorDescriptor(403, "DATA_SCOPE_FORBIDDEN", "PROJECT_SCOPE_DENIED",
                    "SELECT_AUTHORIZED_CONTEXT", "PROJ");
            case STATE_CONFLICT -> new ErrorDescriptor(409, "STATE_CONFLICT", "CUTOVER_STATE_CONFLICT",
                    "REFRESH_TASK", null);
            case TASK_VERSION_CONFLICT -> new ErrorDescriptor(409, "VERSION_CONFLICT", "TASK_VERSION_STALE",
                    "REFRESH_TASK", null);
            case ASSESSMENT_VERSION_CONFLICT -> new ErrorDescriptor(409, "VERSION_CONFLICT",
                    "ASSESSMENT_VERSION_STALE", "REFRESH_TASK", null);
            case CONFIGURATION_CONFLICT -> new ErrorDescriptor(409, "CONFIGURATION_CONFLICT",
                    "CONFIGURATION_REVISION_CONFLICT", "RESELECT_CONFIGURATION", null);
            case ACTIVE_DEVICE_CONFLICT -> new ErrorDescriptor(409, "ACTIVE_DEVICE_CONFLICT",
                    "DEVICE_ALREADY_ACTIVE_IN_CUTOVER", "OPEN_EXISTING_TASK", null);
            case PROJECT_SCOPE_STALE -> stale("PROJECT_SCOPE_STALE", "PROJ");
            case PROJECT_CONTEXT_STALE -> stale("PROJECT_CONTEXT_STALE", "PROJ");
            case DEVICE_SCOPE_STALE -> stale("DEVICE_SCOPE_STALE", "AST");
            case CUSTOMER_SERVICE_LEVEL_STALE -> stale("CUSTOMER_SERVICE_LEVEL_STALE", "CUS");
            case IMPLEMENTATION_READINESS_STALE -> stale("IMPLEMENTATION_READINESS_STALE", "IMP");
            case READINESS_NOT_READY -> new ErrorDescriptor(422, "READINESS_NOT_READY", "IMPLEMENTATION_NOT_READY",
                    "COMPLETE_IMPLEMENTATION_PREREQUISITES", "IMP");
            case CUSTOMER_CONTEXT_INVALID -> new ErrorDescriptor(422, "CUSTOMER_CONTEXT_INVALID",
                    "CUSTOMER_SERVICE_LEVEL_NOT_CONFIGURED", "COMPLETE_CUSTOMER_SERVICE_LEVEL", "CUS");
            case BUSINESS_GATE_INVALID -> new ErrorDescriptor(422, "BUSINESS_GATE_INVALID",
                    "BUSINESS_CONTEXT_INVALID", "CORRECT_BUSINESS_CONTEXT", null);
            case PROJ_PROVIDER_UNAVAILABLE -> provider("PROJ_PROVIDER_UNAVAILABLE", "PROJ");
            case AST_PROVIDER_UNAVAILABLE -> provider("AST_PROVIDER_UNAVAILABLE", "AST");
            case CUS_PROVIDER_UNAVAILABLE -> provider("CUS_PROVIDER_UNAVAILABLE", "CUS");
            case IMP_PROVIDER_UNAVAILABLE -> provider("IMP_PROVIDER_UNAVAILABLE", "IMP");
            case IDEMPOTENCY_CONFLICT -> new ErrorDescriptor(409, "IDEMPOTENCY_CONFLICT",
                    "IDEMPOTENCY_KEY_CONFLICT", "START_NEW_INTENT", null);
            case IDEMPOTENCY_IN_PROGRESS -> new ErrorDescriptor(409, "IDEMPOTENCY_IN_PROGRESS",
                    "IDEMPOTENCY_IN_PROGRESS", "RETRY_SAME_KEY", null);
        };
    }

    private static int errorCode(CutoverTaskApplicationException.Code code) {
        return switch (code) {
            case INVALID_REQUEST -> 1_011_005_100;
            case NOT_FOUND -> 1_011_005_102;
            case DATA_SCOPE_FORBIDDEN -> 1_011_005_103;
            case STATE_CONFLICT -> 1_011_005_104;
            case TASK_VERSION_CONFLICT -> 1_011_005_105;
            case ASSESSMENT_VERSION_CONFLICT -> 1_011_005_122;
            case CONFIGURATION_CONFLICT -> 1_011_005_106;
            case ACTIVE_DEVICE_CONFLICT -> 1_011_005_107;
            case PROJECT_SCOPE_STALE -> 1_011_005_108;
            case PROJECT_CONTEXT_STALE -> 1_011_005_109;
            case DEVICE_SCOPE_STALE -> 1_011_005_110;
            case CUSTOMER_SERVICE_LEVEL_STALE -> 1_011_005_111;
            case IMPLEMENTATION_READINESS_STALE -> 1_011_005_112;
            case READINESS_NOT_READY -> 1_011_005_113;
            case CUSTOMER_CONTEXT_INVALID -> 1_011_005_114;
            case BUSINESS_GATE_INVALID -> 1_011_005_115;
            case PROJ_PROVIDER_UNAVAILABLE -> 1_011_005_116;
            case AST_PROVIDER_UNAVAILABLE -> 1_011_005_117;
            case CUS_PROVIDER_UNAVAILABLE -> 1_011_005_118;
            case IMP_PROVIDER_UNAVAILABLE -> 1_011_005_119;
            case IDEMPOTENCY_CONFLICT -> 1_011_005_120;
            case IDEMPOTENCY_IN_PROGRESS -> 1_011_005_121;
        };
    }

    private static ErrorDescriptor stale(String reasonCode, String ownerContext) {
        return new ErrorDescriptor(409, "SCOPE_STALE", reasonCode, "REFRESH_OWNER_FACTS", ownerContext);
    }

    private static ErrorDescriptor provider(String reasonCode, String ownerContext) {
        return new ErrorDescriptor(503, "OWNER_PROVIDER_UNAVAILABLE", reasonCode,
                "RETRY_AFTER_OWNER_RECOVERY", ownerContext);
    }

    private static ResponseEntity<CommonResult<CutoverTaskErrorData>> error(
            int httpStatus, int code, String message, CutoverTaskErrorData data) {
        CommonResult<CutoverTaskErrorData> result = CommonResult.error(code, message);
        result.setData(data);
        return ResponseEntity.status(httpStatus).body(result);
    }

    private record ErrorDescriptor(int httpStatus, String category, String reasonCode,
                                   String recoveryAction, String ownerContext) {
    }

    private static long requiredVersion(Long value, String field) {
        require(value != null && value >= 0, field);
        return value;
    }

    private static CutoverCreateContextRespVO createContext(CutoverTaskViews.CreateContextData value) {
        List<CutoverCreateContextRespVO.Candidate> candidates = value.candidates().stream().map(candidate -> {
            var project = candidate.project();
            return new CutoverCreateContextRespVO.Candidate(new CutoverCreateContextRespVO.Project(
                    project.projectId(), project.projectVersion(), project.projectCode(), project.projectName(),
                    project.customerId(), project.customerCode(), project.customerName(), project.departmentId(),
                    project.departmentCode(), project.departmentName(), project.projectScopeVersion()),
                    candidate.devices().stream().map(CutoverTaskResponses.Device::from).toList(),
                    CutoverTaskResponses.CustomerLevel.from(candidate.customerServiceLevel()),
                    CutoverTaskResponses.Readiness.from(candidate.implementationReadiness()),
                    candidate.createAllowed());
        }).toList();
        return new CutoverCreateContextRespVO(candidates, value.selectionRequired(),
                value.configurationChoices().stream().map(CutoverTaskResponses.ConfigurationChoice::from).toList(),
                value.configurationSelectionRequired());
    }

    private static long version(String value) {
        String normalized = header(value, "version header");
        try {
            long version = Long.parseLong(normalized);
            require(version >= 0, "version header");
            return version;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid version header", exception);
        }
    }

    private static String header(String value, String field) {
        require(value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= 128, field);
        return value;
    }

    private static void requireId(Long value) {
        require(value != null && value > 0, "taskId");
    }

    private static void require(boolean valid, String field) {
        if (!valid) throw new IllegalArgumentException("invalid " + field);
    }
}
