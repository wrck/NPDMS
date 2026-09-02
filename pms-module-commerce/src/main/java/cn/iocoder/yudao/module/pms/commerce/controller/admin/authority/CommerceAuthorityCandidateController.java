package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo.CommerceAuthorityCandidateCreateReqVO;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo.CommerceAuthorityCandidateReconcileReqVO;
import cn.iocoder.yudao.module.pms.commerce.service.authority.CommerceAuthorityCandidateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/commerce-authority-candidates")
@Validated
@RequiredArgsConstructor
public class CommerceAuthorityCandidateController {
    private final CommerceAuthorityCandidateService service;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:commerce:authority:reconcile')")
    public CommonResult<CommerceAuthorityCandidateService.CandidateResult> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 128) String correlationId,
            @Valid @RequestBody CommerceAuthorityCandidateCreateReqVO request) {
        return success(service.create(new CommerceAuthorityCandidateService.CreateCandidateCommand(
                tenantId(), actorId(), request.objectType(), request.sourceKey(), request.candidateVersion(),
                JsonUtils.toJsonString(request.candidatePayload()),
                JsonUtils.toJsonString(request.evidenceReference()),
                idempotencyKey.trim(), correlationId.trim())));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:commerce:authority:reconcile')")
    public CommonResult<List<CommerceAuthorityCandidateService.CandidateResult>> list(
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String candidateStatus,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return success(service.listVisible(new CommerceAuthorityCandidateService.ListCandidatesQuery(
                tenantId(), actorId(), objectType, candidateStatus, pageNo, pageSize)));
    }

    @PostMapping("/{id}/actions/reconcile")
    @PreAuthorize("@ss.hasPermission('pms:commerce:authority:reconcile')")
    public CommonResult<CommerceAuthorityCandidateService.CandidateResult> reconcile(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 128) String correlationId,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody CommerceAuthorityCandidateReconcileReqVO request) {
        var command = new CommerceAuthorityCandidateService.DecideCandidateCommand(
                tenantId(), actorId(), id, version(ifMatch), request.ownerId(), request.decisionReason(),
                idempotencyKey.trim(), correlationId.trim());
        return switch (request.decision().trim().toUpperCase(Locale.ROOT)) {
            case "MATCHED" -> success(service.reconcile(command));
            case "REJECTED" -> success(service.reject(command));
            default -> throw exception(BAD_REQUEST, "decision必须为MATCHED或REJECTED");
        };
    }

    private Long tenantId() {
        try {
            return TenantContextHolder.getRequiredTenantId();
        } catch (RuntimeException exception) {
            throw exception(FORBIDDEN);
        }
    }

    private Long actorId() {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null) throw exception(FORBIDDEN);
        return actor;
    }

    private Integer version(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException exception) {
            throw exception(BAD_REQUEST, "If-Match必须是非负整数版本");
        }
    }
}
