package cn.iocoder.yudao.module.pms.cutover.controller.business.checklist;

import cn.iocoder.yudao.module.pms.cutover.controller.business.checklist.vo.CutoverExternalChecklistResponses;
import cn.iocoder.yudao.module.pms.cutover.controller.business.support.CutoverErrorData;
import cn.iocoder.yudao.module.pms.cutover.controller.business.checklist.vo.CutoverChecklistResponses;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.controller.business.support.CutoverRequestHeaders;
import cn.iocoder.yudao.module.pms.cutover.controller.business.checklist.vo.CutoverChecklistReqVO;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportException;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.AddCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RematchChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RemoveCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RequestCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SelectManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SubmitChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistExportResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * F-CUT-003 P3 清单 REST 候选。
 *
 * <p>生产 Owner 接通前不得增加 {@code @RestController}/{@code @Component} 或生产 {@code @Bean}。</p>
 */
@RequestMapping("/api/v1/pms/cutover-tasks/{taskId}/checklist")
@RestController
public class CutoverChecklistController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CutoverChecklistApplicationService service;
    private final CutoverChecklistExportService exportService;
    private final CutoverChecklistRequestContext requestContext;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverExternalChecklistService external;

    public CutoverChecklistController(CutoverChecklistApplicationService service,
                                      CutoverChecklistExportService exportService,
                                      CutoverChecklistRequestContext requestContext) {
        this.service = Objects.requireNonNull(service);
        this.exportService = Objects.requireNonNull(exportService);
        this.requestContext = Objects.requireNonNull(requestContext);
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverChecklistResponses.View> get(@PathVariable("taskId") Long taskId,
            @org.springframework.web.bind.annotation.RequestParam(value = "checklistId", required = false) Long checklistId) {
        requireId(taskId);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.View.from(service.getView(
                new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(trusted, taskId, checklistId))));
    }

    @GetMapping("/revisions")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<java.util.List<CutoverChecklistApplicationService.RevisionSummary>> revisions(@PathVariable("taskId") Long taskId) {
        requireId(taskId);
        return success(service.history(new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(requestContext.current(), taskId, null)));
    }

    @PostMapping("/actions/export")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public ResponseEntity<byte[]> export(@PathVariable("taskId") Long taskId,
                                         @RequestBody CutoverChecklistReqVO.Export request) {
        requireId(taskId);
        if (request == null || !request.isChecklistVersionSpecified()
                || request.checklistVersion() == null || request.checklistVersion() <= 0) {
            throw new CutoverChecklistExportException(
                    CutoverChecklistExportException.Code.INVALID_EXPORT_REQUEST, "导出清单版本非法");
        }
        var trusted = requestContext.current();
        CutoverChecklistExportResult result = exportService.export(trusted, taskId, request.checklistVersion());
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .body(result.content());
    }

    @GetMapping("/external-state")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverExternalChecklistResponses.State> externalState(
            @PathVariable("taskId") Long taskId,
            @org.springframework.web.bind.annotation.RequestParam(value = "checklistId", required = false) Long checklistId) {
        requireId(taskId);
        return success(CutoverExternalChecklistResponses.State
                .from(external.state(new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(requestContext.current(), taskId, checklistId))));
    }

    @PutMapping("/device-versions")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<Long> saveDeviceVersions(@PathVariable("taskId") Long taskId,
                                                @RequestHeader HttpHeaders headers, @RequestBody CutoverChecklistReqVO.DeviceVersions request) {
        requireId(taskId); require(request != null, "device versions request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(external.saveVersions(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistDeviceVersionsCommand(
                trusted.tenantId(), trusted.actorId(), taskId, request.expectedTaskVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedProjectScopeVersion(), request.devices(), CutoverRequestHeaders.intent(headers))));
    }

    @PostMapping("/items/{stableItemKey}/external-queries")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<CutoverExternalChecklistResponses.LoadResult> loadExternal(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestHeader HttpHeaders headers, @RequestBody CutoverChecklistReqVO.ExternalLoad request) {
        requireId(taskId); require(request != null, "external load request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverExternalChecklistResponses.LoadResult.from(
                external.load(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.LoadExternalChecklistCommand(
                trusted.tenantId(), trusted.actorId(), taskId, request.expectedTaskVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedProjectScopeVersion(), stableItemKey, CutoverRequestHeaders.intent(headers)))));
    }

    @PutMapping("/items/{stableItemKey}/external-feedback")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<CutoverChecklistResponses.ChecklistItemCommandResult> saveExternalFeedback(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestHeader HttpHeaders headers, @RequestBody CutoverChecklistReqVO.ExternalFeedback request) {
        requireId(taskId); require(request != null, "external feedback request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.ChecklistItemCommandResult.from(external.feedback(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveExternalChecklistFeedbackCommand(
                trusted.tenantId(), trusted.actorId(), taskId, request.expectedTaskVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedProjectScopeVersion(), stableItemKey, request.expectedResultId(), request.feedback(), CutoverRequestHeaders.intent(headers)))));
    }

    @GetMapping("/matching-options")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverChecklistResponses.MatchingOptions> matchingOptions(@PathVariable("taskId") Long taskId,
            @org.springframework.web.bind.annotation.RequestParam(value = "checklistId", required = false) Long checklistId) {
        requireId(taskId);
        return success(CutoverChecklistResponses.MatchingOptions.from(service.getMatchingOptions(
                new cn.iocoder.yudao.module.pms.cutover.service.checklist.query.CutoverChecklistReadQuery(requestContext.current(), taskId, checklistId))));
    }

    @PostMapping("/actions/generate")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<CutoverChecklistResponses.ChecklistCommandResult> generate(
            @PathVariable("taskId") Long taskId,
            @RequestHeader HttpHeaders headers,
            @RequestBody CutoverChecklistReqVO.Generate request) {
        requireId(taskId);
        require(request != null, "generate request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.ChecklistCommandResult.from(service.generate(GenerateChecklistCommand.builder()
                .tenantId(trusted.tenantId()).actorId(trusted.actorId()).taskId(taskId)
                .expectedTaskVersion(request.expectedTaskVersion()).expectedAssessmentVersion(request.expectedAssessmentVersion())
                .expectedProjectScopeVersion(request.expectedProjectScopeVersion()).matchingInput(request.matchingInput())
                .selectedConflictDefinitions(definitions(request.selectedConflictDefinitions()))
                .idempotencyKey(header(CutoverRequestHeaders.value(headers, "Idempotency-Key"), "Idempotency-Key"))
                .correlationId(trusted.correlationId()).build())));
    }

    @PostMapping("/actions/rematch")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<CutoverChecklistResponses.ChecklistCommandResult> rematch(
            @PathVariable("taskId") Long taskId,
            @RequestHeader HttpHeaders headers,
            @RequestBody CutoverChecklistReqVO.Rematch request) {
        requireId(taskId);
        require(request != null, "rematch request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.ChecklistCommandResult.from(service.rematch(RematchChecklistCommand.builder()
                .tenantId(trusted.tenantId()).actorId(trusted.actorId()).taskId(taskId)
                .expectedTaskVersion(request.expectedTaskVersion()).expectedAssessmentVersion(request.expectedAssessmentVersion())
                .checklistId(request.checklistId()).expectedChecklistVersion(request.expectedChecklistVersion())
                .expectedInputSnapshotHash(request.expectedInputSnapshotHash())
                .expectedProjectScopeVersion(request.expectedProjectScopeVersion()).matchingInput(request.matchingInput())
                .selectedConflictDefinitions(definitions(request.selectedConflictDefinitions()))
                .idempotencyKey(header(CutoverRequestHeaders.value(headers, "Idempotency-Key"), "Idempotency-Key"))
                .correlationId(trusted.correlationId()).build())));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<CutoverChecklistResponses.ChecklistCommandResult> save(@PathVariable("taskId") Long taskId,
                                                      @RequestHeader HttpHeaders headers, @RequestBody CutoverChecklistReqVO.Save request) {
        requireId(taskId);
        require(request != null, "save request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.ChecklistCommandResult.from(service.save(new SaveChecklistCommand(trusted.tenantId(), trusted.actorId(), taskId,
                request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), request.answers() == null ? null : request.answers().stream()
                .map(answer -> new SaveChecklistCommand.DirectAnswer(answer.stableItemKey(), answer.answerSnapshot()))
                .toList(), CutoverRequestHeaders.intent(headers)))));
    }

    @PostMapping("/custom-items")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<CutoverChecklistResponses.ChecklistItemCommandResult> addCustom(
            @PathVariable("taskId") Long taskId, @RequestHeader HttpHeaders headers, @RequestBody CutoverChecklistReqVO.CustomItem request) {
        requireId(taskId);
        require(request != null && request.required() != null, "custom item request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.ChecklistItemCommandResult.from(service.addCustomItem(new AddCustomItemCommand(trusted.tenantId(), trusted.actorId(), taskId,
                request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), request.itemTypeCode(), request.itemName(),
                request.itemDescription(), request.interfaceFormatCode(), request.interfaceSchema(),
                request.required(), request.answerSnapshot(), CutoverRequestHeaders.intent(headers)))));
    }

    @DeleteMapping("/custom-items/{stableItemKey}")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<CutoverChecklistResponses.ChecklistItemCommandResult> removeCustom(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestHeader HttpHeaders headers, @RequestBody CutoverChecklistReqVO.CustomItemRemove request) {
        requireId(taskId);
        require(request != null, "custom item remove request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.ChecklistItemCommandResult.from(service.removeCustomItem(new RemoveCustomItemCommand(trusted.tenantId(), trusted.actorId(),
                taskId, request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), stableItemKey, CutoverRequestHeaders.intent(headers)))));
    }

    @PostMapping("/items/{stableItemKey}/collection-requests")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:request-collection')")
    public CommonResult<CutoverChecklistResponses.CollectionRequestCommandResult> requestCollection(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestHeader HttpHeaders headers,
            @org.springframework.web.bind.annotation.RequestParam(value = "executionId", required = false) Long executionId,
            @RequestBody CutoverChecklistReqVO.CollectionRequest request) {
        requireId(taskId);
        require(request != null, "collection request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.CollectionRequestCommandResult.from(service.requestCollection(new RequestCollectionCommand(trusted.tenantId(), trusted.actorId(),
                taskId, request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), stableItemKey, request.deviceId(), request.commandTemplateId(),
                header(CutoverRequestHeaders.value(headers, "Idempotency-Key"), "Idempotency-Key"), trusted.correlationId()), executionId)));
    }

    @PostMapping("/items/{stableItemKey}/manual-results")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<CutoverChecklistResponses.ChecklistItemCommandResult> selectManual(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestHeader HttpHeaders headers, @RequestBody CutoverChecklistReqVO.ManualResult request) {
        requireId(taskId);
        require(request != null && request.file() != null && request.file().fileFactVersion() != null,
                "manual result request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        var file = request.file();
        var version = file.fileFactVersion();
        var handle = CutoverChecklistFilePort.FileHandle.builder()
                .artifactId(file.artifactId())
                .versionNo(file.versionNo())
                .referenceKey(file.referenceKey())
                .fileFactVersion(new CutoverChecklistFilePort.FileFactVersion(version.artifactVersion(), version.referenceVersion(),
                        version.availabilityVersion()))
                .scopeVersion(file.scopeVersion())
                .build();
        return success(CutoverChecklistResponses.ChecklistItemCommandResult.from(service.selectManual(new SelectManualResultCommand(trusted.tenantId(), trusted.actorId(),
                taskId, request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), stableItemKey, handle, request.factDescription(), CutoverRequestHeaders.intent(headers)))));
    }

    @PostMapping("/actions/submit")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:submit-checklist')")
    public CommonResult<CutoverChecklistResponses.ChecklistCommandResult> submit(
            @PathVariable("taskId") Long taskId,
            @RequestHeader HttpHeaders headers,
            @RequestBody CutoverChecklistReqVO.Submit request) {
        requireId(taskId);
        require(request != null, "submit request");
        requireVersionHeaders(headers, request);
        var trusted = requestContext.current();
        return success(CutoverChecklistResponses.ChecklistCommandResult.from(service.submit(new SubmitChecklistCommand(trusted.tenantId(), trusted.actorId(), taskId,
                request.expectedTaskVersion(), request.expectedAssessmentVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedProjectScopeVersion(),
                header(CutoverRequestHeaders.value(headers, "Idempotency-Key"), "Idempotency-Key"), trusted.correlationId()))));
    }

    @ExceptionHandler(CutoverChecklistException.class)
    public ResponseEntity<CommonResult<CutoverErrorData>> handleChecklistException(CutoverChecklistException exception) {
        int status = switch (exception.getCode()) {
            case NOT_FOUND -> 404;
            case DATA_SCOPE_FORBIDDEN -> 403;
            case STATE_CONFLICT, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS -> 409;
            case FILE_FACT_INVALID, COLLECTION_FACT_INVALID -> 422;
            case INVALID_REQUEST, FROZEN_CONFIGURATION_NOT_FOUND, FROZEN_CONFIGURATION_INVALID -> 400;
        };
        CommonResult<CutoverErrorData> result = CommonResult.error(1_011_005_000 + exception.getCode().ordinal(),
                exception.getMessage());
        String recovery = switch (exception.getCode()) {
            case STATE_CONFLICT, VERSION_CONFLICT -> "REFRESH_AGGREGATE";
            case IDEMPOTENCY_CONFLICT -> "START_NEW_INTENT";
            case IDEMPOTENCY_IN_PROGRESS -> "RETRY_SAME_KEY";
            case FILE_FACT_INVALID, COLLECTION_FACT_INVALID -> "REFRESH_OWNER_FACTS";
            case NOT_FOUND, DATA_SCOPE_FORBIDDEN -> "RETURN_TO_LIST";
            case INVALID_REQUEST, FROZEN_CONFIGURATION_NOT_FOUND, FROZEN_CONFIGURATION_INVALID -> "FIX_REQUEST";
        };
        result.setData(new CutoverErrorData(
                status == 409 ? "CONFLICT" : status == 403 ? "FORBIDDEN" : "INVALID_REQUEST",
                exception.getCode().name(), recovery));
        return ResponseEntity.status(status).body(result);
    }

    @ExceptionHandler(CutoverChecklistExportException.class)
    public ResponseEntity<CommonResult<CutoverErrorData>> handleExportException(CutoverChecklistExportException exception) {
        int status = switch (exception.getCode()) {
            case NOT_VISIBLE_OR_NOT_FOUND -> 404;
            case CHECKLIST_VERSION_STALE, OWNER_FACT_STALE -> 409;
            case INVALID_EXPORT_REQUEST -> 400;
            case EXPORT_PROJECTION_INVALID -> 500;
            case OWNER_PROVIDER_UNAVAILABLE -> 503;
        };
        CommonResult<CutoverErrorData> result = CommonResult.error(1_011_005_100 + exception.getCode().ordinal(),
                exception.getMessage());
        result.setData(new CutoverErrorData(
                status >= 500 ? "OWNER_FAILURE" : "INVALID_REQUEST", exception.getCode().name(),
                status == 409 ? "REFRESH_AGGREGATE" : status == 503 ? "RETRY" : status == 404 ? "RETURN_TO_LIST" : "FIX_REQUEST"));
        return ResponseEntity.status(status).body(result);
    }

    private static Map<String, GenerateChecklistCommand.SelectedDefinition> definitions(
            Map<String, CutoverChecklistReqVO.SelectedDefinition> values) {
        if (values == null) {
            return Map.of();
        }
        return values.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> new GenerateChecklistCommand.SelectedDefinition(entry.getValue().itemDefinitionId(),
                        entry.getValue().itemDefinitionVersion())));
    }

    private static void requireVersionHeaders(HttpHeaders headers, CutoverChecklistReqVO.TaskVersioned request) {
        require(Objects.equals(CutoverRequestHeaders.version(headers, "X-Task-Version"), request.expectedTaskVersion()),
                "X-Task-Version must match expectedTaskVersion");
        if (request instanceof CutoverChecklistReqVO.DraftVersioned draft) {
            require(Objects.equals(CutoverRequestHeaders.version(headers, "If-Match"), draft.expectedChecklistVersion()),
                    "If-Match must match expectedChecklistVersion");
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
        if (!valid) {
            throw new IllegalArgumentException("invalid " + field);
        }
    }
}
