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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    public CutoverTaskController(CutoverTaskApplicationService applicationService,
                                 CutoverTaskQueryService queryService,
                                 CutoverTaskRequestContext requestContext) {
        this.applicationService = Objects.requireNonNull(applicationService);
        this.queryService = Objects.requireNonNull(queryService);
        this.requestContext = Objects.requireNonNull(requestContext);
    }

    @PostMapping("/actions/resolve-create-context")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:create')")
    public CommonResult<CutoverCreateContextRespVO> resolveCreateContext(
            @RequestBody CutoverTaskReqVO.ResolveCreateContext request) {
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
            @RequestBody CutoverTaskReqVO.Create request) {
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
            @RequestBody CutoverTaskReqVO.SaveAssessment request) {
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
            @RequestHeader("Assessment-If-Match") String assessmentIfMatch) {
        requireId(id);
        var trusted = requestContext.current();
        return success(applicationService.submitAssessment(new SubmitCutoverAssessmentCommand(
                trusted.tenantId(), trusted.actorId(), id, version(ifMatch), version(assessmentIfMatch),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId())));
    }

    @ExceptionHandler(CutoverTaskApplicationException.class)
    public ResponseEntity<CommonResult<Void>> handleApplication(CutoverTaskApplicationException exception) {
        int status = switch (exception.code()) {
            case NOT_FOUND -> 404;
            case DATA_SCOPE_FORBIDDEN -> 403;
            case STATE_CONFLICT, VERSION_CONFLICT, ACTIVE_DEVICE_CONFLICT,
                    IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS -> 409;
            case READINESS_NOT_READY, CUSTOMER_CONTEXT_INVALID -> 422;
            case OWNER_PROVIDER_UNAVAILABLE -> 503;
            case INVALID_REQUEST -> 400;
        };
        return ResponseEntity.status(status).body(CommonResult.error(
                1_011_005_100 + exception.code().ordinal(), exception.getMessage()));
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
