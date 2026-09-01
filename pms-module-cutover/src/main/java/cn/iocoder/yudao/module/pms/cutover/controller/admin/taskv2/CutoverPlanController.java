package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.DownloadCutoverPlanDraftResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.SubmitCutoverPlanResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.view.CutoverPlanView;
import tools.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** F-CUT-004七路由REST候选；Task 12前不注册生产Controller Bean。 */
@RequestMapping("/api/v1/pms/cutover-tasks/{taskId}/plan")
@ResponseBody
public class CutoverPlanController {
    private final CutoverPlanApplicationService applicationService;
    private final CutoverPlanQueryService queryService;
    private final CutoverPlanRequestContext requestContext;
    private final CutoverPlanRequestCodec codec;

    public CutoverPlanController(CutoverPlanApplicationService applicationService, CutoverPlanQueryService queryService,
                                 CutoverPlanRequestContext requestContext, CutoverPlanRequestCodec codec) {
        this.applicationService = Objects.requireNonNull(applicationService);
        this.queryService = Objects.requireNonNull(queryService);
        this.requestContext = Objects.requireNonNull(requestContext);
        this.codec = Objects.requireNonNull(codec);
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query-plan')")
    public CommonResult<CutoverPlanView> detail(@PathVariable Long taskId) {
        var trusted = trusted(taskId);
        return success(view(trusted, taskId));
    }

    @PostMapping("/actions/create-draft")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-plan')")
    public CommonResult<CutoverPlanView> createDraft(@PathVariable Long taskId,
            @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId); var request = codec.createDraft(body);
        applicationService.createDraft(new CreateCutoverPlanDraftCommand(trusted.tenantId(), trusted.actorId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), request.editMode(), request.fileFact(),
                request.ownershipConfirmed(), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-plan')")
    public CommonResult<CutoverPlanView> saveDraft(@PathVariable Long taskId,
            @RequestHeader("If-Match") String planVersion, @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId); JsonNode content = codec.draftContent(body);
        applicationService.saveDraft(new SaveCutoverPlanDraftCommand(trusted.tenantId(), trusted.actorId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), codec.version(planVersion, "If-Match"), content,
                codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @PostMapping("/actions/download-draft")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:download-plan')")
    public CommonResult<DownloadCutoverPlanDraftResult> downloadDraft(@PathVariable Long taskId,
            @RequestHeader("If-Match") String planVersion, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) JsonNode body) {
        var trusted = trusted(taskId); codec.empty(body);
        return success(applicationService.downloadDraft(new DownloadCutoverPlanDraftCommand(trusted.tenantId(),
                trusted.actorId(), taskId, codec.version(planVersion, "If-Match"),
                codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId())));
    }

    @PostMapping("/actions/submit")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:submit-plan')")
    public CommonResult<SubmitCutoverPlanResult> submit(@PathVariable Long taskId,
            @RequestHeader("If-Match") String planVersion, @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) JsonNode body) {
        var trusted = trusted(taskId); codec.empty(body);
        return success(applicationService.submit(new SubmitCutoverPlanCommand(trusted.tenantId(), trusted.actorId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), codec.version(planVersion, "If-Match"),
                codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId())));
    }

    @PatchMapping("/support-arrangements/{arrangementId}")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-plan')")
    public CommonResult<CutoverPlanView> patchApprovedContact(@PathVariable Long taskId,
            @PathVariable Long arrangementId, @RequestHeader("If-Match") String planVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId); requireId(arrangementId); var request = codec.patchContact(body);
        applicationService.patchApprovedContact(new PatchApprovedContactCommand(trusted.tenantId(), trusted.actorId(),
                taskId, arrangementId, codec.version(planVersion, "If-Match"), request.personName(), request.phone(),
                request.arrivalTime(), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @PostMapping("/actions/revise")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-plan')")
    public CommonResult<CutoverPlanView> revise(@PathVariable Long taskId,
            @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId); var request = codec.revise(body);
        applicationService.revise(new ReviseCutoverPlanCommand(trusted.tenantId(), trusted.actorId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), request.sourcePlanRevisionId(), request.reason(),
                codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @ExceptionHandler(CutoverPlanApplicationException.class)
    public ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> handleApplication(
            CutoverPlanApplicationException exception) {
        return error(contract(exception));
    }

    @ExceptionHandler(CutoverPlanRequestException.class)
    public ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> handleRequest(
            CutoverPlanRequestException exception) {
        return error(new CutoverPlanContractException(400, 1_011_009_100, exception.getMessage(),
                data("INVALID_REQUEST", exception.reason().name(), "FIX_REQUEST", null,
                        null, null, null)));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> handleMissingHeader(Exception exception) {
        return error(new CutoverPlanContractException(400, 1_011_009_100, exception.getMessage(),
                data("INVALID_REQUEST", "HEADER_REQUIRED_OR_INVALID", "FIX_REQUEST", null,
                        null, null, null)));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> handleValidation(Exception exception) {
        return error(new CutoverPlanContractException(400, 1_011_009_100, exception.getMessage(),
                data("INVALID_REQUEST", "REQUEST_SCHEMA_INVALID", "FIX_REQUEST", null,
                        null, null, null)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> handleForbidden(Exception exception) {
        return error(new CutoverPlanContractException(403, 1_011_009_101, exception.getMessage(),
                data("FUNCTION_OR_SCOPE_DENIED", "PROJECT_OR_TASK_SCOPE_DENIED", "CONTACT_ADMIN", null,
                        null, null, null)));
    }

    private CutoverPlanView view(CutoverPlanRequestContext.TrustedContext trusted, Long taskId) {
        return queryService.detail(trusted.tenantId(), trusted.actorId(), taskId,
                new CutoverPlanQueryService.PlanAccess(trusted.canCreate(), trusted.canSave(), trusted.canDownload()));
    }

    private CutoverPlanRequestContext.TrustedContext trusted(Long taskId) { requireId(taskId); return requestContext.current(); }
    private static void requireId(Long value) { if (value == null || value <= 0) throw new IllegalArgumentException("invalid id"); }

    private static CutoverPlanContractException contract(CutoverPlanApplicationException ex) {
        return switch (ex.code()) {
            case INVALID_REQUEST -> ce(400, ex, "INVALID_REQUEST", "FIX_REQUEST");
            case NOT_FOUND -> ce(404, ex, "NOT_VISIBLE_OR_NOT_FOUND", "REFRESH_AGGREGATE");
            case STATE_CONFLICT -> ce(409, ex, "STATE_CONFLICT", "REFRESH_AGGREGATE");
            case VERSION_CONFLICT, TASK_VERSION_STALE -> ce(409, ex, "VERSION_CONFLICT", "REFRESH_AGGREGATE");
            case PROJECT_SCOPE_STALE, PROJECT_OR_DEVICE_STALE, ASSESSMENT_STALE, CHECKLIST_STALE,
                 CONFIGURATION_OR_TEMPLATE_STALE -> ce(409, ex, "SOURCE_STALE", "REFRESH_OWNER_FACTS");
            case FILE_FACT_STALE -> ce(422, ex, "FILE_INVALID", "FIX_REQUEST");
            case PLAN_SECTION_INCOMPLETE, RISK_MITIGATION_INCOMPLETE,
                 SUPPORT_ARRANGEMENT_INCOMPLETE -> ce(422, ex, "BUSINESS_INCOMPLETE", "FIX_REQUEST");
            case IDEMPOTENCY_CONFLICT -> ce(409, ex, "IDEMPOTENCY_CONFLICT", "START_NEW_INTENT");
            case IDEMPOTENCY_IN_PROGRESS -> ce(409, ex, "IDEMPOTENCY_IN_PROGRESS", "RETRY_SAME_KEY");
            case OWNER_PROVIDER_UNAVAILABLE -> ce(503, ex, "OWNER_PROVIDER_UNAVAILABLE", "RETRY_SAME_KEY");
            case OWNER_DATA_CORRUPTED -> ce(500, ex, "OWNER_DATA_CORRUPTED", "CONTACT_ADMIN");
        };
    }

    private static CutoverPlanContractException ce(int status, CutoverPlanApplicationException ex,
                                                    String category, String recovery) {
        return new CutoverPlanContractException(status, 1_011_009_100 + status, ex.getMessage(),
                data(category, ex.reasonCode(), recovery, ex.ownerContext(), ex.currentTaskVersion(),
                        ex.currentPlanVersion(), ex.currentApprovalVersion()));
    }
    private static CutoverPlanContractException.ErrorData data(String category, String reason, String recovery,
                                                               String owner, Integer currentTaskVersion,
                                                               Integer currentPlanVersion,
                                                               Integer currentApprovalVersion) {
        return new CutoverPlanContractException.ErrorData(category, reason, recovery, owner,
                currentTaskVersion, currentPlanVersion, currentApprovalVersion);
    }
    private static ResponseEntity<CommonResult<CutoverPlanContractException.ErrorData>> error(CutoverPlanContractException ex) {
        CommonResult<CutoverPlanContractException.ErrorData> body = CommonResult.error(ex.errorCode(), ex.getMessage());
        body.setData(ex.errorData()); return ResponseEntity.status(ex.httpStatus()).body(body);
    }
}
