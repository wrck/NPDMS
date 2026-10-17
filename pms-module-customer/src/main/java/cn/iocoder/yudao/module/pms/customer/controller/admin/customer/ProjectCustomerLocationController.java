package cn.iocoder.yudao.module.pms.customer.controller.admin.customer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.service.query.ProjectCustomerLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/projects/{projectId}/customer-overview/locations")
public class ProjectCustomerLocationController {
    private final ProjectCustomerLocationService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project:query') and @ss.hasPermission('pms:customer:query')")
    public CommonResult<ProjectCustomerLocationService.Result> get(
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "siteId", required = false) Long siteId) {
        return success(service.get(new ProjectCustomerLocationService.Query(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), projectId, siteId)));
    }
}
