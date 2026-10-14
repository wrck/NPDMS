package cn.iocoder.yudao.module.pms.cutover.controller.business.approval;

import cn.iocoder.yudao.module.pms.cutover.controller.business.support.CutoverRequestHeaders;

import cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext;
import org.springframework.http.HttpHeaders;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.controller.business.approval.vo.CutoverApprovalRequests;
import cn.iocoder.yudao.module.pms.cutover.controller.business.approval.vo.CutoverApprovalResponses;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.ApproveCutoverApprovalCommand;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.ReassignCutoverApprovalCommand;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.RejectCutoverApprovalCommand;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;
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

/** 已接入平台 BPM 的割接审批入口。 */
@RequestMapping("/api/v1/pms")
@RestController
public class CutoverApprovalController {
    private final CutoverApprovalApplicationService applicationService;
    private final CutoverApprovalQueryService queryService;
    private final CutoverApprovalRequestContext requestContext;
    private final CutoverApprovalRequestCodec codec;

    public CutoverApprovalController(CutoverApprovalApplicationService applicationService,
            CutoverApprovalQueryService queryService, CutoverApprovalRequestContext requestContext,
            CutoverApprovalRequestCodec codec) {
        this.applicationService = Objects.requireNonNull(applicationService);
        this.queryService = Objects.requireNonNull(queryService);
        this.requestContext = Objects.requireNonNull(requestContext);
        this.codec = Objects.requireNonNull(codec);
    }

    @GetMapping("/cutover-tasks/{taskId}/approval")
    @PreAuthorize("@ss.hasAnyPermissions('pms:cutover-task:query-approval','pms:cutover-task:reassign-approval')")
    public CommonResult<CutoverApprovalResponses.View> detail(@PathVariable Long taskId) {
        var trusted = trusted(taskId);
        return success(CutoverApprovalResponses.view(queryDetail(trusted, taskId)));
    }

    @GetMapping("/cutover-approvals/{approvalId}")
    @PreAuthorize("@ss.hasAnyPermissions('pms:cutover-task:query-approval','pms:cutover-task:reassign-approval')")
    public CommonResult<CutoverApprovalResponses.View> instance(@PathVariable Long approvalId) {
        var trusted = requestContext.current();
        return success(CutoverApprovalResponses.view(queryService.detailByInstance(trusted, approvalId)));
    }

    @GetMapping("/cutover-approvals/todos")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query-approval')")
    public CommonResult<CutoverApprovalResponses.TodoPage> myTodos(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        var trusted = requestContext.current();
        return success(CutoverApprovalResponses.todos(queryService.myTodos(trusted, pagination(pageNo, pageSize))));
    }

    @GetMapping("/cutover-approvals/reassignment-candidates")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:reassign-approval')")
    public CommonResult<CutoverApprovalResponses.ReassignmentCandidatePage> reassignmentCandidates(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        var trusted = requestContext.current();
        return success(CutoverApprovalResponses.candidates(queryService.reassignmentCandidates(trusted,
                pagination(pageNo, pageSize))));
    }

    @PostMapping("/cutover-approvals/{approvalId}/actions/approve")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:approve')")
    public CommonResult<CutoverApprovalResponses.View> approve(@PathVariable Long approvalId,
            @RequestHeader HttpHeaders headers, @RequestBody JsonNode body) {
        String approvalVersion = CutoverRequestHeaders.value(headers, "If-Match");
        String taskVersion = CutoverRequestHeaders.value(headers, "X-Task-Version");
        String idempotencyKey = CutoverRequestHeaders.value(headers, "Idempotency-Key");
        var trusted = requestContext.current();
        Long taskId = taskForApproval(trusted, approvalId); var request = codec.approve(body);
        var result = applicationService.approve(new ApproveCutoverApprovalCommand(trusted.tenantId(), taskId, approvalId,
                codec.version(taskVersion, "X-Task-Version"), codec.version(approvalVersion, "If-Match"),
                request.reviewItems(), request.assessmentReview(),
                request.feedback(), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(CutoverApprovalResponses.view(queryService.decisionResponse(trusted, result)));
    }

    @PostMapping("/cutover-approvals/{approvalId}/actions/reject")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:approve')")
    public CommonResult<CutoverApprovalResponses.View> reject(@PathVariable Long approvalId,
            @RequestHeader HttpHeaders headers, @RequestBody JsonNode body) {
        String approvalVersion = CutoverRequestHeaders.value(headers, "If-Match");
        String taskVersion = CutoverRequestHeaders.value(headers, "X-Task-Version");
        String idempotencyKey = CutoverRequestHeaders.value(headers, "Idempotency-Key");
        var trusted = requestContext.current();
        Long taskId = taskForApproval(trusted, approvalId); var request = codec.reject(body);
        var result = applicationService.reject(new RejectCutoverApprovalCommand(trusted.tenantId(), taskId, approvalId,
                codec.version(taskVersion, "X-Task-Version"), codec.version(approvalVersion, "If-Match"),
                request.reviewItems(), request.assessmentReview(),
                request.feedback(), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(CutoverApprovalResponses.view(queryService.decisionResponse(trusted, result)));
    }

    @PostMapping("/cutover-approvals/{approvalId}/actions/reassign")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:reassign-approval')")
    public CommonResult<CutoverApprovalResponses.Reassignment> reassign(@PathVariable Long approvalId,
            @RequestHeader HttpHeaders headers, @RequestBody JsonNode body) {
        String approvalVersion = CutoverRequestHeaders.value(headers, "If-Match");
        String idempotencyKey = CutoverRequestHeaders.value(headers, "Idempotency-Key");
        var trusted = requestContext.current();
        Long taskId = taskForApproval(trusted, approvalId); var context = currentReassignment(trusted, taskId);
        var current = context.view(); var request = codec.reassign(body);
        var result = applicationService.reassign(new ReassignCutoverApprovalCommand(trusted.tenantId(), taskId,
                context.taskVersion(), approvalId,
                codec.version(approvalVersion, "If-Match"), request.nodeNo(), request.newApproverUserId(),
                request.reason(), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(CutoverApprovalResponses.reassignment(result));
    }

    @ExceptionHandler(CutoverApprovalContractException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleContract(
            CutoverApprovalContractException exception) { return error(exception); }

    @ExceptionHandler(CutoverApprovalApplicationException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleApplication(
            CutoverApprovalApplicationException exception) { return error(contract(exception)); }

    @ExceptionHandler(CutoverApprovalOwnerFactException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleOwner(
            CutoverApprovalOwnerFactException exception) {
        if (exception.code() == CutoverApprovalOwnerFactException.Code.PROVIDER_UNAVAILABLE
                && exception.ownerContext() != null) {
            return error(ce(503, CutoverApprovalContractException.ErrorData.builder()
                    .category("OWNER_PROVIDER_UNAVAILABLE")
                    .reasonCode("PROJ_OR_SYSTEM_PROVIDER_UNAVAILABLE")
                    .recoveryAction("RETRY_LATER")
                    .ownerContext(exception.ownerContext())
                    .currentApprovalVersion(null)
                    .currentTaskVersion(null)
                    .build()));
        }
        return error(ce(500, CutoverApprovalContractException.ErrorData.builder()
                .category("OWNER_DATA_CORRUPTED")
                .reasonCode("OWNER_FACT_CORRUPTED")
                .recoveryAction("CONTACT_SUPPORT")
                .ownerContext(null)
                .currentApprovalVersion(null)
                .currentTaskVersion(null)
                .build()));
    }

    @ExceptionHandler(CutoverApprovalRequestException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleRequest(
            CutoverApprovalRequestException exception) {
        return error(ce(400, CutoverApprovalContractException.ErrorData.builder()
                .category("INVALID_REQUEST")
                .reasonCode(exception.reason().name())
                .recoveryAction("FIX_REQUEST")
                .ownerContext(null)
                .currentApprovalVersion(null)
                .currentTaskVersion(null)
                .build()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleMissingHeader(Exception exception) {
        return error(ce(400, CutoverApprovalContractException.ErrorData.builder()
                .category("INVALID_REQUEST")
                .reasonCode("HEADER_REQUIRED_OR_INVALID")
                .recoveryAction("FIX_REQUEST")
                .ownerContext(null)
                .currentApprovalVersion(null)
                .currentTaskVersion(null)
                .build()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleValidation(Exception exception) {
        return error(ce(400, CutoverApprovalContractException.ErrorData.builder()
                .category("INVALID_REQUEST")
                .reasonCode("REQUEST_SCHEMA_INVALID")
                .recoveryAction("FIX_REQUEST")
                .ownerContext(null)
                .currentApprovalVersion(null)
                .currentTaskVersion(null)
                .build()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleForbidden(Exception exception) {
        return error(ce(403, CutoverApprovalContractException.ErrorData.builder()
                .category("FUNCTION_OR_SCOPE_DENIED")
                .reasonCode("APPROVAL_PERMISSION_OR_PROJECT_SCOPE_DENIED")
                .recoveryAction("REQUEST_ACCESS")
                .ownerContext(null)
                .currentApprovalVersion(null)
                .currentTaskVersion(null)
                .build()));
    }

    private CutoverApprovalViews.ApprovalView queryDetail(CutoverApprovalContext trusted,
                                                           long taskId) {
        try {
            return queryService.detail(trusted, taskId);
        } catch (CutoverApprovalApplicationException ex) {
            if (ex.code() == CutoverApprovalApplicationException.Code.STATE_CONFLICT) {
                throw ce(404, CutoverApprovalContractException.ErrorData.builder()
                        .category("NOT_VISIBLE_OR_NOT_FOUND")
                        .reasonCode("APPROVAL_NOT_VISIBLE")
                        .recoveryAction("REQUEST_ACCESS")
                        .ownerContext(null)
                        .currentApprovalVersion(null)
                        .currentTaskVersion(null)
                        .build());
            }
            throw ex;
        }
    }

    private CutoverApprovalQueryService.ReassignmentCommandContext currentReassignment(
            CutoverApprovalContext trusted, long taskId) {
        try {
            return queryService.reassignmentCommandContext(trusted, taskId);
        } catch (CutoverApprovalApplicationException ex) {
            if (ex.code() != CutoverApprovalApplicationException.Code.STATE_CONFLICT) throw ex;
            throw ce(404, CutoverApprovalContractException.ErrorData.builder()
                    .category("NOT_VISIBLE_OR_NOT_FOUND")
                    .reasonCode("APPROVAL_NOT_VISIBLE")
                    .recoveryAction("REQUEST_ACCESS")
                    .ownerContext(null)
                    .currentApprovalVersion(null)
                    .currentTaskVersion(null)
                    .build());
        }
    }

    private CutoverApprovalContext trusted(Long taskId) {
        if (taskId == null || taskId <= 0) throw new IllegalArgumentException("invalid taskId");
        return requestContext.current();
    }

    private Long taskForApproval(CutoverApprovalContext context, Long approvalId) {
        if (approvalId == null || approvalId <= 0) throw new IllegalArgumentException("invalid approvalId");
        return switch (queryService.detailByInstance(context, approvalId)) {
            case CutoverApprovalViews.ApprovalDetail value -> value.taskId();
            case CutoverApprovalViews.ApprovalFinalResult value -> value.taskId();
            case CutoverApprovalViews.ApprovalReassignmentView value -> value.taskId();
        };
    }

    private static int requirePage(Integer value, boolean size) {
        if (value == null || value <= 0 || (size && value > 100)) throw new IllegalArgumentException("invalid page");
        return value;
    }

    private static PageParam pagination(Integer pageNo, Integer pageSize) {
        var page = new PageParam();
        page.setPageNo(requirePage(pageNo, false)); page.setPageSize(requirePage(pageSize, true));
        return page;
    }

    private static CutoverApprovalContractException contract(CutoverApprovalApplicationException ex) {
        return switch (ex.code()) {
            case INVALID_REQUEST -> ce(400, CutoverApprovalContractException.ErrorData.builder()
                    .category("INVALID_REQUEST")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("FIX_REQUEST")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
            case STATE_CONFLICT -> ce(409, CutoverApprovalContractException.ErrorData.builder()
                    .category("STATE_CONFLICT")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("REFRESH_APPROVAL")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
            case VERSION_CONFLICT -> ce(409, CutoverApprovalContractException.ErrorData.builder()
                    .category("VERSION_CONFLICT")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("REFRESH_APPROVAL")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
            case SOURCE_STALE -> ce(409, CutoverApprovalContractException.ErrorData.builder()
                    .category("SOURCE_STALE")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("REFRESH_SOURCES")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
            case BUSINESS_INCOMPLETE -> ce(422, CutoverApprovalContractException.ErrorData.builder()
                    .category("BUSINESS_INCOMPLETE")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("FIX_REQUEST")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
            case IDEMPOTENCY_CONFLICT -> ce(409, CutoverApprovalContractException.ErrorData.builder()
                    .category("IDEMPOTENCY_CONFLICT")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("CONTACT_ADMIN")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
            case IDEMPOTENCY_IN_PROGRESS -> ce(409, CutoverApprovalContractException.ErrorData.builder()
                    .category("IDEMPOTENCY_IN_PROGRESS")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("RETRY_SAME_KEY")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
            case OWNER_PROVIDER_UNAVAILABLE -> ce(503, CutoverApprovalContractException.ErrorData.builder()
                    .category("OWNER_PROVIDER_UNAVAILABLE")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("RETRY_LATER")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
            case OWNER_DATA_CORRUPTED -> ce(500, CutoverApprovalContractException.ErrorData.builder()
                    .category("OWNER_DATA_CORRUPTED")
                    .reasonCode(ex.reasonCode())
                    .recoveryAction("CONTACT_SUPPORT")
                    .ownerContext(ex.ownerContext())
                    .currentApprovalVersion(ex.currentApprovalVersion())
                    .currentTaskVersion(ex.currentTaskVersion())
                    .build());
        };
    }

    private static CutoverApprovalContractException ce(int status, CutoverApprovalContractException.ErrorData data) {
        return new CutoverApprovalContractException(status, 1_011_010_000 + status, data.reasonCode(), data);
    }

    private static ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> error(
            CutoverApprovalContractException ex) {
        CommonResult<CutoverApprovalContractException.ErrorData> body = CommonResult.error(ex.errorCode(), ex.getMessage());
        body.setData(ex.errorData());
        return ResponseEntity.status(ex.httpStatus()).body(body);
    }
}
