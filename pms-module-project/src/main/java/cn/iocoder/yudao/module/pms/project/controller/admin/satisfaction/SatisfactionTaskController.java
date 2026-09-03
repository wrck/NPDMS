package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo.SatisfactionAccessGrantCreateReqVO;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAccessGrantService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionTaskManagementService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAssistedResponseApplicationService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionResponseSubmissionService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAssistedFileApplicationService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAssistedResponseReservationService;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedFileFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedUploadInitialized;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-tasks")
@Validated
@RequiredArgsConstructor
public class SatisfactionTaskController {
    private final SatisfactionAccessGrantService grantService;
    private final SatisfactionTaskManagementService taskService;
    private final SatisfactionAssistedResponseApplicationService assistedService;
    private final SatisfactionAssistedFileApplicationService assistedFileService;
    private final Environment environment;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<List<SatisfactionTaskManagementService.TaskView>> list(
            @RequestParam(required = false) Long projectId) {
        return withTenant(() -> success(taskService.list(tenantId(), actorId(), projectId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<SatisfactionTaskManagementService.TaskView> get(@PathVariable("id") Long taskId) {
        return withTenant(() -> success(taskService.get(tenantId(), actorId(), taskId)));
    }

    @PostMapping("/{id}/actions/assign")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionTaskManagementService.AssignmentResult> assign(
            @PathVariable("id") Long taskId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @Valid @RequestBody AssignReqVO request) {
        return withTenant(() -> success(taskService.assign(tenantId(), actorId(), taskId, request.assignedToUserId,
                request.expectedTaskVersion, operationId)));
    }

    @PostMapping("/{id}/actions/recollect")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionTaskManagementService.RecollectResult> recollect(
            @PathVariable("id") Long taskId, @Valid @RequestBody RecollectReqVO request) {
        return withTenant(() -> success(taskService.recollect(tenantId(), actorId(), taskId,
                new SatisfactionTaskManagementService.Recollect(request.priorResultId,
                        request.remediationRequestId, request.evidenceSummary,
                        request.evidenceFileFactVersion))));
    }

    @PostMapping("/{id}/assisted-responses")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:collect')")
    public CommonResult<SatisfactionAssistedResponseApplicationService.Outcome> assistedResponse(
            @PathVariable("id") Long taskId, @Valid @RequestBody AssistedResponseReqVO request) {
        List<SatisfactionResponseSubmissionService.FileFact> files = request.files.stream().map(file ->
                new SatisfactionResponseSubmissionService.FileFact(file.role, file.fileSlotKey, file.sequence,
                        file.artifactId, file.versionNo, file.referenceKey, file.artifactVersion,
                        file.referenceVersion, file.availabilityVersion, file.scopeVersion, file.sha256)).toList();
        return withTenant(() -> success(assistedService.submit(new SatisfactionResponseSubmissionService.AssistedCommand(
                tenantId(), actorId(), taskId, request.requestId, request.responseId, request.customerContactRef,
                request.answerSnapshot, files))));
    }

    @PostMapping("/{id}/assisted-response-reservations")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:collect')")
    public CommonResult<SatisfactionAssistedResponseReservationService.Reservation> reserveAssistedResponse(
            @PathVariable("id") Long taskId, @Valid @RequestBody AssistedReservationReqVO request) {
        return withTenant(() -> success(assistedFileService.reserve(
                tenantId(), actorId(), taskId, request.requestId)));
    }

    @PostMapping("/{id}/assisted-files")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:collect')")
    public CommonResult<AuthenticatedAssistedUploadInitialized> initializeAssistedFile(
            @PathVariable("id") Long taskId, @Valid @RequestBody AssistedFileInitReqVO request) {
        return withTenant(() -> success(assistedFileService.initialize(
                new SatisfactionAssistedFileApplicationService.InitializeCommand(tenantId(), actorId(), taskId,
                        request.requestId, request.responseId, request.policyKey, request.operationId,
                        request.fileName, request.categoryCode, request.declaredSizeBytes,
                        request.declaredMediaType, request.clientSha256))));
    }

    @PostMapping(path = "/{id}/assisted-files/{sessionId}/complete", consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:collect')")
    public CommonResult<AuthenticatedAssistedFileFact> completeAssistedFile(
            @PathVariable("id") Long taskId, @PathVariable("sessionId") Long sessionId,
            @Valid @RequestPart("metadata") AssistedFileCompleteReqVO request,
            @RequestPart("file") MultipartFile file) throws java.io.IOException {
        if (file.isEmpty() || file.getSize() > 52_428_800L) {
            throw new IllegalArgumentException("SATISFACTION_ASSISTED_FILE_CONTENT_INVALID");
        }
        byte[] content = file.getBytes();
        return withTenant(() -> success(assistedFileService.complete(
                new SatisfactionAssistedFileApplicationService.CompleteCommand(tenantId(), actorId(), taskId,
                        request.requestId, request.responseId, request.policyKey, request.operationId,
                        request.fileSlotKey, request.fileSequence, request.artifactId, sessionId,
                        content, request.clientSha256))));
    }

    @PostMapping("/{id}/access-grants")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionAccessGrantService.CreatedGrant> createGrant(
            @PathVariable("id") Long taskId,
            @Valid @RequestBody SatisfactionAccessGrantCreateReqVO request) {
        return withTenant(() -> {
            taskService.requireManageable(tenantId(), actorId(), taskId);
            return success(grantService.create(tenantId(), actorId(), taskId, request.getExpiresAt()));
        });
    }

    private Long tenantId() { return TenantContextHolder.getRequiredTenantId(); }
    private Long actorId() { return SecurityFrameworkUtils.getLoginUserId(); }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }

    @Data
    public static class AssignReqVO {
        @NotNull @Positive private Long assignedToUserId;
        @NotNull @PositiveOrZero private Integer expectedTaskVersion;
    }

    @Data
    public static class RecollectReqVO {
        @NotNull @Positive private Long priorResultId;
        @NotBlank @Size(max = 128) private String remediationRequestId;
        @NotBlank @Size(max = 1000) private String evidenceSummary;
        @Size(max = 256) private String evidenceFileFactVersion;
    }

    @Data
    public static class AssistedResponseReqVO {
        @NotBlank @Size(max = 128) private String requestId;
        @NotNull @Positive private Long responseId;
        @NotBlank @Size(max = 256) private String customerContactRef;
        @NotBlank private String answerSnapshot;
        @NotEmpty private List<@Valid AssistedFileReqVO> files;
    }

    @Data
    public static class AssistedFileReqVO {
        @NotBlank private String role;
        @NotBlank private String fileSlotKey;
        @NotNull @Positive private Integer sequence;
        @NotNull @Positive private Long artifactId;
        @NotNull @Positive private Integer versionNo;
        @NotBlank private String referenceKey;
        @NotNull @PositiveOrZero private Integer artifactVersion;
        @NotNull @PositiveOrZero private Integer referenceVersion;
        @NotNull @PositiveOrZero private Integer availabilityVersion;
        @NotNull @PositiveOrZero private Long scopeVersion;
        @NotBlank @Size(min = 64, max = 64) private String sha256;
    }

    @Data
    public static class AssistedReservationReqVO {
        @NotBlank @Size(max = 128) private String requestId;
    }

    @Data
    public static class AssistedFileInitReqVO {
        @NotBlank @Size(max = 128) private String requestId;
        @NotNull @Positive private Long responseId;
        @NotBlank private String policyKey;
        @NotBlank @Size(max = 64) private String operationId;
        @NotBlank @Size(max = 255) private String fileName;
        @NotBlank private String categoryCode;
        @NotNull @Positive private Long declaredSizeBytes;
        @NotBlank private String declaredMediaType;
        @Size(min = 64, max = 64) private String clientSha256;
    }

    @Data
    public static class AssistedFileCompleteReqVO {
        @NotBlank @Size(max = 128) private String requestId;
        @NotNull @Positive private Long responseId;
        @NotBlank private String policyKey;
        @NotBlank @Size(max = 64) private String operationId;
        @NotBlank private String fileSlotKey;
        @NotNull @Positive private Integer fileSequence;
        @NotNull @Positive private Long artifactId;
        @Size(min = 64, max = 64) private String clientSha256;
    }
}
