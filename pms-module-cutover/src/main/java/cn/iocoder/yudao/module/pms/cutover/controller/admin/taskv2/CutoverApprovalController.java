package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.approval.CutoverApprovalRequests;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.approval.CutoverApprovalResponses;
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

/** F-CUT-005六路由REST候选；Task 14前不注册生产Controller Bean。 */
@RequestMapping("/api/v1/pms")
@ResponseBody
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

    @GetMapping("/cutover-approvals/todos")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query-approval')")
    public CommonResult<CutoverApprovalResponses.TodoPage> myTodos(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        var trusted = requestContext.current();
        return success(CutoverApprovalResponses.todos(queryService.myTodos(trusted.tenantId(), trusted.actorId(),
                requirePage(pageNo, false), requirePage(pageSize, true))));
    }

    @GetMapping("/cutover-approvals/reassignment-candidates")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:reassign-approval')")
    public CommonResult<CutoverApprovalResponses.ReassignmentCandidatePage> reassignmentCandidates(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        var trusted = requestContext.current();
        return success(CutoverApprovalResponses.candidates(queryService.reassignmentCandidates(trusted.tenantId(),
                requirePage(pageNo, false), requirePage(pageSize, true))));
    }

    @PostMapping("/cutover-tasks/{taskId}/approval-actions/approve")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:approve')")
    public CommonResult<CutoverApprovalResponses.View> approve(@PathVariable Long taskId,
            @RequestHeader("If-Match") String approvalVersion,
            @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId); var request = codec.approve(body);
        var result = applicationService.approve(new ApproveCutoverApprovalCommand(trusted.tenantId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), codec.version(approvalVersion, "If-Match"),
                request.reviewItems(), request.assessmentReview(),
                request.feedback(), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(CutoverApprovalResponses.view(queryService.decisionResponse(trusted.tenantId(), taskId,
                result.approvalInstanceId(), result.decidedNodeNo(), trusted.actorId())));
    }

    @PostMapping("/cutover-tasks/{taskId}/approval-actions/reject")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:approve')")
    public CommonResult<CutoverApprovalResponses.View> reject(@PathVariable Long taskId,
            @RequestHeader("If-Match") String approvalVersion,
            @RequestHeader("X-Task-Version") String taskVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId); var request = codec.reject(body);
        var result = applicationService.reject(new RejectCutoverApprovalCommand(trusted.tenantId(), taskId,
                codec.version(taskVersion, "X-Task-Version"), codec.version(approvalVersion, "If-Match"),
                request.reviewItems(), request.assessmentReview(),
                request.feedback(), codec.header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()));
        return success(CutoverApprovalResponses.view(queryService.decisionResponse(trusted.tenantId(), taskId,
                result.approvalInstanceId(), result.decidedNodeNo(), trusted.actorId())));
    }

    @PostMapping("/cutover-tasks/{taskId}/approval-actions/reassign")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:reassign-approval')")
    public CommonResult<CutoverApprovalResponses.Reassignment> reassign(@PathVariable Long taskId,
            @RequestHeader("If-Match") String approvalVersion,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody JsonNode body) {
        var trusted = trusted(taskId); var context = currentReassignment(trusted, taskId);
        var current = context.view(); var request = codec.reassign(body);
        var result = applicationService.reassign(new ReassignCutoverApprovalCommand(trusted.tenantId(), taskId,
                context.taskVersion(), current.approvalInstanceId(),
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
            return error(ce(503, "OWNER_PROVIDER_UNAVAILABLE", "PROJ_OR_SYSTEM_PROVIDER_UNAVAILABLE",
                    "RETRY_LATER", exception.ownerContext()));
        }
        return error(ce(500, "OWNER_DATA_CORRUPTED", "OWNER_FACT_CORRUPTED", "CONTACT_SUPPORT", null));
    }

    @ExceptionHandler(CutoverApprovalRequestException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleRequest(
            CutoverApprovalRequestException exception) {
        return error(ce(400, "INVALID_REQUEST", exception.reason().name(), "FIX_REQUEST", null));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleMissingHeader(Exception exception) {
        return error(ce(400, "INVALID_REQUEST", "HEADER_REQUIRED_OR_INVALID", "FIX_REQUEST", null));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleValidation(Exception exception) {
        return error(ce(400, "INVALID_REQUEST", "REQUEST_SCHEMA_INVALID", "FIX_REQUEST", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> handleForbidden(Exception exception) {
        return error(ce(403, "FUNCTION_OR_SCOPE_DENIED", "APPROVAL_PERMISSION_OR_PROJECT_SCOPE_DENIED",
                "REQUEST_ACCESS", null));
    }

    private CutoverApprovalViews.ApprovalView queryDetail(CutoverApprovalRequestContext.TrustedContext trusted,
                                                           long taskId) {
        try {
            return queryService.detail(trusted.tenantId(), taskId, trusted.actorId(), trusted.canQuery(),
                    trusted.canReassign());
        } catch (CutoverApprovalApplicationException ex) {
            if (ex.code() == CutoverApprovalApplicationException.Code.STATE_CONFLICT) {
                throw ce(404, "NOT_VISIBLE_OR_NOT_FOUND", "APPROVAL_NOT_VISIBLE", "REQUEST_ACCESS", null);
            }
            throw ex;
        }
    }

    private CutoverApprovalQueryService.ReassignmentCommandContext currentReassignment(
            CutoverApprovalRequestContext.TrustedContext trusted, long taskId) {
        try {
            return queryService.reassignmentCommandContext(trusted.tenantId(), taskId, trusted.actorId());
        } catch (CutoverApprovalApplicationException ex) {
            if (ex.code() != CutoverApprovalApplicationException.Code.STATE_CONFLICT) throw ex;
            throw ce(404, "NOT_VISIBLE_OR_NOT_FOUND", "APPROVAL_NOT_VISIBLE", "REQUEST_ACCESS", null);
        }
    }

    private CutoverApprovalRequestContext.TrustedContext trusted(Long taskId) {
        if (taskId == null || taskId <= 0) throw new IllegalArgumentException("invalid taskId");
        return requestContext.current();
    }

    private static int requirePage(Integer value, boolean size) {
        if (value == null || value <= 0 || (size && value > 100)) throw new IllegalArgumentException("invalid page");
        return value;
    }

    private static CutoverApprovalContractException contract(CutoverApprovalApplicationException ex) {
        return switch (ex.code()) {
            case INVALID_REQUEST -> ce(400, "INVALID_REQUEST", ex.reasonCode(), "FIX_REQUEST", ex.ownerContext(), ex);
            case STATE_CONFLICT -> ce(409, "STATE_CONFLICT", ex.reasonCode(), "REFRESH_APPROVAL", ex.ownerContext(), ex);
            case VERSION_CONFLICT -> ce(409, "VERSION_CONFLICT", ex.reasonCode(), "REFRESH_APPROVAL", ex.ownerContext(), ex);
            case SOURCE_STALE -> ce(409, "SOURCE_STALE", ex.reasonCode(), "REFRESH_SOURCES", ex.ownerContext(), ex);
            case BUSINESS_INCOMPLETE -> ce(422, "BUSINESS_INCOMPLETE", ex.reasonCode(), "FIX_REQUEST", ex.ownerContext(), ex);
            case IDEMPOTENCY_CONFLICT -> ce(409, "IDEMPOTENCY_CONFLICT", ex.reasonCode(), "CONTACT_ADMIN", ex.ownerContext(), ex);
            case IDEMPOTENCY_IN_PROGRESS -> ce(409, "IDEMPOTENCY_IN_PROGRESS", ex.reasonCode(), "RETRY_SAME_KEY", ex.ownerContext(), ex);
            case OWNER_PROVIDER_UNAVAILABLE -> ce(503, "OWNER_PROVIDER_UNAVAILABLE", ex.reasonCode(), "RETRY_LATER", ex.ownerContext(), ex);
            case OWNER_DATA_CORRUPTED -> ce(500, "OWNER_DATA_CORRUPTED", ex.reasonCode(), "CONTACT_SUPPORT", ex.ownerContext(), ex);
        };
    }

    private static CutoverApprovalContractException ce(int status, String category, String reason,
                                                         String recovery, String owner) {
        return new CutoverApprovalContractException(status, 1_011_010_000 + status, reason,
                new CutoverApprovalContractException.ErrorData(category, reason, recovery, owner, null, null));
    }

    private static CutoverApprovalContractException ce(int status, String category, String reason,
            String recovery, String owner, CutoverApprovalApplicationException ex) {
        return new CutoverApprovalContractException(status, 1_011_010_000 + status, reason,
                new CutoverApprovalContractException.ErrorData(category, reason, recovery, owner,
                        ex.currentApprovalVersion(), ex.currentTaskVersion()));
    }

    private static ResponseEntity<CommonResult<CutoverApprovalContractException.ErrorData>> error(
            CutoverApprovalContractException ex) {
        CommonResult<CutoverApprovalContractException.ErrorData> body = CommonResult.error(ex.errorCode(), ex.getMessage());
        body.setData(ex.errorData());
        return ResponseEntity.status(ex.httpStatus()).body(body);
    }
}
