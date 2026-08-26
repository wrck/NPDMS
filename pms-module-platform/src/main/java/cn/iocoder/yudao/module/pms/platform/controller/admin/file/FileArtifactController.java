package cn.iocoder.yudao.module.pms.platform.controller.admin.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileUploadInitReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileUploadInitRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileUploadCompleteRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileAccessTicketReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileAccessTicketRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileArtifactRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileCursorPageRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileReferenceRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileVersionRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileArchiveReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileAvailabilityReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileDeleteDraftReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileDetachReqVO;
import cn.iocoder.yudao.module.pms.platform.service.file.FileAccessTicketService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileLifecycleApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileQueryService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleteCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ArchiveFileReferenceCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ChangeFileAvailabilityCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.DeleteDraftFileCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.DetachFileReferenceCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

@Tag(name = "管理后台 - PMS 统一文件")
@RestController
@RequestMapping("/api/v1/pms")
@Validated
@RequiredArgsConstructor
public class FileArtifactController {

    private final FileUploadApplicationService uploadService;
    private final FileQueryService queryService;
    private final FileAccessTicketService accessTicketService;
    private final FileLifecycleApplicationService lifecycleService;
    private final Environment environment;

    @PostMapping("/files:init-upload")
    @Operation(summary = "初始化受控文件上传")
    @PreAuthorize("@ss.hasPermission('pms:file:upload')")
    public CommonResult<FileUploadInitRespVO> initializeUpload(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody FileUploadInitReqVO request) {
        return withTrustedTenant(() -> {
            var result = uploadService.initialize(new FileUploadInitializeCommand(
                    TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                    idempotencyKey, request.getModeCode(), request.getArtifactId(),
                    request.getExpectedReferenceVersion(), request.getOwnerContext(), request.getObjectType(),
                    request.getObjectId(), request.getPurposeCode(), request.getReferenceKey(),
                    request.getFileName(), request.getCategoryCode(), request.getDeclaredSizeBytes(),
                    request.getDeclaredMediaType(), request.getClientSha256()));
            return success(new FileUploadInitRespVO(
                    result.artifactId(), result.sessionId(), result.expiresAt()));
        });
    }

    @PostMapping(value = "/files/{artifactId}:complete-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "完成受控文件上传")
    @PreAuthorize("@ss.hasPermission('pms:file:upload')")
    public CommonResult<FileUploadCompleteRespVO> completeUpload(
            @PathVariable Long artifactId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestParam Long sessionId,
            @RequestParam(required = false) String clientSha256,
            @RequestPart("file") MultipartFile file) {
        return withTrustedTenant(() -> {
            var result = uploadService.complete(new FileUploadCompleteCommand(
                    TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                    idempotencyKey, artifactId, sessionId, file, clientSha256));
            return success(new FileUploadCompleteRespVO(result.artifactId(), result.versionNo(),
                    result.referenceId(), result.referenceKey(), result.sha256()));
        });
    }

    @GetMapping("/files/{artifactId}")
    @Operation(summary = "查询文件业务身份")
    @PreAuthorize("@ss.hasPermission('pms:file:query')")
    public CommonResult<FileArtifactRespVO> getArtifact(
            @PathVariable Long artifactId,
            @RequestParam @NotBlank @Size(max = 32) String ownerContext,
            @RequestParam @NotBlank @Size(max = 64) String objectType,
            @RequestParam @NotBlank @Size(max = 128) String objectId,
            @RequestParam @NotBlank @Size(max = 64) String purposeCode,
            @RequestParam @NotBlank @Size(max = 128) String referenceKey) {
        return withTrustedTenant(() -> success(queryService.getArtifact(
                artifactQuery(artifactId, ownerContext, objectType, objectId, purposeCode, referenceKey), actor())));
    }

    @GetMapping("/files/{artifactId}/versions")
    @Operation(summary = "查询文件版本历史")
    @PreAuthorize("@ss.hasPermission('pms:file:query')")
    public CommonResult<FileCursorPageRespVO<FileVersionRespVO>> getVersions(
            @PathVariable Long artifactId,
            @RequestParam @NotBlank @Size(max = 32) String ownerContext,
            @RequestParam @NotBlank @Size(max = 64) String objectType,
            @RequestParam @NotBlank @Size(max = 128) String objectId,
            @RequestParam @NotBlank @Size(max = 64) String purposeCode,
            @RequestParam @NotBlank @Size(max = 128) String referenceKey,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize) {
        return withTrustedTenant(() -> success(queryService.getVersions(
                artifactQuery(artifactId, ownerContext, objectType, objectId, purposeCode, referenceKey),
                cursor, pageSize, actor())));
    }

    @GetMapping("/file-references")
    @Operation(summary = "按完整业务稳定键查询文件引用")
    @PreAuthorize("@ss.hasPermission('pms:file:query')")
    public CommonResult<FileReferenceRespVO> getReference(
            @RequestParam Long artifactId,
            @RequestParam @NotBlank @Size(max = 32) String ownerContext,
            @RequestParam @NotBlank @Size(max = 64) String objectType,
            @RequestParam @NotBlank @Size(max = 128) String objectId,
            @RequestParam @NotBlank @Size(max = 64) String purposeCode,
            @RequestParam @NotBlank @Size(max = 128) String referenceKey) {
        return withTrustedTenant(() -> success(queryService.getReference(
                artifactQuery(artifactId, ownerContext, objectType, objectId, purposeCode, referenceKey), actor())));
    }

    @PostMapping("/files/{artifactId}/access-tickets")
    @Operation(summary = "创建文件短时下载或预览授权")
    @PreAuthorize("@ss.hasAnyPermissions('pms:file:download', 'pms:file:preview')")
    public CommonResult<FileAccessTicketRespVO> createAccessTicket(
            @PathVariable Long artifactId,
            @Valid @RequestBody FileAccessTicketReqVO request) {
        return withTrustedTenant(() -> success(accessTicketService.create(
                new FileAccessTicketService.AccessCommand(TenantContextHolder.getRequiredTenantId(),
                        SecurityFrameworkUtils.getLoginUserId(), artifactId, request.getVersionNo(),
                        request.getOperationCode(), request.getOwnerContext(), request.getObjectType(),
                        request.getObjectId(), request.getPurposeCode(), request.getReferenceKey()))));
    }

    @DeleteMapping("/file-references/{referenceId}")
    @Operation(summary = "解除文件业务引用")
    @PreAuthorize("@ss.hasPermission('pms:file:manage')")
    public CommonResult<FileLifecycleApplicationService.LifecycleResult> detachReference(
            @PathVariable Long referenceId,
            @RequestHeader("If-Match") Integer expectedVersion,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody FileDetachReqVO request) {
        return withTrustedTenant(() -> success(lifecycleService.detach(new DetachFileReferenceCommand(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                idempotencyKey, referenceId, expectedVersion, request.getOwnerContext(), request.getObjectType(),
                request.getObjectId(), request.getPurposeCode(), request.getReferenceKey(), request.getReason()))));
    }

    @PostMapping("/files/{artifactId}/actions/delete-draft")
    @Operation(summary = "删除未引用文件草稿")
    @PreAuthorize("@ss.hasPermission('pms:file:manage')")
    public CommonResult<FileLifecycleApplicationService.LifecycleResult> deleteDraft(
            @PathVariable Long artifactId,
            @RequestHeader("If-Match") Integer expectedVersion,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody FileDeleteDraftReqVO request) {
        return withTrustedTenant(() -> success(lifecycleService.deleteDraft(new DeleteDraftFileCommand(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                idempotencyKey, artifactId, expectedVersion, request.getOwnerContext(), request.getObjectType(),
                request.getObjectId(), request.getPurposeCode(), request.getReferenceKey(), request.getReason()))));
    }

    @PostMapping("/files/{artifactId}/actions/invalidate")
    @Operation(summary = "变更文件版本可用性")
    @PreAuthorize("@ss.hasPermission('pms:file:archive')")
    public CommonResult<FileLifecycleApplicationService.LifecycleResult> changeAvailability(
            @PathVariable Long artifactId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody FileAvailabilityReqVO request) {
        return withTrustedTenant(() -> success(lifecycleService.changeAvailability(
                new ChangeFileAvailabilityCommand(TenantContextHolder.getRequiredTenantId(),
                        SecurityFrameworkUtils.getLoginUserId(), idempotencyKey, artifactId,
                        request.getVersionNo(), request.getExpectedAvailabilityVersion(), request.getTargetStatus(),
                        request.getReasonCode(), request.getReasonDetail(), request.getOwnerContext(),
                        request.getObjectType(), request.getObjectId(), request.getPurposeCode(),
                        request.getReferenceKey()))));
    }

    @PostMapping("/file-references/{referenceId}/actions/archive")
    @Operation(summary = "归档文件业务引用")
    @PreAuthorize("@ss.hasPermission('pms:file:archive')")
    public CommonResult<FileLifecycleApplicationService.LifecycleResult> archiveReference(
            @PathVariable Long referenceId,
            @RequestHeader("If-Match") Integer expectedVersion,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody FileArchiveReqVO request) {
        return withTrustedTenant(() -> success(lifecycleService.archive(new ArchiveFileReferenceCommand(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                idempotencyKey, referenceId, expectedVersion, request.getArchiveBatchId(),
                request.getBusinessDecisionRef(), request.getArchiveNote(), request.getOwnerContext(),
                request.getObjectType(), request.getObjectId(), request.getPurposeCode(), request.getReferenceKey()))));
    }

    private FileQueryService.ArtifactQuery artifactQuery(Long artifactId, String ownerContext,
                                                         String objectType, String objectId,
                                                         String purposeCode, String referenceKey) {
        return new FileQueryService.ArtifactQuery(artifactId, ownerContext, objectType,
                objectId, purposeCode, referenceKey);
    }

    private FileQueryService.Actor actor() {
        return new FileQueryService.Actor(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId());
    }

    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) {
            return action.get();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
