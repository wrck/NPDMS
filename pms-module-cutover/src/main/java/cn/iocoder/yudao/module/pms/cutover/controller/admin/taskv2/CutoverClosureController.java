package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import org.springframework.http.HttpHeaders;
import cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext;
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
            @RequestHeader HttpHeaders headers,
            @RequestBody JsonNode body) {
        String closureVersion = CutoverRequestHeaders.value(headers, "If-Match");
        String taskVersion = CutoverRequestHeaders.value(headers, "X-Task-Version");
        String idempotencyKey = CutoverRequestHeaders.value(headers, "Idempotency-Key");

        var trusted = trusted(taskId);
        applicationService.save(new SaveCutoverClosureCommand(trusted.tenantId(), trusted.actorId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), codec.optionalVersion(closureVersion, "If-Match"),
                codec.content(body), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(view(trusted, taskId));
    }

    @PostMapping("/actions/request-collection")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:request-collection')")
    public CommonResult<CutoverClosureResponse> requestCollection(@PathVariable Long taskId,
            @RequestHeader HttpHeaders headers,
            @RequestBody JsonNode body) {
        String closureVersion = CutoverRequestHeaders.value(headers, "If-Match");
        String taskVersion = CutoverRequestHeaders.value(headers, "X-Task-Version");
        String idempotencyKey = CutoverRequestHeaders.value(headers, "Idempotency-Key");

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
            @RequestHeader HttpHeaders headers,
            @RequestBody JsonNode body) {
        String closureVersion = CutoverRequestHeaders.value(headers, "If-Match");
        String taskVersion = CutoverRequestHeaders.value(headers, "X-Task-Version");
        String idempotencyKey = CutoverRequestHeaders.value(headers, "Idempotency-Key");

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
            @RequestHeader HttpHeaders headers,
            @RequestBody JsonNode body) {
        String closureVersion = CutoverRequestHeaders.value(headers, "If-Match");
        String taskVersion = CutoverRequestHeaders.value(headers, "X-Task-Version");
        String idempotencyKey = CutoverRequestHeaders.value(headers, "Idempotency-Key");

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
        return error(ce(400, exception.getMessage(), CutoverClosureContractException.ErrorData.builder()
                .category("INVALID_REQUEST")
                .reasonCode("REQUEST_SCHEMA_INVALID")
                .recoveryAction("FIX_REQUEST")
                .ownerContext(null)
                .currentTaskVersion(null)
                .currentClosureVersion(null)
                .build()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> handleMissingHeader(Exception exception) {
        return error(ce(400, exception.getMessage(), CutoverClosureContractException.ErrorData.builder()
                .category("INVALID_REQUEST")
                .reasonCode("HEADER_REQUIRED_OR_INVALID")
                .recoveryAction("FIX_REQUEST")
                .ownerContext(null)
                .currentTaskVersion(null)
                .currentClosureVersion(null)
                .build()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> handleValidation(Exception exception) {
        return error(ce(400, exception.getMessage(), CutoverClosureContractException.ErrorData.builder()
                .category("INVALID_REQUEST")
                .reasonCode("REQUEST_SCHEMA_INVALID")
                .recoveryAction("FIX_REQUEST")
                .ownerContext(null)
                .currentTaskVersion(null)
                .currentClosureVersion(null)
                .build()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> handleForbidden(Exception exception) {
        return error(ce(403, exception.getMessage(), CutoverClosureContractException.ErrorData.builder()
                .category("FUNCTION_OR_SCOPE_DENIED")
                .reasonCode("PROJECT_OR_TASK_SCOPE_DENIED")
                .recoveryAction("CONTACT_ADMIN")
                .ownerContext(null)
                .currentTaskVersion(null)
                .currentClosureVersion(null)
                .build()));
    }

    private CutoverClosureResponse view(CutoverClosureContext trusted, Long taskId) {
        return CutoverClosureResponse.from(current(trusted, taskId));
    }

    private cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView current(
            CutoverClosureContext trusted, Long taskId) {
        return queryService.detail(trusted, taskId);
    }

    private CutoverClosureContext trusted(Long taskId) {
        requireId(taskId);
        return requestContext.current();
    }

    private static long requireClosure(cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView view) {
        if (view.closureId() == null) throw new CutoverClosureApplicationException(
                CutoverClosureApplicationException.Code.NOT_FOUND,
                CutoverClosureApplicationException.Reason.TASK_OR_CLOSURE_NOT_VISIBLE,
                null, view.taskVersion(), view.closureVersion(), "闭环不存在");
        return view.closureId();
    }

    private static void requireId(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("invalid taskId");
    }

    private static CutoverClosureContractException contract(CutoverClosureApplicationException ex) {
        String reason = ex.reason().name();
        CutoverClosureContractException base = switch (ex.code()) {
            case INVALID_REQUEST -> ce(400, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("INVALID_REQUEST")
                    .reasonCode(reason)
                    .recoveryAction("FIX_REQUEST")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case FUNCTION_OR_SCOPE_DENIED -> ce(403, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("FUNCTION_OR_SCOPE_DENIED")
                    .reasonCode(reason)
                    .recoveryAction("CONTACT_ADMIN")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case NOT_FOUND -> ce(404, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("NOT_VISIBLE_OR_NOT_FOUND")
                    .reasonCode(reason)
                    .recoveryAction("REFRESH_AGGREGATE")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case STATE_CONFLICT -> ce(409, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("STATE_CONFLICT")
                    .reasonCode(reason)
                    .recoveryAction("REFRESH_AGGREGATE")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case TASK_VERSION_STALE, CLOSURE_VERSION_STALE -> ce(409, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("VERSION_CONFLICT")
                    .reasonCode(reason)
                    .recoveryAction("REFRESH_AGGREGATE")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case SOURCE_STALE -> ce(409, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("SOURCE_STALE")
                    .reasonCode(reason)
                    .recoveryAction("REFRESH_OWNER_FACTS")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case FILE_INVALID -> ce(422, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("FILE_INVALID")
                    .reasonCode(reason)
                    .recoveryAction("FIX_REQUEST")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case COLLECTION_INVALID -> ce(422, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("COLLECTION_INVALID")
                    .reasonCode(reason)
                    .recoveryAction("FIX_REQUEST")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case BUSINESS_INCOMPLETE -> ce(422, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("BUSINESS_INCOMPLETE")
                    .reasonCode(reason)
                    .recoveryAction("FIX_REQUEST")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case IDEMPOTENCY_CONFLICT -> ce(409, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("IDEMPOTENCY_CONFLICT")
                    .reasonCode(reason)
                    .recoveryAction("START_NEW_INTENT")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case IDEMPOTENCY_IN_PROGRESS -> ce(409, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("IDEMPOTENCY_IN_PROGRESS")
                    .reasonCode(reason)
                    .recoveryAction("RETRY_SAME_KEY")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case OWNER_PROVIDER_UNAVAILABLE -> ce(503, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("OWNER_PROVIDER_UNAVAILABLE")
                    .reasonCode(reason)
                    .recoveryAction("RETRY_SAME_KEY")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
            case OWNER_DATA_CORRUPTED -> ce(500, ex.getMessage(), CutoverClosureContractException.ErrorData.builder()
                    .category("OWNER_DATA_CORRUPTED")
                    .reasonCode(reason)
                    .recoveryAction("CONTACT_ADMIN")
                    .ownerContext(ex.ownerContext())
                    .currentTaskVersion(null)
                    .currentClosureVersion(null)
                    .build());
        };
        return new CutoverClosureContractException(base.httpStatus(), base.errorCode(), base.getMessage(),
                CutoverClosureContractException.ErrorData.builder()
                        .category(base.errorData().category())
                        .reasonCode(reason)
                        .recoveryAction(base.errorData().recoveryAction())
                        .ownerContext(ex.ownerContext())
                        .currentTaskVersion(ex.currentTaskVersion())
                        .currentClosureVersion(ex.currentClosureVersion())
                        .build());
    }

    private static CutoverClosureContractException ce(int status, String message,
                                                       CutoverClosureContractException.ErrorData data) {
        return new CutoverClosureContractException(status, 1_011_011_100 + status, message, data);
    }

    private static ResponseEntity<CommonResult<CutoverClosureContractException.ErrorData>> error(
            CutoverClosureContractException ex) {
        CommonResult<CutoverClosureContractException.ErrorData> body = CommonResult.error(ex.errorCode(), ex.getMessage());
        body.setData(ex.errorData());
        return ResponseEntity.status(ex.httpStatus()).body(body);
    }
}
