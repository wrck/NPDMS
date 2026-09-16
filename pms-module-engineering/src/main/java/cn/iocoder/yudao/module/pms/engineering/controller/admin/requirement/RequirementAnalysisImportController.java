package cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisImportService;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/entity-capability-import/requirement-analyses")
public class RequirementAnalysisImportController {
    private final RequirementAnalysisImportService service;

    @PostMapping
    @PreAuthorize("@ss.hasRole('super_admin')")
    public CommonResult<Report> migrate(@Valid @RequestBody Request request) {
        var actor = new EntityActor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), java.util.UUID.randomUUID().toString());
        var completed = new ArrayList<RequirementAnalysisImportService.Result>();
        var failures = new ArrayList<Failure>();
        for (Long id : request.projectIds().stream().distinct().toList()) {
            try { completed.add(service.importProject(id, actor)); }
            catch (RuntimeException failure) { failures.add(new Failure(id, failure.getMessage())); }
        }
        return CommonResult.success(new Report(completed, failures));
    }

    public record Request(@NotEmpty @Size(max = 200) List<@Positive Long> projectIds) {}
    public record Failure(Long projectId, String reason) {}
    public record Report(List<RequirementAnalysisImportService.Result> completed, List<Failure> failures) {}
}
