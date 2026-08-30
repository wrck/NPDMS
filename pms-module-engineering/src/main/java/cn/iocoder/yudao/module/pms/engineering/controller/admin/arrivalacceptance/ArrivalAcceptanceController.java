package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance.vo.ArrivalAcceptanceReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance.vo.ArrivalAcceptanceRespVO;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalDifferenceScopeCodec;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceCommandService;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceContractException;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceCommands;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceViews;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.OwnerFactVersionMismatchException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * F-IMP-002 到货签收 REST 契约候选。
 *
 * <p>Task 12 前不得增加 {@code @RestController}/{@code @Component} 或生产 {@code @Bean}；
 * 当前类仅允许测试显式组装。</p>
 */
@Tag(name = "管理后台 - PMS 到货签收")
@RequestMapping("/api/v1/pms/arrival-acceptances")
@ResponseBody
public class ArrivalAcceptanceController {

    private static final int ARRIVAL_NOT_VISIBLE = 1_011_004_011;
    private static final int ARRIVAL_DATA_SCOPE_FORBIDDEN = 1_011_004_012;
    private static final int ARRIVAL_STATE_CONFLICT = 1_011_004_013;
    private static final int ARRIVAL_VERSION_CONFLICT = 1_011_004_014;
    private static final int DIFFERENCE_VERSION_CONFLICT = 1_011_004_015;
    private static final int IDEMPOTENCY_CONFLICT = 1_011_004_016;
    private static final int IDEMPOTENCY_IN_PROGRESS = 1_011_004_017;
    private static final int OWNER_UNAVAILABLE = 1_011_004_018;
    private static final int SCOPE_STALE = 1_011_004_019;
    private static final int EVIDENCE_INVALID = 1_011_004_020;
    private static final int BUSINESS_GATE_INVALID = 1_011_004_021;

    private final ArrivalAcceptanceApplicationService applicationService;
    private final ArrivalAcceptanceCommandService commandService;
    private final ArrivalAcceptanceQueryService queryService;
    private final ArrivalAcceptanceRequestContext requestContext;

    public ArrivalAcceptanceController(ArrivalAcceptanceApplicationService applicationService,
                                       ArrivalAcceptanceCommandService commandService,
                                       ArrivalAcceptanceQueryService queryService,
                                       ArrivalAcceptanceRequestContext requestContext) {
        this.applicationService = Objects.requireNonNull(applicationService);
        this.commandService = Objects.requireNonNull(commandService);
        this.queryService = Objects.requireNonNull(queryService);
        this.requestContext = Objects.requireNonNull(requestContext);
    }

    @GetMapping
    @Operation(summary = "分页查询到货签收批次")
    @PreAuthorize("@ss.hasPermission('pms:arrival-acceptance:query')")
    public CommonResult<ArrivalAcceptanceRespVO.Page> list(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "batchCode", required = false) String batchCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        requirePage(projectId, batchCode, status, pageNo, pageSize);
        var trusted = requestContext.current();
        PageResult<ArrivalAcceptanceViews.ArrivalListItem> page = queryService.page(
                new ArrivalAcceptanceViews.PageRequest(trusted.tenantId(), projectId, batchCode,
                        status, pageNo, pageSize, trusted.access()));
        return success(new ArrivalAcceptanceRespVO.Page(
                page.getList().stream().map(ArrivalAcceptanceController::listItem).toList(),
                page.getTotal()));
    }

    @PostMapping
    @Operation(summary = "创建到货签收草稿")
    @PreAuthorize("@ss.hasPermission('pms:arrival-acceptance:create')")
    public CommonResult<ArrivalAcceptanceRespVO.Command> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody JsonNode body) {
        ArrivalAcceptanceReqVO.Create request = ArrivalAcceptanceRequestCodec.create(body);
        requireCreate(request);
        String key = normalizedHeader(idempotencyKey, "Idempotency-Key");
        var trusted = requestContext.current();
        var created = applicationService.createDraft(new ArrivalAcceptanceApplicationService.CreateDraftCommand(
                trusted.tenantId(), request.projectId(), trusted.actorUserId(), request.batchCode(),
                request.logisticsNo(), request.arrivedAt(), request.signerName(),
                request.expectedDeliveryScopeVersion(), key, trusted.correlationId()));
        return success(command(detail(trusted, created.getId()), List.of(), null, null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询到货签收详情")
    @PreAuthorize("@ss.hasPermission('pms:arrival-acceptance:query')")
    public CommonResult<ArrivalAcceptanceRespVO.Detail> detail(@PathVariable("id") Long id) {
        requireId(id);
        var trusted = requestContext.current();
        return success(detail(detail(trusted, id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "修改本人到货签收草稿")
    @PreAuthorize("@ss.hasPermission('pms:arrival-acceptance:edit-own-draft')")
    public CommonResult<ArrivalAcceptanceRespVO.Command> patch(
            @PathVariable("id") Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody JsonNode body) {
        ArrivalAcceptanceReqVO.Patch request = ArrivalAcceptanceRequestCodec.patch(body);
        requireId(id);
        requirePatch(request);
        int expectedVersion = parseIfMatch(ifMatch);
        var trusted = requestContext.current();
        var result = commandService.patchDraft(new ArrivalAcceptanceCommands.PatchDraftCommand(
                trusted.tenantId(), id, trusted.actorUserId(), expectedVersion,
                request.logisticsNo(), request.arrivedAt(), request.signerName(),
                draftLines(request.lines()), file(request.evidenceRevision())));
        ArrivalAcceptanceViews.ArrivalDetail current = detail(trusted, id);
        return success(command(current, changedLineIds(current, request.lines()), null,
                result.successorAcceptanceId()));
    }

    @PostMapping("/{id}/actions/submit")
    @Operation(summary = "提交到货签收草稿")
    @PreAuthorize("@ss.hasPermission('pms:arrival-acceptance:edit-own-draft')")
    public CommonResult<ArrivalAcceptanceRespVO.Command> submit(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody(required = false) JsonNode body) {
        ArrivalAcceptanceRequestCodec.empty(body);
        requireId(id);
        String key = normalizedHeader(idempotencyKey, "Idempotency-Key");
        int expectedVersion = parseIfMatch(ifMatch);
        var trusted = requestContext.current();
        applicationService.submit(new ArrivalAcceptanceApplicationService.SubmitCommand(
                trusted.tenantId(), id, trusted.actorUserId(), expectedVersion,
                key, trusted.correlationId()));
        return success(command(detail(trusted, id), List.of(), null, null));
    }

    @PostMapping("/{id}/actions/confirm")
    @Operation(summary = "确认到货签收批次")
    @PreAuthorize("@ss.hasPermission('pms:arrival-acceptance:confirm')")
    public CommonResult<ArrivalAcceptanceRespVO.Command> confirm(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody(required = false) JsonNode body) {
        ArrivalAcceptanceRequestCodec.empty(body);
        requireId(id);
        String key = normalizedHeader(idempotencyKey, "Idempotency-Key");
        int expectedVersion = parseIfMatch(ifMatch);
        var trusted = requestContext.current();
        var result = applicationService.confirm(new ArrivalAcceptanceApplicationService.ConfirmCommand(
                trusted.tenantId(), id, trusted.actorUserId(), expectedVersion,
                key, trusted.correlationId()));
        return success(command(detail(trusted, id), List.of(), result.eventId(), null));
    }

    @PostMapping("/{id}/actions/raise-difference")
    @Operation(summary = "提出到货差异")
    @PreAuthorize("@ss.hasPermission('pms:arrival-acceptance:resolve-difference')")
    public CommonResult<ArrivalAcceptanceRespVO.DifferenceCommand> raiseDifference(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody JsonNode body) {
        ArrivalAcceptanceReqVO.RaiseDifference request = ArrivalAcceptanceRequestCodec.raise(body);
        requireId(id);
        requireRaise(request);
        String key = normalizedHeader(idempotencyKey, "Idempotency-Key");
        int expectedVersion = parseIfMatch(ifMatch);
        var trusted = requestContext.current();
        var result = commandService.raiseDifference(new ArrivalAcceptanceCommands.RaiseDifferenceCommand(
                trusted.tenantId(), id, trusted.actorUserId(), expectedVersion,
                request.arrivalLineId(), request.expectedLineVersion(), request.differenceTypeCode(),
                scope(request.scopeSnapshot()), request.reason(), request.riskDescription(),
                file(request.evidenceRevision()), key, trusted.correlationId()));
        return success(differenceCommand(result, detail(trusted, id)));
    }

    @PostMapping("/{id}/actions/resolve-difference")
    @Operation(summary = "处理到货差异或创建信息纠正后继")
    @PreAuthorize("@ss.hasPermission('pms:arrival-acceptance:resolve-difference')")
    public CommonResult<ArrivalAcceptanceRespVO.DifferenceCommand> resolveDifference(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody JsonNode body) {
        ArrivalAcceptanceReqVO.Resolution request = ArrivalAcceptanceRequestCodec.resolution(body);
        requireId(id);
        String key = normalizedHeader(idempotencyKey, "Idempotency-Key");
        int expectedVersion = parseIfMatch(ifMatch);
        var trusted = requestContext.current();
        var result = commandService.resolveDifference(new ArrivalAcceptanceCommands.ResolveDifferenceCommand(
                trusted.tenantId(), id, trusted.actorUserId(), expectedVersion,
                resolution(request), key, trusted.correlationId()));
        Long projectedId = result.successorAcceptanceId() == null ? id : result.successorAcceptanceId();
        return success(differenceCommand(result, detail(trusted, projectedId)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<CommonResult<ArrivalAcceptanceRespVO.ErrorData>> handleRuntime(
            RuntimeException exception) {
        if (exception instanceof ArrivalAcceptanceQueryService.NotVisibleException) {
            return error(404, ARRIVAL_NOT_VISIBLE, "到货签收不可见或不存在", null);
        }
        if (exception instanceof ArrivalAcceptanceCommandService.DifferenceVersionConflictException) {
            var conflict = (ArrivalAcceptanceCommandService.DifferenceVersionConflictException) exception;
            return recoverable(409, DIFFERENCE_VERSION_CONFLICT, "到货差异版本已变化",
                    "DIFFERENCE_VERSION_CONFLICT", conflict.reasonCode(), "REFRESH_AGGREGATE",
                    null, null, conflict.currentRevision(), conflict.currentVersion(), null);
        }
        if (exception instanceof ArrivalAcceptanceCommandService.LineVersionConflictException) {
            return recoverable(409, ARRIVAL_VERSION_CONFLICT, "到货签收明细版本已变化",
                    "AGGREGATE_OR_LINE_VERSION_CONFLICT", "LINE_VERSION_STALE", "REFRESH_AGGREGATE");
        }
        if (exception instanceof ArrivalAcceptanceCommandService.VersionConflictException) {
            return recoverable(409, ARRIVAL_VERSION_CONFLICT, "到货签收或明细版本已变化",
                    "AGGREGATE_OR_LINE_VERSION_CONFLICT", "AGGREGATE_VERSION_STALE", "REFRESH_AGGREGATE");
        }
        if (exception instanceof ArrivalAcceptanceCommandService.StateConflictException) {
            return recoverable(409, ARRIVAL_STATE_CONFLICT, "到货签收状态已变化，请刷新后重试",
                    "STATE_CONFLICT", "BATCH_STATE_CONFLICT", "REFRESH_AGGREGATE");
        }
        if (exception instanceof ArrivalAcceptanceCommandService.IdempotencyConflictException) {
            return recoverable(409, IDEMPOTENCY_CONFLICT, "同一幂等键对应不同请求",
                    "IDEMPOTENCY_CONFLICT", "IDEMPOTENCY_PAYLOAD_CONFLICT", "START_NEW_INTENT");
        }
        if (exception instanceof ArrivalAcceptanceCommandService.IdempotencyInProgressException) {
            return recoverable(409, IDEMPOTENCY_IN_PROGRESS, "到货签收命令正在处理中",
                    "IDEMPOTENCY_IN_PROGRESS", "IDEMPOTENCY_COMMAND_IN_PROGRESS", "RETRY_SAME_KEY");
        }
        if (exception instanceof OwnerFactVersionMismatchException) {
            var mismatch = (OwnerFactVersionMismatchException) exception;
            if (mismatch.ownerContext() == null || mismatch.reasonCode() == null) throw exception;
            return recoverable(409, SCOPE_STALE, "到货签收范围事实已变化",
                    "SCOPE_STALE", mismatch.reasonCode(), "REFRESH_OWNER_FACTS",
                    null, null, null, null, mismatch.ownerContext());
        }
        if (exception instanceof ArrivalAcceptanceContractException) {
            return contractError((ArrivalAcceptanceContractException) exception);
        }
        if (exception instanceof IllegalArgumentException) {
            return error(400, 400, "请求参数无效", null);
        }
        throw exception;
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<CommonResult<ArrivalAcceptanceRespVO.ErrorData>> handleBinding(Exception exception) {
        return error(400, 400, "请求参数无效", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<ArrivalAcceptanceRespVO.ErrorData>> handleForbidden(
            AccessDeniedException exception) {
        return error(403, 403, "无功能权限", null);
    }

    private ArrivalAcceptanceViews.ArrivalDetail detail(
            ArrivalAcceptanceRequestContext.TrustedContext trusted, Long id) {
        return queryService.detail(new ArrivalAcceptanceViews.DetailRequest(
                trusted.tenantId(), id, trusted.access()));
    }

    private static ArrivalAcceptanceRespVO.ListItem listItem(ArrivalAcceptanceViews.ArrivalListItem item) {
        return new ArrivalAcceptanceRespVO.ListItem(item.id(), item.projectId(), item.batchCode(),
                item.logisticsNo(), item.arrivedAt(), item.signerName(), item.status(),
                item.evidenceSyncStatus(), item.version(), item.allowedActions(), item.createTime());
    }

    private static ArrivalAcceptanceRespVO.Detail detail(ArrivalAcceptanceViews.ArrivalDetail value) {
        return new ArrivalAcceptanceRespVO.Detail(value.id(), value.projectId(), value.batchCode(),
                value.logisticsNo(), value.arrivedAt(), value.signerName(), value.status(),
                value.deliveryScopeVersion(), watermark(value.scopeWatermark()), value.evidenceId(),
                value.evidenceRevision(), value.projectFactVersion(), value.predecessorAcceptanceId(),
                value.successorReason(), value.submittedBy(), value.submittedAt(), value.confirmedBy(),
                value.confirmedAt(), value.version(), value.allowedActions(),
                value.currentLines().stream().map(ArrivalAcceptanceController::line).toList(),
                value.differences().stream().map(ArrivalAcceptanceController::difference).toList(),
                evidence(value.evidence()));
    }

    private static ArrivalAcceptanceRespVO.Command command(ArrivalAcceptanceViews.ArrivalDetail detail,
                                                            List<Long> changedLineIds, String eventId,
                                                            Long successorAcceptanceId) {
        return new ArrivalAcceptanceRespVO.Command(detail.id(), detail.projectId(), detail.status(),
                detail.version(), detail.deliveryScopeVersion(), List.copyOf(changedLineIds),
                detail.evidenceId(), detail.evidenceRevision(), detail.projectFactVersion(),
                detail.evidence() == null ? null : detail.evidence().syncStatus(), eventId,
                successorAcceptanceId, detail.allowedActions());
    }

    private static ArrivalAcceptanceRespVO.DifferenceCommand differenceCommand(
            ArrivalAcceptanceCommands.CommandResult result, ArrivalAcceptanceViews.ArrivalDetail detail) {
        return new ArrivalAcceptanceRespVO.DifferenceCommand(result.arrivalAcceptanceId(),
                result.differenceId(), result.differenceNo(), result.revisionNo(), result.resolutionStatus(),
                result.aggregateStatus(), result.aggregateVersion(), result.successorAcceptanceId(),
                result.projectFactVersion(), result.factImpactType(), scope(result.remainingScope()),
                detail.allowedActions());
    }

    private static ArrivalAcceptanceRespVO.Line line(ArrivalAcceptanceViews.ArrivalLineData value) {
        return new ArrivalAcceptanceRespVO.Line(value.id(), value.lineNo(), value.lineRevision(),
                value.scopeType(), value.deviceId(), value.deviceAssignmentVersion(), value.orderLineId(),
                value.productCode(), value.modelCode(), value.expectedQuantity(), value.acceptedQuantity(),
                value.unitCode(), value.status(), value.version());
    }

    private static ArrivalAcceptanceRespVO.Difference difference(
            ArrivalAcceptanceViews.ArrivalDifferenceData value) {
        return new ArrivalAcceptanceRespVO.Difference(value.id(), value.arrivalLineId(),
                value.differenceNo(), value.revisionNo(), value.differenceType(), value.resolutionStatus(),
                value.reason(), value.riskDescription(), scope(ArrivalDifferenceScopeCodec.parse(value.scopeSnapshot())),
                value.approvedBy(), value.approvedAt(), value.exemptionExpiresAt(), value.evidenceId(),
                value.evidenceRevision(), value.current(), value.projectFactVersion(), value.factImpactType(),
                value.version());
    }

    private static ArrivalAcceptanceRespVO.Evidence evidence(ArrivalAcceptanceViews.DeliveryEvidenceData value) {
        if (value == null) return null;
        var fact = value.fileFactVersion();
        return new ArrivalAcceptanceRespVO.Evidence(value.evidenceId(), value.currentRevision(),
                value.artifactId(), value.referenceKey(), value.fileVersionNo(),
                fact == null ? null : new ArrivalAcceptanceRespVO.FileFactVersion(
                        fact.artifactVersion().longValue(), fact.referenceVersion().longValue(),
                        fact.availabilityVersion().longValue()),
                value.fileScopeVersion(), value.fileHash(), value.syncStatus(), value.nextRetryAt(),
                value.retryCount());
    }

    private static ArrivalAcceptanceRespVO.ScopeWatermark watermark(
            ArrivalAcceptanceViews.ScopeWatermarkData value) {
        return new ArrivalAcceptanceRespVO.ScopeWatermark(value.deliveryScopeVersion(),
                value.deviceAssignmentVersions().stream().map(item ->
                        new ArrivalAcceptanceRespVO.DeviceAssignmentVersion(
                                item.deviceId(), item.projectAssignmentVersion())).toList());
    }

    private static ArrivalAcceptanceCommands.FileRevision file(ArrivalAcceptanceReqVO.FileRevision value) {
        if (value == null) return null;
        var fact = value.fileFactVersion();
        return new ArrivalAcceptanceCommands.FileRevision(value.artifactId(), value.referenceKey(),
                value.versionNo(), value.scopeVersion(), fact == null ? null
                : new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion(
                        fact.artifactVersion(), fact.referenceVersion(), fact.availabilityVersion()), value.hash());
    }

    private static List<ArrivalAcceptanceCommands.DraftLine> draftLines(
            List<ArrivalAcceptanceReqVO.DraftLine> lines) {
        if (lines == null) return null;
        if (lines.isEmpty()) throw new IllegalArgumentException("empty draft lines");
        return lines.stream().map(ArrivalAcceptanceController::draftLine).toList();
    }

    private static List<Long> changedLineIds(ArrivalAcceptanceViews.ArrivalDetail detail,
                                             List<ArrivalAcceptanceReqVO.DraftLine> requested) {
        if (requested == null) return List.of();
        Set<Long> ids = new TreeSet<>();
        for (ArrivalAcceptanceReqVO.DraftLine request : requested) {
            for (ArrivalAcceptanceViews.ArrivalLineData current : detail.currentLines()) {
                if (sameScope(request, current)) ids.add(current.id());
            }
        }
        if (ids.size() != requested.size()) {
            throw new IllegalStateException("changed arrival line projection is incomplete");
        }
        return List.copyOf(ids);
    }

    private static boolean sameScope(ArrivalAcceptanceReqVO.DraftLine requested,
                                     ArrivalAcceptanceViews.ArrivalLineData current) {
        if (requested instanceof ArrivalAcceptanceReqVO.DeviceDraftLine device) {
            return "DEVICE".equals(current.scopeType()) && Objects.equals(device.deviceId(), current.deviceId());
        }
        var quantity = (ArrivalAcceptanceReqVO.QuantityDraftLine) requested;
        return "ORDER_MODEL_QUANTITY".equals(current.scopeType())
                && Objects.equals(quantity.orderLineId(), current.orderLineId())
                && Objects.equals(normalizeNullable(quantity.productCode()), current.productCode())
                && Objects.equals(normalizeNullable(quantity.modelCode()), current.modelCode())
                && Objects.equals(quantity.unitCode(), current.unitCode());
    }

    private static ArrivalAcceptanceCommands.DraftLine draftLine(ArrivalAcceptanceReqVO.DraftLine value) {
        if (value instanceof ArrivalAcceptanceReqVO.DeviceDraftLine device) {
            requireDiscriminator("DEVICE", device.scopeType());
            if (device.received() == null) throw new IllegalArgumentException("received is required");
            return new ArrivalAcceptanceCommands.DeviceDraftLine(device.lineId(), device.expectedLineVersion(),
                    device.deviceId(), device.received());
        }
        var quantity = (ArrivalAcceptanceReqVO.QuantityDraftLine) value;
        requireDiscriminator("ORDER_MODEL_QUANTITY", quantity.scopeType());
        return new ArrivalAcceptanceCommands.QuantityDraftLine(quantity.lineId(), quantity.expectedLineVersion(),
                quantity.orderLineId(), quantity.productCode(), quantity.modelCode(),
                quantity.acceptedQuantity(), quantity.unitCode());
    }

    private static ArrivalDifferenceScopeCodec.Scope scope(ArrivalAcceptanceReqVO.Scope value) {
        if (value == null) throw new IllegalArgumentException("scope is required");
        if (value instanceof ArrivalAcceptanceReqVO.DeviceScope device) {
            requireDiscriminator("DEVICE", device.scopeType());
            return new ArrivalDifferenceScopeCodec.DeviceScope(device.deviceId());
        }
        var quantity = (ArrivalAcceptanceReqVO.QuantityScope) value;
        requireDiscriminator("ORDER_MODEL_QUANTITY", quantity.scopeType());
        return new ArrivalDifferenceScopeCodec.QuantityScope(quantity.orderLineId(), quantity.productCode(),
                quantity.modelCode(), quantity.quantity(), quantity.unitCode());
    }

    private static ArrivalAcceptanceRespVO.Scope scope(ArrivalDifferenceScopeCodec.Scope value) {
        if (value == null) return null;
        if (value instanceof ArrivalDifferenceScopeCodec.DeviceScope device) {
            return new ArrivalAcceptanceRespVO.DeviceScope("DEVICE", device.deviceId());
        }
        var quantity = (ArrivalDifferenceScopeCodec.QuantityScope) value;
        return new ArrivalAcceptanceRespVO.QuantityScope("ORDER_MODEL_QUANTITY", quantity.orderLineId(),
                quantity.productCode(), quantity.modelCode(), quantity.quantity(), quantity.unitCode());
    }

    private static ArrivalAcceptanceCommands.Resolution resolution(ArrivalAcceptanceReqVO.Resolution value) {
        if (value == null) throw new IllegalArgumentException("resolution is required");
        if (value instanceof ArrivalAcceptanceReqVO.Supplement request) {
            requireDiscriminator("SUPPLEMENT", request.resolutionType());
            return new ArrivalAcceptanceCommands.Supplement(request.differenceId(),
                    request.expectedDifferenceRevision(), request.expectedDifferenceVersion(),
                    scope(request.supplementScope()), request.reason(), file(request.evidenceRevision()));
        }
        if (value instanceof ArrivalAcceptanceReqVO.KeepRejected request) {
            requireDiscriminator("KEEP_REJECTED", request.resolutionType());
            return new ArrivalAcceptanceCommands.KeepRejected(request.differenceId(),
                    request.expectedDifferenceRevision(), request.expectedDifferenceVersion(),
                    request.reason(), file(request.evidenceRevision()));
        }
        if (value instanceof ArrivalAcceptanceReqVO.Exempt request) {
            requireDiscriminator("EXEMPT", request.resolutionType());
            return new ArrivalAcceptanceCommands.Exempt(request.differenceId(),
                    request.expectedDifferenceRevision(), request.expectedDifferenceVersion(),
                    request.reason(), request.riskDescription(), request.expiresAt(),
                    file(request.evidenceRevision()));
        }
        if (value instanceof ArrivalAcceptanceReqVO.Close request) {
            requireDiscriminator("CLOSE", request.resolutionType());
            return new ArrivalAcceptanceCommands.Close(request.differenceId(),
                    request.expectedDifferenceRevision(), request.expectedDifferenceVersion(),
                    request.reason(), file(request.evidenceRevision()));
        }
        var request = (ArrivalAcceptanceReqVO.CorrectInformation) value;
        requireDiscriminator("CORRECT_INFORMATION", request.resolutionType());
        var patch = request.correctionPatch();
        if (patch == null) throw new IllegalArgumentException("correction patch is required");
        return new ArrivalAcceptanceCommands.CorrectInformation(request.expectedSourceVersion(),
                request.reason(), new ArrivalAcceptanceCommands.CorrectionPatch(
                patch.logisticsNo(), patch.arrivedAt(), patch.signerName(), draftLines(patch.lines())),
                file(request.evidenceRevision()));
    }

    private static void requireCreate(ArrivalAcceptanceReqVO.Create request) {
        if (request == null || request.projectId() == null || request.projectId() <= 0
                || request.expectedDeliveryScopeVersion() == null || request.expectedDeliveryScopeVersion() < 0
                || request.arrivedAt() == null) throw new IllegalArgumentException("invalid create request");
        normalized(request.batchCode(), 64, "batchCode");
        normalized(request.logisticsNo(), 128, "logisticsNo");
        normalized(request.signerName(), 128, "signerName");
    }

    private static void requirePatch(ArrivalAcceptanceReqVO.Patch request) {
        if (request == null || request.logisticsNo() == null && request.arrivedAt() == null
                && request.signerName() == null && request.lines() == null && request.evidenceRevision() == null) {
            throw new IllegalArgumentException("empty patch request");
        }
    }

    private static void requireRaise(ArrivalAcceptanceReqVO.RaiseDifference request) {
        if (request == null || request.arrivalLineId() == null || request.arrivalLineId() <= 0
                || request.expectedLineVersion() == null || request.expectedLineVersion() < 0
                || request.scopeSnapshot() == null || request.evidenceRevision() == null) {
            throw new IllegalArgumentException("invalid raise difference request");
        }
    }

    private static void requirePage(Long projectId, String batchCode, String status,
                                    Integer pageNo, Integer pageSize) {
        if (projectId != null && projectId <= 0 || pageNo == null || pageNo <= 0
                || pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new IllegalArgumentException("invalid page request");
        }
        if (batchCode != null) normalized(batchCode, 64, "batchCode");
        if (status != null && !List.of("DRAFT", "PARTIALLY_ACCEPTED", "DIFFERENCE_PENDING",
                "ACCEPTED", "CONFIRMED").contains(status)) throw new IllegalArgumentException("invalid status");
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("invalid arrival acceptance id");
    }

    private static int parseIfMatch(String value) {
        String normalized = normalizedHeader(value, "If-Match");
        if (!normalized.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("invalid If-Match");
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("invalid If-Match");
        }
    }

    private static String normalizedHeader(String value, String field) {
        return normalized(value, 128, field);
    }

    private static String normalized(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maxLength) {
            throw new IllegalArgumentException("invalid " + field);
        }
        return value;
    }

    private static String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    private static void requireDiscriminator(String expected, String actual) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("invalid request discriminator");
    }

    private static ResponseEntity<CommonResult<ArrivalAcceptanceRespVO.ErrorData>> recoverable(
            int status, int code, String message, String category, String reason, String recovery) {
        return recoverable(status, code, message, category, reason, recovery,
                null, null, null, null, null);
    }

    private static ResponseEntity<CommonResult<ArrivalAcceptanceRespVO.ErrorData>> recoverable(
            int status, int code, String message, String category, String reason, String recovery,
            Integer aggregateVersion, Integer lineVersion, Integer differenceRevision,
            Integer differenceVersion, String ownerContext) {
        return error(status, code, message, new ArrivalAcceptanceRespVO.ErrorData(
                category, reason, recovery, aggregateVersion, lineVersion,
                differenceRevision, differenceVersion, ownerContext));
    }

    private static ResponseEntity<CommonResult<ArrivalAcceptanceRespVO.ErrorData>> contractError(
            ArrivalAcceptanceContractException exception) {
        return switch (exception.category()) {
            case "DATA_SCOPE_FORBIDDEN" -> error(403, ARRIVAL_DATA_SCOPE_FORBIDDEN,
                    exception.getMessage(), null);
            case "NOT_VISIBLE_OR_NOT_FOUND" -> error(404, ARRIVAL_NOT_VISIBLE,
                    exception.getMessage(), null);
            case "STATE_CONFLICT" -> recoverable(409, ARRIVAL_STATE_CONFLICT,
                    exception.getMessage(), exception.category(), exception.reasonCode(), "REFRESH_AGGREGATE",
                    exception.currentAggregateVersion(), exception.currentLineVersion(),
                    exception.currentDifferenceRevision(), exception.currentDifferenceVersion(), null);
            case "AGGREGATE_OR_LINE_VERSION_CONFLICT" -> recoverable(409, ARRIVAL_VERSION_CONFLICT,
                    exception.getMessage(), exception.category(), exception.reasonCode(), "REFRESH_AGGREGATE",
                    exception.currentAggregateVersion(), exception.currentLineVersion(),
                    exception.currentDifferenceRevision(), exception.currentDifferenceVersion(), null);
            case "IDEMPOTENCY_CONFLICT" -> recoverable(409, IDEMPOTENCY_CONFLICT,
                    exception.getMessage(), exception.category(), exception.reasonCode(), "START_NEW_INTENT");
            case "IDEMPOTENCY_IN_PROGRESS" -> recoverable(409, IDEMPOTENCY_IN_PROGRESS,
                    exception.getMessage(), exception.category(), exception.reasonCode(), "RETRY_SAME_KEY");
            case "SCOPE_STALE" -> recoverable(409, SCOPE_STALE,
                    exception.getMessage(), exception.category(), exception.reasonCode(), "REFRESH_OWNER_FACTS",
                    exception.currentAggregateVersion(), exception.currentLineVersion(),
                    exception.currentDifferenceRevision(), exception.currentDifferenceVersion(),
                    exception.ownerContext());
            case "EVIDENCE_INVALID" -> recoverable(422, EVIDENCE_INVALID,
                    exception.getMessage(), exception.category(), exception.reasonCode(), "REPLACE_EVIDENCE",
                    exception.currentAggregateVersion(), exception.currentLineVersion(),
                    exception.currentDifferenceRevision(), exception.currentDifferenceVersion(),
                    exception.ownerContext());
            case "BUSINESS_GATE_INVALID" -> recoverable(422, BUSINESS_GATE_INVALID,
                    exception.getMessage(), exception.category(), exception.reasonCode(), "CORRECT_PROJECT_GATE");
            case "OWNER_PROVIDER_UNAVAILABLE" -> recoverable(503, OWNER_UNAVAILABLE,
                    exception.getMessage(), exception.category(), exception.reasonCode(), "RETRY_LATER",
                    null, null, null, null, exception.ownerContext());
            default -> throw exception;
        };
    }

    private static ResponseEntity<CommonResult<ArrivalAcceptanceRespVO.ErrorData>> error(
            int status, int code, String message, ArrivalAcceptanceRespVO.ErrorData data) {
        CommonResult<ArrivalAcceptanceRespVO.ErrorData> result = CommonResult.error(code, message);
        result.setData(data);
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(result);
    }
}
