package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyImportService;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/entity-capability-import/site-surveys")
public class SiteSurveyImportController {
    private final SiteSurveyImportService service;

    @PostMapping
    @PreAuthorize("@ss.hasRole('super_admin')")
    public CommonResult<Report> migrate(@Valid @RequestBody Request request) {
        var actor = new EntityActor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), java.util.UUID.randomUUID().toString());
        var completed = new ArrayList<SiteSurveyImportService.Result>();
        var failures = new ArrayList<Failure>();
        for (Long id : request.entityIds().stream().distinct().toList()) {
            try { completed.add(service.importOne(id, actor)); }
            catch (RuntimeException failure) { failures.add(new Failure(id, failure.getMessage())); }
        }
        return CommonResult.success(new Report(completed, failures));
    }

    public record Request(@NotEmpty @Size(max = 200) List<@Positive Long> entityIds) {}
    public record Failure(Long entityId, String reason) {}
    public record Report(List<SiteSurveyImportService.Result> completed, List<Failure> failures) {}
}
