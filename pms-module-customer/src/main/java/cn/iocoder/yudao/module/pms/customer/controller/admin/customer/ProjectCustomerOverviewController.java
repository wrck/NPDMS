package cn.iocoder.yudao.module.pms.customer.controller.admin.customer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import cn.iocoder.yudao.module.pms.customer.service.query.ProjectCustomerOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/projects/{projectId}/customer-overview")
public class ProjectCustomerOverviewController {
    private final ProjectCustomerOverviewService service;
    private final cn.iocoder.yudao.module.pms.customer.service.customer.CustomerApplicationService commands;

    public record ExtensionRequest(String remark) { }

    @org.springframework.web.bind.annotation.PutMapping
    @PreAuthorize("@ss.hasPermission('pms:project:query') and @ss.hasPermission('pms:customer:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerCommandResult> update(
            @PathVariable("projectId") Long projectId,
            @org.springframework.web.bind.annotation.RequestBody ExtensionRequest request,
            @org.springframework.web.bind.annotation.RequestHeader("If-Match") Long expectedVersion,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String idempotencyKey) {
        return success(commands.updateProjectExtension(new cn.iocoder.yudao.module.pms.customer.service.customer.command.ProjectCustomerExtensionCommand(
                TenantContextHolder.getRequiredTenantId(),projectId,request.remark(),expectedVersion,idempotencyKey)));
    }


    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project:query') and @ss.hasPermission('pms:customer:query')")
    public CommonResult<CustomerRespVO> get(@PathVariable("projectId") Long projectId) {
        return success(service.get(new ProjectCustomerOverviewService.Query(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), projectId)));
    }
}
