package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo.SatisfactionAccessGrantCreateReqVO;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAccessGrantService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionTaskManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-tasks")
@Validated
@RequiredArgsConstructor
public class SatisfactionTaskController {
    private final SatisfactionAccessGrantService grantService;
    private final SatisfactionTaskManagementService taskService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<List<SatisfactionTaskManagementService.TaskView>> list(
            @RequestParam(required = false) Long projectId) {
        return success(taskService.list(tenantId(), actorId(), projectId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<SatisfactionTaskManagementService.TaskView> get(@PathVariable("id") Long taskId) {
        return success(taskService.get(tenantId(), actorId(), taskId));
    }

    @PostMapping("/{id}/actions/assign")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionTaskManagementService.AssignmentResult> assign(
            @PathVariable("id") Long taskId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @Valid @RequestBody AssignReqVO request) {
        return success(taskService.assign(tenantId(), actorId(), taskId, request.assignedToUserId,
                request.expectedTaskVersion, operationId));
    }

    @PostMapping("/{id}/actions/recollect")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionTaskManagementService.RecollectResult> recollect(
            @PathVariable("id") Long taskId, @Valid @RequestBody RecollectReqVO request) {
        return success(taskService.recollect(tenantId(), actorId(), taskId,
                new SatisfactionTaskManagementService.Recollect(request.priorResultId,
                        request.remediationRequestId, request.evidenceSummary,
                        request.evidenceFileFactVersion)));
    }

    @PostMapping("/{id}/access-grants")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionAccessGrantService.CreatedGrant> createGrant(
            @PathVariable("id") Long taskId,
            @Valid @RequestBody SatisfactionAccessGrantCreateReqVO request) {
        taskService.requireManageable(tenantId(), actorId(), taskId);
        return success(grantService.create(tenantId(), actorId(), taskId, request.getExpiresAt()));
    }

    private Long tenantId() { return TenantContextHolder.getRequiredTenantId(); }
    private Long actorId() { return SecurityFrameworkUtils.getLoginUserId(); }

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
}
