package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.checklist.CutoverChecklistReqVO;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverRuntimeChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportException;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverRuntimeChecklistExportService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.AddCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RematchChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RemoveCustomItemCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.RequestCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SelectManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SubmitChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistItemCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CollectionRequestCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistMatchingOptions;
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
public class CutoverRuntimeChecklistController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CutoverRuntimeChecklistApplicationService service;
    private final CutoverRuntimeChecklistExportService exportService;
    private final CutoverChecklistRequestContext requestContext;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverExternalChecklistService external;

    public CutoverRuntimeChecklistController(CutoverRuntimeChecklistApplicationService service,
                                      CutoverRuntimeChecklistExportService exportService,
                                      CutoverChecklistRequestContext requestContext) {
        this.service = Objects.requireNonNull(service);
        this.exportService = Objects.requireNonNull(exportService);
        this.requestContext = Objects.requireNonNull(requestContext);
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverChecklistView> get(@PathVariable("taskId") Long taskId) {
        requireId(taskId);
        var trusted = requestContext.current();
        return success(service.getView(trusted.tenantId(), trusted.actorId(), taskId));
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
    public CommonResult<cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverExternalChecklistService.State> externalState(
            @PathVariable("taskId") Long taskId) {
        requireId(taskId);
        return success(external.state(requestContext.current(), taskId));
    }

    @PutMapping("/device-versions")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<Long> saveDeviceVersions(@PathVariable("taskId") Long taskId,
                                                @RequestBody CutoverChecklistReqVO.DeviceVersions request) {
        requireId(taskId); require(request != null, "device versions request");
        var trusted = requestContext.current();
        return success(external.saveVersions(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistDeviceVersionsCommand(
                trusted.tenantId(), trusted.actorId(), taskId, request.expectedTaskVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedProjectScopeVersion(), request.devices())));
    }

    @PostMapping("/items/{stableItemKey}/external-queries")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverExternalChecklistService.LoadResult> loadExternal(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestBody CutoverChecklistReqVO.ExternalLoad request) {
        requireId(taskId); require(request != null, "external load request");
        var trusted = requestContext.current();
        return success(external.load(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.LoadExternalChecklistCommand(
                trusted.tenantId(), trusted.actorId(), taskId, request.expectedTaskVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedProjectScopeVersion(), stableItemKey)));
    }

    @PutMapping("/items/{stableItemKey}/external-feedback")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<ChecklistItemCommandResult> saveExternalFeedback(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestBody CutoverChecklistReqVO.ExternalFeedback request) {
        requireId(taskId); require(request != null, "external feedback request");
        var trusted = requestContext.current();
        return success(external.feedback(new cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveExternalChecklistFeedbackCommand(
                trusted.tenantId(), trusted.actorId(), taskId, request.expectedTaskVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedProjectScopeVersion(), stableItemKey, request.expectedResultId(), request.feedback())));
    }

    @GetMapping("/matching-options")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverChecklistMatchingOptions> matchingOptions(@PathVariable("taskId") Long taskId) {
        requireId(taskId);
        return success(service.getMatchingOptions(requestContext.current(), taskId));
    }

    @PostMapping("/actions/generate")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<ChecklistCommandResult> generate(
            @PathVariable("taskId") Long taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CutoverChecklistReqVO.Generate request) {
        requireId(taskId);
        require(request != null, "generate request");
        var trusted = requestContext.current();
        return success(service.generate(new GenerateChecklistCommand(trusted.tenantId(), trusted.actorId(), taskId,
                request.expectedTaskVersion(), request.expectedAssessmentVersion(),
                request.expectedProjectScopeVersion(), request.deviceTypeCodes(), definitions(request.selectedConflictDefinitions()),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId())));
    }

    @PostMapping("/actions/rematch")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<ChecklistCommandResult> rematch(
            @PathVariable("taskId") Long taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CutoverChecklistReqVO.Rematch request) {
        requireId(taskId);
        require(request != null, "rematch request");
        var trusted = requestContext.current();
        return success(service.rematch(new RematchChecklistCommand(trusted.tenantId(), trusted.actorId(), taskId,
                request.expectedTaskVersion(), request.expectedAssessmentVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedInputSnapshotHash(),
                request.expectedProjectScopeVersion(), request.deviceTypeCodes(), definitions(request.selectedConflictDefinitions()),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId())));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<ChecklistCommandResult> save(@PathVariable("taskId") Long taskId,
                                                      @RequestBody CutoverChecklistReqVO.Save request) {
        requireId(taskId);
        require(request != null, "save request");
        var trusted = requestContext.current();
        return success(service.save(new SaveChecklistCommand(trusted.tenantId(), trusted.actorId(), taskId,
                request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), request.answers() == null ? null : request.answers().stream()
                .map(answer -> new SaveChecklistCommand.DirectAnswer(answer.stableItemKey(), answer.answerSnapshot()))
                .toList())));
    }

    @PostMapping("/custom-items")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<ChecklistItemCommandResult> addCustom(
            @PathVariable("taskId") Long taskId, @RequestBody CutoverChecklistReqVO.CustomItem request) {
        requireId(taskId);
        require(request != null && request.required() != null, "custom item request");
        var trusted = requestContext.current();
        return success(service.addCustomItem(new AddCustomItemCommand(trusted.tenantId(), trusted.actorId(), taskId,
                request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), request.itemTypeCode(), request.itemName(),
                request.itemDescription(), request.interfaceFormatCode(), request.interfaceSchema(),
                request.required(), request.answerSnapshot())));
    }

    @DeleteMapping("/custom-items/{stableItemKey}")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<ChecklistItemCommandResult> removeCustom(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestBody CutoverChecklistReqVO.CustomItemRemove request) {
        requireId(taskId);
        require(request != null, "custom item remove request");
        var trusted = requestContext.current();
        return success(service.removeCustomItem(new RemoveCustomItemCommand(trusted.tenantId(), trusted.actorId(),
                taskId, request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), stableItemKey)));
    }

    @PostMapping("/items/{stableItemKey}/collection-requests")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:request-collection')")
    public CommonResult<CollectionRequestCommandResult> requestCollection(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @org.springframework.web.bind.annotation.RequestParam(value = "executionId", required = false) Long executionId,
            @RequestBody CutoverChecklistReqVO.CollectionRequest request) {
        requireId(taskId);
        require(request != null, "collection request");
        var trusted = requestContext.current();
        return success(service.requestCollection(new RequestCollectionCommand(trusted.tenantId(), trusted.actorId(),
                taskId, request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), stableItemKey, request.deviceId(), request.commandTemplateId(),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId()), executionId));
    }

    @PostMapping("/items/{stableItemKey}/manual-results")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:save-checklist')")
    public CommonResult<ChecklistItemCommandResult> selectManual(
            @PathVariable("taskId") Long taskId, @PathVariable("stableItemKey") String stableItemKey,
            @RequestBody CutoverChecklistReqVO.ManualResult request) {
        requireId(taskId);
        require(request != null && request.file() != null && request.file().fileFactVersion() != null,
                "manual result request");
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
        return success(service.selectManual(new SelectManualResultCommand(trusted.tenantId(), trusted.actorId(),
                taskId, request.expectedTaskVersion(), request.checklistId(), request.expectedChecklistVersion(),
                request.expectedProjectScopeVersion(), stableItemKey, handle, request.factDescription())));
    }

    @PostMapping("/actions/submit")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:submit-checklist')")
    public CommonResult<ChecklistCommandResult> submit(
            @PathVariable("taskId") Long taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CutoverChecklistReqVO.Submit request) {
        requireId(taskId);
        require(request != null, "submit request");
        var trusted = requestContext.current();
        return success(service.submit(new SubmitChecklistCommand(trusted.tenantId(), trusted.actorId(), taskId,
                request.expectedTaskVersion(), request.expectedAssessmentVersion(), request.checklistId(),
                request.expectedChecklistVersion(), request.expectedProjectScopeVersion(),
                header(idempotencyKey, "Idempotency-Key"), trusted.correlationId())));
    }

    @ExceptionHandler(CutoverChecklistException.class)
    public ResponseEntity<CommonResult<Void>> handleChecklistException(CutoverChecklistException exception) {
        int status = switch (exception.getCode()) {
            case NOT_FOUND -> 404;
            case DATA_SCOPE_FORBIDDEN -> 403;
            case STATE_CONFLICT, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS -> 409;
            case FILE_FACT_INVALID, COLLECTION_FACT_INVALID -> 422;
            case INVALID_REQUEST, FROZEN_CONFIGURATION_NOT_FOUND, FROZEN_CONFIGURATION_INVALID -> 400;
        };
        CommonResult<Void> result = CommonResult.error(1_011_005_000 + exception.getCode().ordinal(),
                exception.getMessage());
        return ResponseEntity.status(status).body(result);
    }

    @ExceptionHandler(CutoverChecklistExportException.class)
    public ResponseEntity<CommonResult<Void>> handleExportException(CutoverChecklistExportException exception) {
        int status = switch (exception.getCode()) {
            case NOT_VISIBLE_OR_NOT_FOUND -> 404;
            case CHECKLIST_VERSION_STALE, OWNER_FACT_STALE -> 409;
            case INVALID_EXPORT_REQUEST -> 400;
            case EXPORT_PROJECTION_INVALID -> 500;
            case OWNER_PROVIDER_UNAVAILABLE -> 503;
        };
        CommonResult<Void> result = CommonResult.error(1_011_005_100 + exception.getCode().ordinal(),
                exception.getMessage());
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
