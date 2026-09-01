package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.closure.CutoverClosureResponse;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.LinkClosureManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.RequestClosureCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SubmitCutoverClosureCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.JsonNode;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** F-CUT-006五路由REST候选；跨模块生产依赖接通前不注册Controller Bean。 */
@RequestMapping("/api/v1/pms/cutover-tasks/{taskId}/closure")
@ResponseBody
public class CutoverClosureController {
    private final CutoverClosureApplicationService applicationService;
    private final CutoverClosureQueryService queryService;
    private final CutoverClosureRequestContext requestContext;
    private final CutoverClosureRequestCodec codec;

    public CutoverClosureController(CutoverClosureApplicationService applicationService,
                                    CutoverClosureQueryService queryService,
                                    CutoverClosureRequestContext requestContext,
                                    CutoverClosureRequestCodec codec) {
        this.applicationService = Objects.requireNonNull(applicationService);
        this.queryService = Objects.requireNonNull(queryService);
        this.requestContext = Objects.requireNonNull(requestContext);
        this.codec = Objects.requireNonNull(codec);
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query-closure')")
    public CommonResult<CutoverClosureResponse> detail(@PathVariable Long taskId) {
        var trusted = trusted(taskId);
        return success(view(trusted, taskId));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-closure')")
    public CommonResult<CutoverClosureResponse> save(@PathVariable Long taskId,
            @RequestHeader(value = "If-Match", required = false) String closureVersion,
            @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId);
        applicationService.save(new SaveCutoverClosureCommand(trusted.tenantId(), trusted.actorId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), codec.optionalVersion(closureVersion, "If-Match"),
                codec.content(body), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @PostMapping("/actions/request-collection")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:request-collection')")
    public CommonResult<CutoverClosureResponse> requestCollection(@PathVariable Long taskId,
            @RequestHeader("If-Match") String closureVersion,
            @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId);
        var current = current(trusted, taskId);
        var request = codec.collection(body);
        applicationService.requestCollection(new RequestClosureCollectionCommand(trusted.tenantId(), trusted.actorId(),
                taskId, codec.version(taskVersion, "X-Task-Version"), requireClosure(current),
                codec.version(closureVersion, "If-Match"), request.deviceId(), request.collectionStage(),
                request.authentication(), request.templateCode(), request.templateVersion(),
                codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @PostMapping("/actions/link-manual-result")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-closure')")
    public CommonResult<CutoverClosureResponse> linkManualResult(@PathVariable Long taskId,
            @RequestHeader("If-Match") String closureVersion,
            @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId);
        var current = current(trusted, taskId);
        var request = codec.manual(body);
        applicationService.linkManualResult(new LinkClosureManualResultCommand(trusted.tenantId(), trusted.actorId(),
                taskId, codec.version(taskVersion, "X-Task-Version"), requireClosure(current),
                codec.version(closureVersion, "If-Match"), request.originalFailedCollectionTaskId(),
                request.deviceId(), request.collectionStage(), request.file(),
                codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @PostMapping("/actions/submit")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:submit-closure')")
    public CommonResult<CutoverClosureResponse> submit(@PathVariable Long taskId,
            @RequestHeader("If-Match") String closureVersion,
            @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId);
        var current = current(trusted, taskId);
        applicationService.submit(new SubmitCutoverClosureCommand(trusted.tenantId(), trusted.actorId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), requireClosure(current),
                codec.version(closureVersion, "If-Match"), codec.finalResult(body),
                codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @ExceptionHandler(CutoverClosureApplicationException.class)
    public ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> handleApplication(
            CutoverClosureApplicationException exception) {
        return error(contract(exception));
    }

    @ExceptionHandler(CutoverClosureRequestException.class)
    public ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> handleRequest(Exception exception) {
        return error(ce(400, exception.getMessage(), "INVALID_REQUEST", "REQUEST_SCHEMA_INVALID", "FIX_REQUEST", null));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> handleMissingHeader(Exception exception) {
        return error(ce(400, exception.getMessage(), "INVALID_REQUEST", "HEADER_REQUIRED_OR_INVALID", "FIX_REQUEST", null));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> handleValidation(Exception exception) {
        return error(ce(400, exception.getMessage(), "INVALID_REQUEST", "REQUEST_SCHEMA_INVALID", "FIX_REQUEST", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> handleForbidden(Exception exception) {
        return error(ce(403, exception.getMessage(), "FUNCTION_OR_SCOPE_DENIED",
                "PROJECT_OR_TASK_SCOPE_DENIED", "CONTACT_ADMIN", null));
    }

    private CutoverClosureResponse view(CutoverClosureRequestContext.TrustedContext trusted, Long taskId) {
        return CutoverClosureResponse.from(current(trusted, taskId));
    }

    private cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView current(
            CutoverClosureRequestContext.TrustedContext trusted, Long taskId) {
        return queryService.detail(trusted.tenantId(), trusted.actorId(), taskId,
                new CutoverClosureQueryService.ClosureAccess(trusted.canSave(), trusted.canRequestCollection(),
                        trusted.canSubmit()));
    }

    private CutoverClosureRequestContext.TrustedContext trusted(Long taskId) {
        requireId(taskId);
        return requestContext.current();
    }

    private static long requireClosure(cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView view) {
        if (view.closureId() == null) throw new CutoverClosureApplicationException(
                CutoverClosureApplicationException.Code.NOT_FOUND, "闭环不存在");
        return view.closureId();
    }

    private static void requireId(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("invalid taskId");
    }

    private static CutoverClosureContractException contract(CutoverClosureApplicationException ex) {
        return switch (ex.code()) {
            case INVALID_REQUEST -> ce(400, ex.getMessage(), "INVALID_REQUEST", "REQUEST_SCHEMA_INVALID", "FIX_REQUEST", null);
            case NOT_FOUND -> ce(404, ex.getMessage(), "NOT_VISIBLE_OR_NOT_FOUND", "TASK_OR_CLOSURE_NOT_VISIBLE", "REFRESH_AGGREGATE", null);
            case STATE_CONFLICT -> ce(409, ex.getMessage(), "STATE_CONFLICT", "CLOSURE_ALREADY_SUBMITTED", "REFRESH_AGGREGATE", null);
            case TASK_VERSION_STALE -> ce(409, ex.getMessage(), "VERSION_CONFLICT", "TASK_VERSION_STALE", "REFRESH_AGGREGATE", null);
            case CLOSURE_VERSION_STALE -> ce(409, ex.getMessage(), "VERSION_CONFLICT", "CLOSURE_VERSION_STALE", "REFRESH_AGGREGATE", null);
            case SOURCE_STALE -> ce(409, ex.getMessage(), "SOURCE_STALE", "APPROVAL_OR_PLAN_STALE", "REFRESH_OWNER_FACTS", null);
            case FILE_INVALID -> ce(422, ex.getMessage(), "FILE_INVALID", "FILE_FACT_INVALID", "FIX_REQUEST", "PLT");
            case COLLECTION_INVALID -> ce(422, ex.getMessage(), "COLLECTION_INVALID", "COLLECTION_EVIDENCE_MISMATCH", "FIX_REQUEST", "INT-12");
            case BUSINESS_INCOMPLETE -> ce(422, ex.getMessage(), "BUSINESS_INCOMPLETE", "CLOSURE_RESULT_INCOMPLETE", "FIX_REQUEST", null);
            case IDEMPOTENCY_CONFLICT -> ce(409, ex.getMessage(), "IDEMPOTENCY_CONFLICT", "IDEMPOTENCY_PAYLOAD_CONFLICT", "START_NEW_INTENT", null);
            case IDEMPOTENCY_IN_PROGRESS -> ce(409, ex.getMessage(), "IDEMPOTENCY_IN_PROGRESS", "IDEMPOTENCY_OPERATION_IN_PROGRESS", "RETRY_SAME_KEY", null);
            case OWNER_PROVIDER_UNAVAILABLE -> ce(503, ex.getMessage(), "OWNER_PROVIDER_UNAVAILABLE", "PROJECT_SCOPE_PROVIDER_UNAVAILABLE", "RETRY_SAME_KEY", null);
            case OWNER_DATA_CORRUPTED -> ce(500, ex.getMessage(), "OWNER_DATA_CORRUPTED", "OWNER_FACT_CORRUPTED", "CONTACT_ADMIN", null);
        };
    }

    private static CutoverClosureContractException ce(int status, String message, String category,
                                                       String reason, String recovery, String owner) {
        return new CutoverClosureContractException(status, 1_011_011_100 + status, message,
                new CutoverClosureContractException.ErrorData(category, reason, recovery, owner, null, null));
    }

    private static ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> error(
            CutoverClosureContractException ex) {
        CommonResult<CutoverClosureContractException.ErrorData> body = CommonResult.error(ex.errorCode(), ex.getMessage());
        body.setData(ex.errorData());
        return ResponseEntity.status(ex.httpStatus()).body(body);
    }
}
