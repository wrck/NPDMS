package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionTemplateManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-questionnaire-templates")
@Validated
@RequiredArgsConstructor
public class SatisfactionTemplateController {
    private final SatisfactionTemplateManagementService service;
    private final Environment environment;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<List<SatisfactionTemplateManagementService.TemplateView>> list() {
        return withTenant(() -> success(service.list(tenantId())));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionTemplateManagementService.TemplateView> create(
            @Valid @RequestBody TemplateCreateReqVO request) {
        return withTenant(() -> success(service.create(tenantId(), actorId(),
                new SatisfactionTemplateManagementService.CreateTemplate(request.templateCode, request.name))));
    }

    @PostMapping("/{id}/revisions")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionTemplateManagementService.RevisionView> createRevision(
            @PathVariable("id") Long templateId, @Valid @RequestBody RevisionCreateReqVO request) {
        return withTenant(() -> success(service.createRevision(tenantId(), actorId(), templateId,
                new SatisfactionTemplateManagementService.CreateRevision(request.projectType, request.signingMode,
                        request.implementationMode, request.businessPurposeCode, request.applicableTimingCode,
                        request.priority, request.questionnaireJson, request.threshold, request.ruleVersion))));
    }

    @PostMapping("/{id}/revisions/{revisionId}/actions/publish")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionTemplateManagementService.PublishResult> publish(
            @PathVariable("id") Long templateId, @PathVariable Long revisionId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @Valid @RequestBody PublishReqVO request) {
        return withTenant(() -> success(service.publish(tenantId(), actorId(), templateId, revisionId,
                request.expectedRevisionVersion, operationId)));
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
    public static class TemplateCreateReqVO {
        @NotBlank @Size(max = 64) private String templateCode;
        @NotBlank @Size(max = 128) private String name;
    }

    @Data
    public static class RevisionCreateReqVO {
        @NotBlank @Size(max = 64) private String projectType;
        @NotBlank @Size(max = 64) private String signingMode;
        @NotBlank @Size(max = 64) private String implementationMode;
        @NotBlank @Size(max = 64) private String businessPurposeCode;
        @NotBlank @Size(max = 64) private String applicableTimingCode;
        @NotNull private Integer priority;
        @NotBlank private String questionnaireJson;
        @NotNull private BigDecimal threshold;
        @NotBlank @Size(max = 64) private String ruleVersion;
    }

    @Data
    public static class PublishReqVO {
        @NotNull @PositiveOrZero private Integer expectedRevisionVersion;
    }
}
