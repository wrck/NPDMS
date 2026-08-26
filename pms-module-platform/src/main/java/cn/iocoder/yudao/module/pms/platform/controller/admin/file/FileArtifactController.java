package cn.iocoder.yudao.module.pms.platform.controller.admin.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileUploadInitReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileUploadInitRespVO;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

@Tag(name = "管理后台 - PMS 统一文件")
@RestController
@RequestMapping("/api/v1/pms/files")
@Validated
@RequiredArgsConstructor
public class FileArtifactController {

    private final FileUploadApplicationService uploadService;
    private final Environment environment;

    @PostMapping(":init-upload")
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
