package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.CutoverTaskReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.CutoverCreateContextRespVO;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;
import tools.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** F-CUT-002任务接入REST候选；正式Owner齐备前不注册生产Bean。 */
@RequestMapping("/api/v1/pms/cutover-tasks")
@ResponseBody
public class CutoverTaskController {

    private final CutoverTaskApplicationService applicationService;
    private final CutoverTaskQueryService queryService;
    private final CutoverTaskRequestContext requestContext;
    private final CutoverTaskRequestCodec requestCodec;

    public CutoverTaskController(CutoverTaskApplicationService applicationService,
                                 CutoverTaskQueryService queryService,
                                 CutoverTaskRequestContext requestContext,
                                 CutoverTaskRequestCodec requestCodec) {
        this.applicationService = Objects.requireNonNull(applicationService);
        this.queryService = Objects.requireNonNull(queryService);
        this.requestContext = Objects.requireNonNull(requestContext);
        this.requestCodec = Objects.requireNonNull(requestCodec);
    }

    @PostMapping("/actions/resolve-create-context")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:create')")
    public CommonResult<CutoverCreateContextRespVO> resolveCreateContext(
            @RequestBody JsonNode body) {
        CutoverTaskReqVO.ResolveCreateContext request = requestCodec.resolveCreateContext(body);
        require(request != null && request.serialNumbers() != null, "serialNumbers");
        var trusted = requestContext.current();
        return success(createContext(queryService.resolveCreateContext(
                trusted.tenantId(), trusted.actorId(), request.serialNumbers())));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<PageResult<CutoverTaskViews.Summary>> list(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "taskStatus", required = false) String taskStatus,
            @RequestParam(value = "currentStage", required = false) String currentStage,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        var trusted = requestContext.current();
        return success(queryService.page(trusted.tenantId(), trusted.actorId(), projectId,
                taskStatus, currentStage, pageNo, pageSize));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:create')")
    public CommonResult<CutoverTaskCommandResult> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody JsonNode body) {
        CutoverTaskReqVO.Create request = requestCodec.create(body);
        require(request != null && request.expectedProjectContext() != null
                && request.expectedDeviceScopeWatermark() != null, "create request");
        var trusted = requestContext.current();
        var project = request.expectedProjectContext();
        CutoverProjectContextPort.ProjectContextFact projectFact = new CutoverProjectContextPort.ProjectContextFact(
                trusted.tenantId(), project.projectId(), project.projectVersion(), project.projectCode(),
                project.projectName(), project.customerId(), project.customerCode(), project.customerName(),
                project.officeDepartmentId(), project.officeCode(), project.officeName(),
                requiredVersion(request.expectedProjectScopeVersion(), "expectedProjectScopeVersion"));
        List<CutoverDeviceScopePort.DeviceFact> devices = request.expectedDeviceScopeWatermark().stream()
                .map(item -> new CutoverDeviceScopePort.DeviceFact(item.deviceId(), item.serialNumber(),
                        request.projectId(), requiredVersion(item.projectAssignmentVersion(),
                        "projectAssignmentVersion"))).toList();
        CutoverCustomerLevelPort.CustomerLevelFact customer = new CutoverCustomerLevelPort.CustomerLevelFact(
                request.expectedCustomerServiceLevelStatus(), project.customerId(), project.customerCode(),
                project.customerName(), request.expectedCustomerServiceLevelRevisionId(),
                request.expectedCustomerServiceLevelCode(), requiredVersion(
                request.expectedCustomerServiceLevelFactVersion(), "expectedCustomerServiceLevelFactVersion"),
                request.expectedCustomerServiceLevelEffectiveFrom(), request.expectedCustomerServiceLevelEffectiveTo());
        CutoverReadinessPort.ReadinessFact readiness = new CutoverReadinessPort.ReadinessFact(
                request.expectedReadinessSnapshotId(), requiredVersion(request.expectedReadinessSnapshotVersion(),
                "expectedReadinessSnapshotVersion"), "READY", request.projectId(),
                devices.stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).toList(), null, List.of());
        return success(applicationService.create(new CreateCutoverTaskCommand(trusted.tenantId(), trusted.actorId(),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId(), "SELF_CREATED",
                request.projectId(), request.serialNumbers(), request.configurationCode(), request.taskName(),
                request.background(), request.cutoverType(), request.networkMode(), request.scheduledTime(),
                null, null, null, new CreateCutoverTaskCommand.ExpectedCreateContext(
                projectFact, devices, customer, readiness))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverTaskViews.Detail> detail(@PathVariable("id") Long id) {
        requireId(id);
        var trusted = requestContext.current();
        return success(queryService.detail(trusted.tenantId(), trusted.actorId(), id,
                trusted.canSaveAssessment(), trusted.canSubmitAssessment(), trusted.canSaveChecklist(),
                trusted.canRequestCollection(), trusted.canSubmitChecklist()));
    }

    @PutMapping("/{id}/assessment")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-assessment')")
    public CommonResult<CutoverAssessmentCommandResult> saveAssessment(
            @PathVariable("id") Long id, @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Assessment-If-Match") String assessmentIfMatch,
            @RequestBody JsonNode body) {
        CutoverTaskReqVO.SaveAssessment request = requestCodec.saveAssessment(body);
        requireId(id);
        require(request != null && request.answers() != null, "assessment request");
        var trusted = requestContext.current();
        var answers = request.answers();
        return success(applicationService.saveAssessment(new SaveCutoverAssessmentCommand(
                trusted.tenantId(), trusted.actorId(), id, version(ifMatch), version(assessmentIfMatch),
                new CutoverAssessmentAnswers(answers.businessImportanceLevel(), answers.operationComplexityLevel(),
                        answers.hiddenRiskLevel(), answers.sparePartApplied()), request.manualGrade(),
                trusted.correlationId())));
    }

    @PostMapping("/{id}/assessment/actions/submit")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:submit-assessment')")
    public CommonResult<CutoverTaskCommandResult> submitAssessment(
            @PathVariable("id") Long id, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Assessment-If-Match") String assessmentIfMatch,
            @RequestBody JsonNode body) {
        requireId(id);
        requestCodec.emptyCommand(body);
        var trusted = requestContext.current();
        return success(applicationService.submitAssessment(new SubmitCutoverAssessmentCommand(
                trusted.tenantId(), trusted.actorId(), id, version(ifMatch), version(assessmentIfMatch),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId())));
    }

    @ExceptionHandler(CutoverTaskApplicationException.class)
    public ResponseEntity<CommonResult<CutoverTaskErrorData>> handleApplication(
            CutoverTaskApplicationException exception) {
        ErrorDescriptor descriptor = errorDescriptor(exception.code());
        return error(descriptor.httpStatus(), errorCode(exception.code()), exception.getMessage(),
                descriptor.category(), descriptor.reasonCode(), descriptor.recoveryAction(), descriptor.ownerContext());
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<CommonResult<CutoverTaskErrorData>> handleValidation(Exception exception) {
        return error(400, 1_011_005_100, exception.getMessage(),
                "VALIDATION", "INVALID_REQUEST", "CORRECT_REQUEST", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverTaskErrorData>> handlePermission(AccessDeniedException exception) {
        return error(403, 1_011_005_101, exception.getMessage(),
                "FUNCTION_PERMISSION", "FUNCTION_PERMISSION_DENIED", "REQUEST_PERMISSION", null);
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
            case VERSION_CONFLICT -> new ErrorDescriptor(409, "VERSION_CONFLICT", "TASK_VERSION_STALE",
                    "REFRESH_TASK", null);
            case CONFIGURATION_CONFLICT -> new ErrorDescriptor(409, "CONFIGURATION_CONFLICT",
                    "CONFIGURATION_REVISION_CONFLICT", "RESELECT_CONFIGURATION", null);
            case ACTIVE_DEVICE_CONFLICT -> new ErrorDescriptor(409, "ACTIVE_DEVICE_CONFLICT",
                    "DEVICE_ALREADY_ACTIVE_IN_CUTOVER", "OPEN_EXISTING_TASK", "AST");
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
            case VERSION_CONFLICT -> 1_011_005_105;
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
            int httpStatus, int code, String message, String category, String reasonCode,
            String recoveryAction, String ownerContext) {
        CommonResult<CutoverTaskErrorData> result = CommonResult.error(code, message);
        result.setData(new CutoverTaskErrorData(category, reasonCode, recoveryAction,
                ownerContext, null, null, null));
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
                    candidate.devices(), candidate.customerServiceLevel(), candidate.implementationReadiness(),
                    candidate.createAllowed());
        }).toList();
        return new CutoverCreateContextRespVO(candidates, value.selectionRequired(), value.configurationChoices(),
                value.configurationSelectionRequired());
    }

    private static int version(String value) {
        String normalized = header(value, "version header");
        try {
            int version = Integer.parseInt(normalized);
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
