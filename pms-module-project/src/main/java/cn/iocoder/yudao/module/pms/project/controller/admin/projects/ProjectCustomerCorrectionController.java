package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCustomerCorrectionService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCustomerCorrectionService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/projects/{projectId}/customer")
@RequiredArgsConstructor
@Validated
public class ProjectCustomerCorrectionController {
    private final ProjectCustomerCorrectionService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Inspection> inspect(@PathVariable @Positive Long projectId) {
        return success(service.inspect(projectId, actor()));
    }
    @PutMapping
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Result> correct(@PathVariable @Positive Long projectId,
            @RequestHeader("If-Match") @Min(0) Integer expectedVersion,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=128) String key,
            @Valid @RequestBody Request request) {
        return success(service.correct(new Command(projectId, expectedVersion, request.customerCode(), request.reason(), key), actor()));
    }
    public record Request(@NotBlank @Size(max=64) String customerCode, @Size(max=500) String reason) { }
    private static Actor actor() {
        return new Actor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString());
    }
}
