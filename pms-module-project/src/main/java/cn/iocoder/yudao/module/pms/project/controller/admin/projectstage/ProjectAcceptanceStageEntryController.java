package cn.iocoder.yudao.module.pms.project.controller.admin.projectstage;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectstage.vo.ProjectAcceptanceStageEntryReqVO;
import cn.iocoder.yudao.module.pms.project.service.projectstage.ProjectAcceptanceStageEntryCommand;
import cn.iocoder.yudao.module.pms.project.service.projectstage.ProjectAcceptanceStageEntryResult;
import cn.iocoder.yudao.module.pms.project.service.projectstage.ProjectAcceptanceStageEntryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/projects")
@Validated
@RequiredArgsConstructor
public class ProjectAcceptanceStageEntryController {

    private final ProjectAcceptanceStageEntryService service;
    private final Environment environment;

    @PostMapping("/{id}/actions/enter-acceptance-stage")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectAcceptanceStageEntryResult> enter(
            @PathVariable("id") Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody ProjectAcceptanceStageEntryReqVO request) {
        return withTenant(() -> {
            Integer expectedVersion = parseIfMatch(ifMatch);
            String digest = digest(projectId, expectedVersion, request);
            return success(service.enter(new ProjectAcceptanceStageEntryCommand(projectId, expectedVersion,
                    request.expectedTreeVersion(), idempotencyKey, digest), new ProjectAcceptanceStageEntryService.Actor(
                    currentTenantId(), currentUserId(), UUID.randomUUID().toString())));
        });
    }

    private Integer parseIfMatch(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) {
                throw new NumberFormatException();
            }
            return version;
        } catch (NumberFormatException exception) {
            throw exception(BAD_REQUEST, "If-Match必须是非负Project版本");
        }
    }

    private String digest(Long projectId, Integer expectedVersion, Object request) {
        try {
            String value = projectId + ":" + expectedVersion + ":" + JsonUtils.toJsonString(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private Long currentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(FORBIDDEN);
        }
        return userId;
    }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) {
            return action.get();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
