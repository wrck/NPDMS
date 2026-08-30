package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo.SatisfactionAccessGrantCreateReqVO;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAccessGrantService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionTaskManagementService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAssistedResponseApplicationService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionResponseSubmissionService;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
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
import jakarta.validation.constraints.NotEmpty;
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
    private final SatisfactionAssistedResponseApplicationService assistedService;

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

    @PostMapping("/{id}/assisted-responses")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:collect')")
    public CommonResult<SatisfactionAssistedResponseApplicationService.Outcome> assistedResponse(
            @PathVariable("id") Long taskId, @Valid @RequestBody AssistedResponseReqVO request) {
        List<SatisfactionResponseSubmissionService.AssistedFile> files = request.files.stream().map(file ->
                new SatisfactionResponseSubmissionService.AssistedFile(file.role, file.sequence,
                        new FileArtifactVersionRevalidationQuery(file.artifactId, file.versionNo,
                                file.ownerContext, file.objectType, file.objectId, file.purposeCode,
                                file.referenceKey, FileActionCodes.READ,
                                new FileFactVersion(file.artifactVersion, file.referenceVersion,
                                        file.availabilityVersion), file.scopeVersion))).toList();
        return success(assistedService.submit(new SatisfactionResponseSubmissionService.AssistedCommand(
                tenantId(), actorId(), taskId, request.requestId, request.customerContactRef,
                request.answerSnapshot, files)));
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

    @Data
    public static class AssistedResponseReqVO {
        @NotBlank @Size(max = 128) private String requestId;
        @NotBlank @Size(max = 256) private String customerContactRef;
        @NotBlank private String answerSnapshot;
        @NotEmpty private List<@Valid AssistedFileReqVO> files;
    }

    @Data
    public static class AssistedFileReqVO {
        @NotBlank private String role;
        @NotNull @Positive private Integer sequence;
        @NotNull @Positive private Long artifactId;
        @NotNull @Positive private Integer versionNo;
        @NotBlank private String ownerContext;
        @NotBlank private String objectType;
        @NotBlank private String objectId;
        @NotBlank private String purposeCode;
        @NotBlank private String referenceKey;
        @NotNull @PositiveOrZero private Integer artifactVersion;
        @NotNull @PositiveOrZero private Integer referenceVersion;
        @NotNull @PositiveOrZero private Integer availabilityVersion;
        @NotNull @PositiveOrZero private Long scopeVersion;
    }
}
